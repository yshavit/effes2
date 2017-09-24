package com.yuvalshavit.effes2.compile;

import org.antlr.v4.runtime.tree.TerminalNode;

import com.yuvalshavit.effes2.parse.EffesLexer;
import com.yuvalshavit.effes2.parse.EffesParser;
import com.yuvalshavit.effes2.util.Dispatcher;
import com.yuvalshavit.effesvm.runtime.EffesOps;

public class MatcherCompiler {

  private /*final*/ Scope scope;
  private /*final*/ EffesOps<Void> out;
  private /*final*/ String noMatchLabel; // TODO need a way to provide the "before you go to this label, pop some vars" labels.
  private /*final*/ LabelAssigner labelAssigner;
  private int matchStackSize;
  private int noMatchPopsCount;

  public static void expression(EffesParser.MatcherContext matcher, Object o, boolean negated, EffesOps<Void> out) {
    throw new UnsupportedOperationException(); // TODO
  }

  @Dispatcher.SubclassesAreIn(EffesParser.class)
  private class CompilerImpl extends CompileDispatcher<EffesParser.MatcherContext> {
    final ScratchVars scratchVars = new ScratchVars();

    CompilerImpl() {
      super(EffesParser.MatcherContext.class);
    }

    @Dispatched
    public void apply(EffesParser.MatcherAnyContext input) {
      out.pop();
    }

    @Dispatched
    public void apply(EffesParser.MatcherWithPatternContext input) {
      TerminalNode name = input.IDENT_NAME();
      if (name != null) {
        lookUp(name.getSymbol().getText(), input.AT(), false, scratchVars).storeNoPop(out);
      }
    }

    @Dispatched
    public void apply(EffesParser.MatcherJustNameContext input) {
      String varName = input.IDENT_NAME().getSymbol().getText();
      EffesParser.ExpressionContext expression = input.expression();
      if (expression == null) {
        // always matches, so we always want to pop the value and store it to the var
        if (input.AT() == null) {
          scope.allocateLocal(varName, true).store(out);
        } else {
          scope.lookUpInParentScope(varName).store(out);
        }
      } else {
        // First we need to load the stack-top into a tmp field (without popping it), so that we can evaluate the expression. Do that in a sub-scope.
        // Then, iff that matches, we simply commit the var (if it was to an outer scope var). Otherwise we just go to the noMatchLabel
        // We'll do that in a separate scope. Then, if the expression matches, we'll pop the stack-top into the var and do the standard ifMatched/ifNotMatched
        // work. Otherwise, we'll just do the ifNotMatched.
        scope.inNewScope(() -> {
          lookUp(varName, null, true, scratchVars).storeNoPop(out);
          new ExpressionCompiler(scope, out).apply(expression);
        });
        // This goto is based on the s.t. expression. So whether we take the jump or not, after this line, the s.t. is popped, and the top of the stack is the
        // original element we matched on.
        out.gotoIfNot(noMatchLabel);
        scratchVars.commit(out);
      }
    }
  }

  private void pushMatchStack() {
    ++matchStackSize;
  }

  private void popMatchStack() {
    noMatchPopsCount = Math.max(noMatchPopsCount, matchStackSize);
    --matchStackSize;
  }

  private VarRef lookUp(String varName, TerminalNode at, boolean allowShadowing, ScratchVars scratchVars) {
    if (at == null) {
      return scope.allocateOrLookUp(varName, allowShadowing);
    } else {
      assert at.getSymbol().getType() == EffesLexer.AT : at.getSymbol().getType() + ": " + EffesLexer.VOCABULARY.getDisplayName(at.getSymbol().getType());
      VarRef commit = scope.lookUpInParentScope(varName);
      VarRef scratch = scope.allocateLocal(varName, true);
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
