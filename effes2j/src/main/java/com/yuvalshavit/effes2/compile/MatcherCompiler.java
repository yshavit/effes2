package com.yuvalshavit.effes2.compile;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.antlr.v4.runtime.tree.TerminalNode;

import com.yuvalshavit.effes2.parse.EffesLexer;
import com.yuvalshavit.effes2.parse.EffesParser;
import com.yuvalshavit.effes2.util.Dispatcher;
import com.yuvalshavit.effes2.util.EvmStrings;
import com.yuvalshavit.effesvm.runtime.EffesNativeType;

public class MatcherCompiler {

  private final CompilerContext cc;
  private final ScratchVars scratchVars;
  private final List<String> popAndFailTargets;
  private int depth;

  /**
   * @param targetVar the var being matched; if provided, and the top-level matcher is a typed pattern matcher, we'll provide an overlay of this var with type
   * info
   */
  public static void compile(
    EffesParser.MatcherContext matcherContext,
    String optionalLabelIfMatched,
    String labelNoMatch,
    boolean keepIfNoMatch,
    CompilerContext compilerContext,
    String targetVar)
  {
    VarRef keepIfNoMatchRef;
    if (keepIfNoMatch) {
      keepIfNoMatchRef = compilerContext.scope.allocateAnonymous(null);
      keepIfNoMatchRef.storeNoPop(compilerContext.out);
    } else {
      keepIfNoMatchRef = null;
    }

    MatcherCompiler compiler = new MatcherCompiler(compilerContext);
    MatcherImpl matcherImpl = compiler.new MatcherImpl();
    matcherImpl.apply(matcherContext);
    // The above would have written all of the gotos for failure. Now write the success case.
    compiler.scratchVars.commit(compilerContext.out);
    if (optionalLabelIfMatched != null) {
      compilerContext.out.gotoAbs(optionalLabelIfMatched);
    }

    if (targetVar != null && matcherContext instanceof EffesParser.MatcherWithPatternContext) {
      // This is something like "foo is Bar", where we want to re-type the "foo" variable as Bar.
      EffesParser.MatcherPatternContext pattern = ((EffesParser.MatcherWithPatternContext) matcherContext).matcherPattern();
      String type = Dispatcher.dispatch(EffesParser.MatcherPatternContext.class, String.class)
        .when(EffesParser.PatternTypeContext.class, c -> c.IDENT_TYPE().getSymbol().getText())
        .when(EffesParser.PatternRegexContext.class, c -> EffesNativeType.MATCH.getEvmType())
        .when(EffesParser.PatternStringLiteralContext.class, c -> EffesNativeType.STRING.getEvmType())
        .on(pattern);
      VarRef atMatched = compilerContext.scope.tryLookUpInTopFrame(targetVar);
      if (atMatched == null) {
        // The scope's top frame includes any "@foo" bound vars. This block is to auto-retype
        compilerContext.scope.replaceType(targetVar, type);
      } else {
        // This is a case of something like "foo is Recursive(@foo)". If we're not careful, we could try to bind "foo" twice -- once for the "@foo" (which has
        // already happened) and once for the "foo is" (which is about to happen). Since we already have the atMatched var in this scope, we just need to fix
        // its type.
        compilerContext.scope.replaceType(targetVar, type);
      }
    }

    // Finally, write the pops-and-goto for failure. Only do this if popAndFailTargets is non-empty; if it's empty, it means the match can never fail.
    if (!compiler.popAndFailTargets.isEmpty()) {
      for (int i = compiler.popAndFailTargets.size() - 1; i >= 0; --i) {
        String target = compiler.popAndFailTargets.get(i);
        if (target != null) {
          compiler.cc.out.label(target);
        }
        if (i > 0) {
          compiler.cc.out.pop();
        }
      }
      if (keepIfNoMatchRef != null) {
        keepIfNoMatchRef.push(compilerContext.out);
      }
      compiler.cc.out.gotoAbs(labelNoMatch);
    }
  }

  private MatcherCompiler(CompilerContext cc) {
    this.cc = cc;
    scratchVars = new ScratchVars();
    depth = 0;
    popAndFailTargets = new ArrayList<>();
  }

  private String getPopAndFailLabel() {
    while (popAndFailTargets.size() <= depth) {
      popAndFailTargets.add(null);
    }
    String res = popAndFailTargets.get(depth);
    if (res == null) {
      res = cc.labelAssigner.allocate(String.format("match_fail_pop_%d", depth));
      popAndFailTargets.set(depth, res);
    }
    return res;
  }

  /**
   * Dispatch for the three top-level "matcher" productions.
   *
   *   - Each "apply" assumes that its LHS operand is at the top of the stack.
   *   - A successful match should pop that operand and otherwise fall through.
   *   - An unsuccessful match should have a goto to the appropriate stack-unrolling-and-goto-fail section.
   */
  @Dispatcher.SubclassesAreIn(EffesParser.class)
  private class MatcherImpl extends CompileDispatcher<EffesParser.MatcherContext> {

    MatcherImpl() {
      super(EffesParser.MatcherContext.class);
    }

    @Dispatched
    public void apply(EffesParser.MatcherAnyContext input) {
      // <op IS> *
      cc.out.pop(); // Always matches, so just need to pop the LHS
    }

    @Dispatched
    public void apply(EffesParser.MatcherWithPatternContext input) {
      Consumer<String> varBinderByType = bindType -> bindVarIfNeeded(input.IDENT_NAME(), input.AT(), bindType);
      new MatcherWithPatternImpl(this, varBinderByType).apply(input.matcherPattern());
    }

    @Dispatched
    public void apply(EffesParser.MatcherJustNameContext input) {
      // <op IS> [@]fooName [:? expr]
      // Always matches the type, but still need to do the binding and check the expression.
      bindVarIfNeeded(input.IDENT_NAME(), input.AT(), null);
      cc.out.pop();
      if (input.expression() != null) {
        new ExpressionCompiler(cc).apply(input.expression());
        cc.out.gotoIfNot(getPopAndFailLabel());
      }
    }

    public void bindVarIfNeeded(TerminalNode varBind, TerminalNode varBindAt, String varBindType) {
      if (varBind != null) {
        String varName = varBind.getSymbol().getText();
        final VarRef varRef = cc.scope.allocateLocal(varName, true, varBindType);
        if (varBindAt != null) {
          assert varBindAt.getSymbol().getType() == EffesLexer.AT : varBindAt; // make sure we really passed in an AT
          VarRef eventualBind = cc.scope.lookUpInParentScope(varName);
          scratchVars.add(varRef, eventualBind);
        }
        varRef.storeNoPop(cc.out);
      }
    }
  }

  @Dispatcher.SubclassesAreIn(EffesParser.class)
  private class MatcherWithPatternImpl extends CompileDispatcher<EffesParser.MatcherPatternContext> {

    private final Consumer<String> varBinderByType;
    private final MatcherImpl matcherDispatch;

    MatcherWithPatternImpl(MatcherImpl matcherDispatch, Consumer<String> varBinderByType) {
      super(EffesParser.MatcherPatternContext.class);
      this.matcherDispatch = matcherDispatch;
      this.varBinderByType = varBinderByType;
    }

    @Dispatched
    public void apply(EffesParser.PatternRegexContext input) {
      ++depth;
      applyRegex((input.REGEX().getSymbol().getText()));
      --depth;
    }

    @Dispatched
    public void apply(EffesParser.PatternTypeContext input) {
      ++depth;
      //                                                                          // [..., val]
      String type = cc.qualifyType(input.IDENT_TYPE().getSymbol().getText());
      checkType(type);
      List<EffesParser.MatcherContext> fieldMatchers = input.matcher();
      int nFields = cc.typeInfo.fieldsCount(type);
      if (nFields != fieldMatchers.size()) {
        throw new CompilationException(
          input,
          String.format("incorrect number of field matchers for %s (expected %d, found %d)", type, nFields, fieldMatchers.size()));
      }
      for (int i = 0; i < nFields; ++i) {
        String fieldName = cc.typeInfo.fieldName(type, i);
        cc.out.PushField(type, fieldName);                                        // [..., val, val.fieldN]
        matcherDispatch.apply(fieldMatchers.get(i));                              // [..., val]
      }
      cc.out.pop();
      --depth;
    }

    @Dispatched
    public void apply(EffesParser.PatternStringLiteralContext input) {
      ++depth;
      applyRegex(Pattern.quote(EvmStrings.quotedEfToString(input.QUOTED_STRING())));
      --depth;
    }

    public void checkType(String type) {
      cc.out.typp(type);
      cc.out.gotoIfNot(getPopAndFailLabel());
    }

    public void applyRegex(String pattern) {
      //                                                  // [..., str]
      checkType(cc.qualifyType(EffesNativeType.STRING));
      cc.out.strPush(EvmStrings.escape(pattern));         // [..., str, pattern]
      cc.out.stringRegex();                               // [..., match?]
      checkType(cc.qualifyType(EffesNativeType.MATCH));
      varBinderByType.accept(cc.qualifyType(EffesNativeType.MATCH));
      cc.out.pop();                                       // [...]
    }
  }
}
