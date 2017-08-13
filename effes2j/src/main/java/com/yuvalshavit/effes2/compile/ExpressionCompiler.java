package com.yuvalshavit.effes2.compile;

import java.util.Map;
import java.util.function.Consumer;

import com.yuvalshavit.effes2.parse.EffesParser;
import com.yuvalshavit.effes2.util.Dispatcher;
import com.yuvalshavit.effes2.util.VoidDispatcher;

@Dispatcher.SubclassesAreIn(EffesParser.class)
public class ExpressionCompiler extends VoidDispatcher<EffesParser.ExpressionContext> {

  public ExpressionCompiler(Map<String,Symbol> symbolsToRegister, Consumer<? super Op> out) {
    super(EffesParser.ExpressionContext.class);
  }

  @Dispatched
  public void apply(EffesParser.ExprCmpContext input) {
    throw new UnsupportedOperationException(); // TODO
  }

  @Dispatched
  public void apply(EffesParser.ExprThisContext input) {
    throw new UnsupportedOperationException(); // TODO
  }

  @Dispatched
  public void apply(EffesParser.ExprStringLiteralContext input) {
    throw new UnsupportedOperationException(); // TODO
  }

  @Dispatched
  public void apply(EffesParser.ExprNegationContext input) {
    throw new UnsupportedOperationException(); // TODO
  }

  @Dispatched
  public void apply(EffesParser.ExprInstantiationContext input) {
    throw new UnsupportedOperationException(); // TODO
  }

  @Dispatched
  public void apply(EffesParser.ExprVariableOrMethodInvocationContext input) {
//    throw new UnsupportedOperationException(); // TODO
  }

  @Dispatched
  public void apply(EffesParser.ExprParenthesisContext input) {
    throw new UnsupportedOperationException(); // TODO
  }

  @Dispatched
  public void apply(EffesParser.ExprIntLiteralContext input) {
    throw new UnsupportedOperationException(); // TODO
  }

  @Dispatched
  public void apply(EffesParser.ExprIsAContext input) {
    throw new UnsupportedOperationException(); // TODO
  }

  @Dispatched
  public void apply(EffesParser.ExprPlusOrMinusContext input) {
    throw new UnsupportedOperationException(); // TODO
  }

  @Dispatched
  public void apply(EffesParser.ExprMultOrDivideContext input) {
    throw new UnsupportedOperationException(); // TODO
  }
}
