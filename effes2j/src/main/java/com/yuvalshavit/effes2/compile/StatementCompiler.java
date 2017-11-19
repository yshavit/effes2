package com.yuvalshavit.effes2.compile;

import java.util.ArrayDeque;
import java.util.Deque;

import com.yuvalshavit.effes2.parse.EffesParser;
import com.yuvalshavit.effes2.util.Dispatcher;
import com.yuvalshavit.effesvm.runtime.EffesNativeType;
import com.yuvalshavit.effesvm.runtime.EffesOps;

@Dispatcher.SubclassesAreIn(EffesParser.class)
public class StatementCompiler extends CompileDispatcher<EffesParser.StatementContext> {

  private final Scope scope;
  private final FieldLookup fieldLookup;
  private final LabelAssigner labelAssigner;
  private final EffesOps<Void> out;
  private final Deque<String> breakLabels;

  public StatementCompiler(Scope scope, FieldLookup fieldLookup, LabelAssigner labelAssigner, EffesOps<Void> out) {
    super(EffesParser.StatementContext.class);
    this.scope = scope;
    this.fieldLookup = fieldLookup;
    this.labelAssigner = labelAssigner;
    this.out = out;
    breakLabels = new ArrayDeque<>();
  }

  @Dispatched
  public void apply(EffesParser.StatAssignMultilineContext ctx) {
    throw new UnsupportedOperationException(); // TODO
  }

  @Dispatched
  public void apply(EffesParser.StatForContext ctx) {
    String iterVarname = ctx.IDENT_NAME().getSymbol().getText();
    EffesParser.ExpressionContext iterateOver = ctx.expression();
    EffesParser.BlockContext body = ctx.block();
    scope.inNewScope(() -> {
      VarRef iterVar = scope.allocateLocal(iterVarname, false);
      VarRef.LocalVar iterLen = scope.allocateAnoymous(EffesNativeType.STRING.getEvmType());
      VarRef.LocalVar iterIdx = scope.allocateAnoymous(EffesNativeType.STRING.getEvmType());

      // Evaluate the iterateOver expression and get its length. Then initialize the idx var
      compileExpression(iterateOver);
      out.copy();
      out.arrayLen();
      iterLen.store(out);
      out.pushInt("0");
      iterIdx.store(out);
      // Now the loop. At the top of each iteration, the stack's top contains the iterateOver value.
      String loopTopLabel = labelAssigner.allocate("loopTop");
      String loopDoneLabel = labelAssigner.allocate("loopDone");
      breakLabels.push(loopDoneLabel);
      labelAssigner.place(loopTopLabel);
      // if arr.len >= idx goto done
      iterLen.push(out);
      iterIdx.push(out);
      out.ge();
      out.gotoIfNot(loopDoneLabel);
      // otherwise: var, body, and then back to the top. Remember, as of now, the stack's top is the iterateOver value
      out.copy();
      iterIdx.push(out);
      out.arrayGet();
      compileBlock(body);
      out.gotoAbs(loopTopLabel);
      // end the loop
      labelAssigner.place(loopDoneLabel);
      breakLabels.pop();
    });
  }

  @Dispatched
  public void apply(EffesParser.StatAssignContext ctx) {
    throw new UnsupportedOperationException(); // TODO
  }

  @Dispatched
  public void apply(EffesParser.StatIfContext ctx) {
    throw new UnsupportedOperationException(); // TODO
  }

  @Dispatched
  public void apply(EffesParser.StatVarDeclareContext ctx) {
    throw new UnsupportedOperationException(); // TODO
  }

  @Dispatched
  public void apply(EffesParser.StatMethodInvokeContext ctx) {
    throw new UnsupportedOperationException(); // TODO
  }

  @Dispatched
  public void apply(EffesParser.StatWhileContext ctx) {
    throw new UnsupportedOperationException(); // TODO
  }

  @Dispatched
  public void apply(EffesParser.StatNoopContext ctx) {
    // nothing
  }

  @Dispatched
  public void apply(EffesParser.StatMatchContext ctx) {
    throw new UnsupportedOperationException(); // TODO
  }

  private void compileBlock(EffesParser.BlockContext ctx) {
    throw new UnsupportedOperationException();
  }

  private void compileExpression(EffesParser.ExpressionContext ctx) {
    throw new UnsupportedOperationException(); // TODO
  }

}
