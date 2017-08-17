package com.yuvalshavit.effes2.compile;

import com.yuvalshavit.effes2.parse.EffesParser;
import com.yuvalshavit.effes2.util.Dispatcher;
import com.yuvalshavit.effesvm.runtime.EffesOps;

/**
 * <p>
 *   Compiles just the matchers for a type. The contract for this segment of EVM code is:
 * </p>
 *
 * <ul>
 *   <li>we assume there is at least one element on the local stack, the element we want matched</li>
 *   <li>iff the element matches:
 *     <ul>
 *       <li>it will be popped from the stack by the end of the matcher code</li>
 *       <li>the stack will remain otherwise unchanged</li>
 *       <li>any variables whose names start with an <code>@</code> will be assigned to in the enclosing scope</li>
 *       <li>any variables that don't start with an <code>@</code> will be assigned to in a new scope</li>
 *       <li>execution will take the <code>ifMatch</code> action, if one is provided</li>
 *     </ul>
 *   </li>
 *   <li>otherwise:
 *     <ul>
 *       <li>the stack will be unchanged, unless this is an expression matcher, in which case the element will be popped</li>
 *       <li>execution will take the <code>ifNoMatch</code> action, if one is provided</li>
 *     </ul>
 *   </li>
 * </ul>
 * <p>
 *   In practice, there are two possible sets of ifMatched/ifNoMatched actions:
 *   <ul>
 *     <li>if the matcher is used in a matchers section, then ifMatched is nothing (just a fallthrough), and ifNotMatched is a jump</li>
 *     <li>if the matcher is used in an expression, then ifMatched is to push <code>true</code>, and ifNotMatched is to pop the var and then push
 *         <code>false</code></li>
 *   </ul>
 * </p>
 * <p>
 *   It's the caller's responsibility to do any scope management. Specifically, for expressions we'll generally want a scope just for this one expression,
 *   and for matchers, you'll want a scope that extends through the block.
 * </p>
 */
@Dispatcher.SubclassesAreIn(EffesParser.class)
public class MatcherCompiler {

  private final Scope scope;
  private final EffesOps<Void> out;
//  private final Consumer<EffesOps<Void>> ifMatched;
//  private final Consumer<EffesOps<Void>> ifNotMatched;
  private final String ifNotMatchedJump; // or null, if used as an expression; TODO if I use this approach, update the javadoc for this class
  private final boolean affirmativeExpression; // as opposed to negated
  private final LabelAssigner labelAssigner;

  public static void expression(EffesParser.MatcherContext ctx, LabelAssigner labelAssigner, boolean negated, EffesOps<Void> out) {
    throw new UnsupportedOperationException();
  }

  private boolean isExpressionMatcher() {
    return ifNotMatchedJump == null;
  }

  private class CompilerImpl extends CompileDispatcher<EffesParser.MatcherContext> {

    private final MatcherPatternCompiler patternCompiler;

    CompilerImpl() {
      super(EffesParser.MatcherContext.class);
      patternCompiler = new MatcherPatternCompiler();
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
        // always matches
        scope.allocateOrLookUp(varName, false).store(out);
        if (isExpressionMatcher()) {
          out.bool(String.valueOf(affirmativeExpression));
        }
      } else {
        // First we need to load the stack-top into a tmp field (without popping it), so that we can evaluate the expression.
        // We'll do that in a separate scope. Then, if the expression matches, we'll pop the stack-top into the var and do the standard ifMatched/ifNotMatched
        // work. Otherwise, we'll just do the ifNotMatched.
        scope.inNewScope(() -> {
          Symbol symbol = scope.allocateLocal(CompilerUtil.plainVariableName(varName), true);
          symbol.storeNoPop(out);
          new ExpressionCompiler(scope, out).apply(expression);
        });
        String ifNot;
        String endMatcher;
        if (isExpressionMatcher()) {
          ifNot = labelAssigner.allocate("no match for " + varName);
          endMatcher = labelAssigner.allocate("after match for " + varName);
        } else {
          ifNot = ifNotMatchedJump;
          endMatcher = null;
        }

        // This goto is based on the s.t. expression. So whether we take the jump or not, after this line, the s.t. is popped, and the top of the stack is the
        // original element we matched on.
        out.gotoIfNot(ifNot);
        // If we did match: store the element, and iff we're an expression, push "true" and goto the endMatcher
        scope.allocateOrLookUp(varName, false).store(out);
        if (isExpressionMatcher()) {
          out.bool(String.valueOf(affirmativeExpression));
          out.gotoAbs(endMatcher);
        }
        // If we didn't match: If we're an expression, pop the element and push "false." Otherwise, keep the element and go to the ifNotMatched label. But the
        // gotoIfNot(ifNot) already made that jump, so nothing to do here!
        if (isExpressionMatcher()) {
          labelAssigner.place(ifNot);
          out.pop();
          out.bool(String.valueOf(!affirmativeExpression));
          labelAssigner.place(endMatcher);
        }
      }
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

    @Dispatched
    public void apply(EffesParser.PatternAnyContext input) {
      throw new UnsupportedOperationException(); // TODO
    }
  }
}
