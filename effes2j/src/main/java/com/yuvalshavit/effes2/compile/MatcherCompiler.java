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
    String labelIfMatched,
    String labelNoMatch,
    boolean keepIfNoMatch,
    CompilerContext compilerContext,
    String targetVar)
  {
    VarRef keepIfNoMatchRef;
    if (keepIfNoMatch) {
      keepIfNoMatchRef = compilerContext.scope.allocateAnonymous(null);
      keepIfNoMatchRef.storeNoPop(compilerContext.module, compilerContext.out);
    } else {
      keepIfNoMatchRef = null;
    }

    MatcherCompiler compiler = new MatcherCompiler(compilerContext);
    MatcherImpl matcherImpl = compiler.new MatcherImpl();
    matcherImpl.apply(matcherContext);
    // The above would have written all of the gotos for failure. Now write the success case.
    compiler.scratchVars.commit(compilerContext.out);
    compilerContext.out.gotoAbs(labelIfMatched);

    if (targetVar != null && matcherContext instanceof EffesParser.MatcherWithPatternContext) {
      // This is something like "foo is Bar", where we want to re-type the "foo" variable as Bar.
      EffesParser.MatcherPatternContext pattern = ((EffesParser.MatcherWithPatternContext) matcherContext).matcherPattern();
      Name.QualifiedType type = Dispatcher.dispatch(EffesParser.MatcherPatternContext.class, Name.QualifiedType.class)
        .when(EffesParser.PatternTypeContext.class, c -> compilerContext.type(c.IDENT_TYPE()))
        .when(EffesParser.PatternRegexContext.class, c -> Name.QualifiedType.forBuiltin(EffesBuiltinType.REGEX_MATCH))
        .when(EffesParser.PatternStringLiteralContext.class, c -> Name.QualifiedType.forBuiltin(EffesBuiltinType.STRING))
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
        keepIfNoMatchRef.push(compilerContext.module, compilerContext.out);
      }
      compiler.cc.out.gotoAbs(labelNoMatch);
    }
  }

  private MatcherCompiler(CompilerContext cc) {
    this.cc = cc;
    scratchVars = new ScratchVars(cc.module);
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
      Consumer<Name.QualifiedType> varBinderByType = bindType -> bindVarIfNeeded(input.IDENT_NAME(), input.AT(), bindType);
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

    public void bindVarIfNeeded(TerminalNode varBind, TerminalNode varBindAt, Name.QualifiedType varBindType) {
      if (varBind != null) {
        String varName = varBind.getSymbol().getText();
        final VarRef varRef = cc.scope.allocateLocal(varName, true, varBindType);
        if (varBindAt != null) {
          assert varBindAt.getSymbol().getType() == EffesLexer.AT : varBindAt; // make sure we really passed in an AT
          VarRef eventualBind = cc.scope.lookUpInParentScope(varName);
          scratchVars.add(varRef, eventualBind);
        }
        varRef.storeNoPop(cc.module, cc.out);
      }
    }
  }

  @Dispatcher.SubclassesAreIn(EffesParser.class)
  private class MatcherWithPatternImpl extends CompileDispatcher<EffesParser.MatcherPatternContext> {

    private final Consumer<Name.QualifiedType> varBinderByType;
    private final MatcherImpl matcherDispatch;

    MatcherWithPatternImpl(MatcherImpl matcherDispatch, Consumer<Name.QualifiedType> varBinderByType) {
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
      Name.QualifiedType type = cc.type(input.IDENT_TYPE());
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
        cc.out.PushField(type.evmDescriptor(cc.module), fieldName);               // [..., val, val.fieldN]
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

    public void checkType(Name.QualifiedType type) {
      cc.out.typp(type.evmDescriptor(cc.module));
      cc.out.gotoIfNot(getPopAndFailLabel());
    }

    public void applyRegex(String pattern) {
      //                                                  // [..., str]
      checkType(Name.QualifiedType.forBuiltin(EffesBuiltinType.STRING));
      cc.out.strPush(EvmStrings.escape(pattern));         // [..., str, pattern]
      cc.out.stringRegex();                               // [..., match?]
      checkType(Name.QualifiedType.forBuiltin(EffesBuiltinType.REGEX_MATCH));
      varBinderByType.accept(Name.QualifiedType.forBuiltin(EffesBuiltinType.REGEX_MATCH));
      cc.out.pop();                                       // [...]
    }
  }
}
