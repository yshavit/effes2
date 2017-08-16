package com.yuvalshavit.effes2.compile;

import com.yuvalshavit.effes2.parse.EffesParser;
import com.yuvalshavit.effes2.util.Dispatcher;

/**
 * <p>Compiles just the matchers for a type. The contract for this segment of EVM code is:</p>
 *
 * <ul>
 *   <li>we assume there is at least one element on the local stack, the element we want matched</li>
 *   <li>iff the element matches:
 *     <ul>
 *       <li>it will be popped from the stack by the end of the matcher code</li>
 *       <li>the stack will remain otherwise unchanged</li>
 *       <li>any variables whose names start with an <code>@</code> will be assigned to in the enclosing scope</li>
 *       <li>any variables that don't start with an <code>@</code> will be assigned to in a new scope</li>
 *       <li>execution will jump to <code>ifMatchLabel</code>, if one is provided</li>
 *     </ul>
 *   </li>
 *   <li>otherwise:
 *     <ul>
 *       <li>the stack will be unchanged</li>
 *       <li>execution will jump to <code>ifNoMatchLabel</code>, if one is provided</li>
 *     </ul>
 *   </li>
 * </ul>
 */
@Dispatcher.SubclassesAreIn(EffesParser.class)
public class MatcherCompiler {

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
      throw new UnsupportedOperationException(); // TODO
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
