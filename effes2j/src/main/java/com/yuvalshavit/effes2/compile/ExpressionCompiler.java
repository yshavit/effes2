package com.yuvalshavit.effes2.compile;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.antlr.v4.runtime.tree.TerminalNode;

import com.yuvalshavit.effes2.parse.EffesParser;
import com.yuvalshavit.effes2.util.Dispatcher;
import com.yuvalshavit.effes2.util.VoidDispatcher;

@Dispatcher.SubclassesAreIn(EffesParser.class)
public class ExpressionCompiler extends VoidDispatcher<EffesParser.ExpressionContext> {

  private final Map<String,Symbol> symbolsToRegister;
  private final Consumer<? super Op> out;

  public ExpressionCompiler(Map<String,Symbol> symbolsToRegister, Consumer<? super Op> out) {
    super(EffesParser.ExpressionContext.class);
    this.symbolsToRegister = symbolsToRegister;
    this.out = out;
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
    EffesParser.QualifiedIdentNameContext qualifiedName = input.qualifiedIdentName();
    EffesParser.ArgsInvocationContext argsInvocation = input.argsInvocation();

    if (argsInvocation != null) {
      throw new UnsupportedOperationException();
    }

    EffesParser.QualifiedIdentNameStartContext qualifiedStart = qualifiedName.qualifiedIdentNameStart();
    List<EffesParser.QualifiedIdentNameMiddleContext> qualifiedMiddle = qualifiedName.qualifiedIdentNameMiddle();
    if (qualifiedStart != null || !qualifiedMiddle.isEmpty()) {
      throw new UnsupportedOperationException();
    }

    TerminalNode finalName = qualifiedName.IDENT_NAME();
    String symbolName = finalName.getSymbol().getText();
    Symbol symbol = symbolsToRegister.get(symbolName);
    int reg = symbol.getReg();
    out.accept(Op.factory.pvar(String.valueOf(reg)));
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
