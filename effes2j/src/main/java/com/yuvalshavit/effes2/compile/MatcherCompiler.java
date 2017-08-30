package com.yuvalshavit.effes2.compile;

import org.antlr.v4.runtime.tree.TerminalNode;

import com.yuvalshavit.effes2.parse.EffesLexer;
import com.yuvalshavit.effes2.parse.EffesParser;
import com.yuvalshavit.effes2.util.Dispatcher;
import com.yuvalshavit.effesvm.runtime.EffesOps;

@Dispatcher.SubclassesAreIn(EffesParser.class)
public class MatcherCompiler {

  private final Scope scope;
  private final EffesOps<Void> out;
  private final String noMatchLabel; // TODO need a way to provide the "before you go to this label, pop some vars" labels.

  private class CompilerImpl extends CompileDispatcher<EffesParser.MatcherContext> {

    private final MatcherPatternCompiler patternCompiler;

    CompilerImpl() {
      super(EffesParser.MatcherContext.class);
      patternCompiler = new MatcherPatternCompiler();
    }

    @Dispatched
    public void apply(EffesParser.MatcherAnyContext input) {
      out.pop();
    }

    @Dispatched
    public void apply(EffesParser.MatcherWithPatternContext input) {
      throw new UnsupportedOperationException(); // TODO
    }

    @Dispatched
    public void apply(EffesParser.MatcherJustNameContext input) {
      String varName = input.IDENT_NAME().getSymbol().getText();
      EffesParser.ExpressionContext expression = input.expression();
      if (expression == null) {
        // always matches, so we always want to pop the value and store it to the var
        ScratchVars scratchVars = new ScratchVars();
        lookUp(varName, input.AT(), false, scratchVars).store(out);
        scratchVars.commit(out); // TODO this produces "svar x, pvar x, svar y". We should post-facto optimize this to just "svar y"
      } else {
        // First we need to load the stack-top into a tmp field (without popping it), so that we can evaluate the expression. Do that in a sub-scope.
        // Then, iff that matches, we simply commit the var (if it was to an outer scope var). Otherwise we just go to the noMatchLabel
        // We'll do that in a separate scope. Then, if the expression matches, we'll pop the stack-top into the var and do the standard ifMatched/ifNotMatched
        // work. Otherwise, we'll just do the ifNotMatched.
        ScratchVars scratch = new ScratchVars();
        scope.inNewScope(() -> {
          lookUp(varName, null, true, scratch).storeNoPop(out);
          new ExpressionCompiler(scope, out).apply(expression);
        });
        // This goto is based on the s.t. expression. So whether we take the jump or not, after this line, the s.t. is popped, and the top of the stack is the
        // original element we matched on.
        out.gotoIfNot(noMatchLabel);
        scratch.commit(out);
      }
    }
  }

  private Symbol lookUp(String varName, TerminalNode at, boolean allowShadowing, ScratchVars scratchVars) {
    if (at == null) {
      return scope.allocateOrLookUp(varName, allowShadowing);
    } else {
      assert at.getSymbol().getType() == EffesLexer.AT : at.getSymbol().getType() + ": " + EffesLexer.VOCABULARY.getDisplayName(at.getSymbol().getType());
      Symbol commit = scope.lookUpInParentScope(varName);
      Symbol scratch = scope.allocateLocal(varName, true);
      scratchVars.add(scratch, commit);
      return scratch;
    }
  }

  @Dispatcher.SubclassesAreIn(EffesParser.class)
  private class MatcherPatternCompiler extends CompileDispatcher<EffesParser.MatcherPatternContext> {
    MatcherPatternCompiler() {
      super(EffesParser.MatcherPatternContext.class);
    }

    @Dispatched
    public void apply(EffesParser.PatternRegexContext input) {
      throw new UnsupportedOperationException(); // TODO
    }

    @Dispatched
    public void apply(EffesParser.PatternTypeContext input) {
      throw new UnsupportedOperationException(); // TODO
    }

    @Dispatched
    public void apply(EffesParser.PatternStringLiteralContext input) {
      throw new UnsupportedOperationException(); // TODO
    }
  }
}
