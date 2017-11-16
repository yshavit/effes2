package com.yuvalshavit.effes2.compile;

import java.util.List;
import java.util.regex.Pattern;

import org.antlr.v4.runtime.tree.TerminalNode;

import com.yuvalshavit.effes2.parse.EffesLexer;
import com.yuvalshavit.effes2.parse.EffesParser;
import com.yuvalshavit.effes2.util.Dispatcher;
import com.yuvalshavit.effes2.util.EvmStrings;
import com.yuvalshavit.effesvm.runtime.EffesNativeType;
import com.yuvalshavit.effesvm.runtime.EffesOps;

public class MatcherCompiler {

  private final Scope scope;
  private final FieldLookup fieldLookup;
  private final String labelNoMatch;
  private final boolean keepIfNoMatch;
  private final LabelAssigner labelAssigner;
  private final EffesOps<Void> out;
  private final ScratchVars scratchVars;
  private int depth;

  public static void compile(
    EffesParser.MatcherContext matcherContext,
    FieldLookup fieldLookup,
    String labelIfMatched,
    String labelNoMatch,
    boolean keepIfNoMatch,
    Scope scope,
    LabelAssigner labelAssigner,
    EffesOps<Void> out)
  {
    MatcherCompiler compiler = new MatcherCompiler(fieldLookup, labelNoMatch, keepIfNoMatch, scope, labelAssigner, out);
    MatcherImpl matcherImpl = compiler.new MatcherImpl();
    matcherImpl.apply(matcherContext);
    // The above would have written all of the gotos for failure. Now write the success case.
    compiler.scratchVars.commit(compiler.out);
    for (int i = 0; i < compiler.depth; ++i) {
      compiler.out.pop();
    }
    if (labelIfMatched != null) {
      compiler.out.gotoAbs(labelIfMatched);
    }
  }

  private MatcherCompiler(
    FieldLookup fieldLookup,
    String labelNoMatch,
    boolean keepIfNoMatch,
    Scope scope,
    LabelAssigner labelAssigner,
    EffesOps<Void> out)
  {
    this.scope = scope;
    this.fieldLookup = fieldLookup;
    this.labelNoMatch = labelNoMatch;
    this.keepIfNoMatch = keepIfNoMatch;
    this.labelAssigner = labelAssigner;
    this.out = out;
    scratchVars = new ScratchVars();
    depth = 1;
  }

  @Dispatcher.SubclassesAreIn(EffesParser.class)
  private class MatcherImpl extends CompileDispatcher<EffesParser.MatcherContext> {

    MatcherImpl() {
      super(EffesParser.MatcherContext.class);
    }

    @Dispatched
    public void apply(EffesParser.MatcherAnyContext input) {
      handle(null, null, null, null, null);
    }

    @Dispatched
    public void apply(EffesParser.MatcherWithPatternContext input) {
      handle(input.AT(), input.IDENT_NAME(), null, null, () -> new MatcherPatternImpl().apply(input.matcherPattern()));
    }

    @Dispatched
    public void apply(EffesParser.MatcherJustNameContext input) {
      handle(
        input.AT(),
        input.IDENT_NAME(),
        null,
        input.expression() == null ? null : () -> new ExpressionCompiler(scope, fieldLookup, labelAssigner, out).apply(input.expression()),
        null);
    }
  }

  @Dispatcher.SubclassesAreIn(EffesParser.class)
  private class MatcherPatternImpl extends CompileDispatcher<EffesParser.MatcherPatternContext> {
    MatcherPatternImpl() {
      super(EffesParser.MatcherPatternContext.class);
    }

    @Dispatched
    public void apply(EffesParser.PatternRegexContext input) {
      handle(
        null,
        null,
        EffesNativeType.STRING.getEvmType(),
        checkRegex(input.REGEX().getSymbol().getText()),
        null);
    }

    @Dispatched
    public void apply(EffesParser.PatternTypeContext input) {
      String typeName = input.IDENT_TYPE().getSymbol().getText();
      handle(
        null,
        null,
        typeName,
        null,
        () -> {
          ++depth;
          List<EffesParser.MatcherContext> matcher = input.matcher();
          for (int childIdx = 0; childIdx < matcher.size(); childIdx++) {
            EffesParser.MatcherContext childContext = matcher.get(childIdx);
            String fieldName = fieldLookup.fieldName(typeName, childIdx);
            out.PushField(typeName, fieldName);
            new MatcherImpl().apply(childContext);
            out.pop();
          }
          --depth;
        }
      );
    }

    @Dispatched
    public void apply(EffesParser.PatternStringLiteralContext input) {
      handle(
        null,
        null,
        EffesNativeType.STRING.getEvmType(),
        checkRegex(Pattern.quote(ExpressionCompiler.getQuotedString(input.QUOTED_STRING()))),
        null);
    }

    private Runnable checkRegex(String regex) {
      return () -> {
        out.strPush(EvmStrings.escape(regex));
        out.stringRegex();
        out.type(EffesNativeType.MATCH.getEvmType());
      };
    }
  }

  private void handle(TerminalNode varBindAt, TerminalNode varBind, String type, Runnable suchThat, Runnable next) {
    // stack coming in: [... target]
    if (varBind != null) {
      String varName = varBind.getSymbol().getText();
      final VarRef varRef;
      if (varBindAt == null) {
        // local var, just allocate it. We'll only ever care about the var if the type matches, so assume that type
        varRef = scope.allocateLocal(varName, true, type);
      } else {
        assert varBindAt.getSymbol().getType() == EffesLexer.AT : varBindAt; // make sure we really passed in an AT
        VarRef eventualBind = scope.lookUpInParentScope(varName);
        varRef = scope.allocateLocal(varName, true, type);
        scratchVars.add(varRef, eventualBind);
      }
      varRef.storeNoPop(out);
    }
    if (type != null) {
      String typeMatchLabel = labelAssigner.allocate(String.format("match_%d_%s", depth, type));
      out.typp(type);
      // stack: [... target, isRightType]
      out.gotoIf(typeMatchLabel);
      // stack: [... target]
      // If we get past the goto, that means we had a type mismatch. Handle the failure (that'll include a goto).
      // Otherwise, we're done with the type check, so just provide the gotoIf's destination pin
      handleFailure();
      labelAssigner.place(typeMatchLabel);
    }
    if (suchThat != null) {
      String suchThatSuccessLabel = labelAssigner.allocate(String.format("match_%d_suchThat", depth));
      suchThat.run();
      // stack: [... target, suchThat]
      out.gotoIf(suchThatSuccessLabel);
      // stack: [... target]
      // Same branching as the "if type != null" bit
      handleFailure();
      labelAssigner.place(suchThatSuccessLabel);
    }
    if (next != null) {
      next.run();
    }
  }

  private void handleFailure() {
    int pops = keepIfNoMatch ? (depth - 1) : depth;
    for (int i = 0; i < pops; ++i) {
      out.pop();
    }
    out.gotoAbs(labelNoMatch);
  }

}
