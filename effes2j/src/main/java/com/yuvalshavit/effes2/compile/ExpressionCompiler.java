package com.yuvalshavit.effes2.compile;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.antlr.v4.runtime.tree.TerminalNode;

import com.yuvalshavit.effes2.parse.EffesParser;
import com.yuvalshavit.effes2.util.Dispatcher;
import com.yuvalshavit.effes2.util.VoidDispatcher;
import com.yuvalshavit.effesvm.runtime.EffesOps;

@Dispatcher.SubclassesAreIn(EffesParser.class)
public class ExpressionCompiler extends VoidDispatcher<EffesParser.ExpressionContext> {

  private final Map<String,Symbol> symbolsToRegister;
  private final EffesOps<Void> out;

  public ExpressionCompiler(Map<String,Symbol> symbolsToRegister, Consumer<? super Op> out) {
    super(EffesParser.ExpressionContext.class);
    this.symbolsToRegister = symbolsToRegister;
    this.out = Op.factory(out::accept);
  }

  @Dispatched
  public void apply(EffesParser.ExprCmpContext input) {
    final Runnable op;
    switch (input.cmp().getChild(TerminalNode.class, 0).getSymbol().getText()) {
      case "<":
        op = out::lt;
        break;
      case "<=":
        op = out::le;
        break;
      case "==":
        op = out::eq;
        break;
      case "!=":
        op = out::ne;
        break;
      case ">":
        op = out::gt;
        break;
      case ">=":
        op = out::ge;
        break;
      default:
        throw new IllegalArgumentException("unrecognized op: " + input);
    }
    binaryExpr(input.expression(), op);
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
    out.pvar(String.valueOf(reg));
  }

  @Dispatched
  public void apply(EffesParser.ExprParenthesisContext input) {
    apply(input.expression());
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
    binaryExpr(input.expression(), input.PLUS() == null ? out::iSub : out::iAdd);
  }

  @Dispatched
  public void apply(EffesParser.ExprMultOrDivideContext input) {
    binaryExpr(input.expression(), input.ASTERISK() == null ? out::iSub : out::iMul);
  }

  private void binaryExpr(List<EffesParser.ExpressionContext> exprs, Runnable op) {
    if (exprs.size() != 2) {
      throw new IllegalArgumentException("require exactly two expressions: " + exprs);
    }
    apply(exprs.get(0));
    apply(exprs.get(1));
    op.run();
  }
}
