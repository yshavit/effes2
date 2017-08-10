package com.yuvalshavit.effes2.compile;

import com.yuvalshavit.effes2.parse.EffesParser;
import com.yuvalshavit.effes2.util.Dispatcher;
import com.yuvalshavit.effes2.util.VoidDispatcher;

@Dispatcher.SubclassesAreIn(EffesParser.class)
public class ExpressionCompiler extends VoidDispatcher<EffesParser.ExpressionContext> {

  public ExpressionCompiler() {
    super(EffesParser.ExpressionContext.class);
  }

  @Dispatched
  public Void apply(EffesParser.ExprCmpContext input) {
    throw new UnsupportedOperationException(); // TODO
  }

  @Dispatched
  public Void apply(EffesParser.ExprThisContext input) {
    throw new UnsupportedOperationException(); // TODO
  }

  @Dispatched
  public Void apply(EffesParser.ExprStringLiteralContext input) {
    throw new UnsupportedOperationException(); // TODO
  }

  @Dispatched
  public Void apply(EffesParser.ExprNegationContext input) {
    throw new UnsupportedOperationException(); // TODO
  }

  @Dispatched
  public Void apply(EffesParser.ExprInstantiationContext input) {
    throw new UnsupportedOperationException(); // TODO
  }

  @Dispatched
  public Void apply(EffesParser.ExprVariableOrMethodInvocationContext input) {
    throw new UnsupportedOperationException(); // TODO
  }

  @Dispatched
  public Void apply(EffesParser.ExprParenthesisContext input) {
    throw new UnsupportedOperationException(); // TODO
  }

  @Dispatched
  public Void apply(EffesParser.ExprIntLiteralContext input) {
    throw new UnsupportedOperationException(); // TODO
  }

  @Dispatched
  public Void apply(EffesParser.ExprIsAContext input) {
    throw new UnsupportedOperationException(); // TODO
  }

  @Dispatched
  public Void apply(EffesParser.ExprPlusOrMinusContext input) {
    throw new UnsupportedOperationException(); // TODO
  }

  @Dispatched
  public Void apply(EffesParser.ExprMultOrDivideContext input) {
    throw new UnsupportedOperationException(); // TODO
  }
}
