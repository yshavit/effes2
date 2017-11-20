package com.yuvalshavit.effes2.compile;

import java.util.ArrayDeque;
import java.util.Deque;

import com.yuvalshavit.effes2.parse.EffesParser;
import com.yuvalshavit.effes2.util.Dispatcher;
import com.yuvalshavit.effesvm.runtime.EffesNativeType;

@Dispatcher.SubclassesAreIn(EffesParser.class)
public class StatementCompiler extends CompileDispatcher<EffesParser.StatementContext> {

  private final CompilerContext cc;
  private final Deque<String> breakLabels;
  private final Deque<String> continueLabels;
  private final ExpressionCompiler expressionCompiler;

  public StatementCompiler(CompilerContext cc) {
    super(EffesParser.StatementContext.class);
    this.cc = cc;
    breakLabels = new ArrayDeque<>();
    continueLabels = new ArrayDeque<>();
    expressionCompiler = new ExpressionCompiler(cc);
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
    cc.scope.inNewScope(() -> {
      VarRef iterVar = cc.scope.allocateLocal(iterVarname, false);
      VarRef.LocalVar iterLen = cc.scope.allocateAnoymous(EffesNativeType.STRING.getEvmType());
      VarRef.LocalVar iterIdx = cc.scope.allocateAnoymous(EffesNativeType.STRING.getEvmType());

      // Evaluate the iterateOver expression and get its length. Then initialize the idx var
      expressionCompiler.apply(iterateOver);
      cc.out.copy();
      cc.out.arrayLen();
      iterLen.store(cc.out);
      cc.out.pushInt("0");
      iterIdx.store(cc.out);
      // Now the loop. At the top of each iteration, the stack's top contains the iterateOver value.
      String loopTopLabel = cc.labelAssigner.allocate("loopTop");
      String loopDoneLabel = cc.labelAssigner.allocate("loopDone");
      breakLabels.push(loopDoneLabel);
      continueLabels.push(loopTopLabel);
      cc.labelAssigner.place(loopTopLabel);
      // if arr.len >= idx goto done
      iterLen.push(cc.out);
      iterIdx.push(cc.out);
      cc.out.ge();
      cc.out.gotoIfNot(loopDoneLabel);
      // otherwise: var, body, and then back to the top. Remember, as of now, the stack's top is the iterateOver value
      cc.out.copy();
      iterIdx.push(cc.out);
      cc.out.arrayGet();
      iterVar.store(cc.out);
      compileBlock(body);
      cc.out.gotoAbs(loopTopLabel);
      // end the loop
      cc.labelAssigner.place(loopDoneLabel);
      breakLabels.pop();
      continueLabels.pop();
    });
  }

  @Dispatched
  public void apply(EffesParser.StatAssignContext ctx) {
    VarRef var = getVar(ctx.qualifiedIdentName());
    expressionCompiler.apply(ctx.expression());
    var.store(cc.out);
  }

  @Dispatched
  public void apply(EffesParser.StatIfContext ctx) {
    throw new UnsupportedOperationException(); // TODO
  }

  @Dispatched
  public void apply(EffesParser.StatVarDeclareContext ctx) {
    cc.scope.allocateLocal(ctx.IDENT_NAME().getText(), false);
  }

  @Dispatched
  public void apply(EffesParser.StatMethodInvokeContext ctx) {
    EffesParser.QualifiedIdentNameContext targetCtx = ctx.qualifiedIdentName();
    EffesParser.ArgsInvocationContext argsInvocation = ctx.argsInvocation();
    boolean hasRv = expressionCompiler.compileMethodInvocation(targetCtx, argsInvocation);

    if (hasRv) {
      cc.out.pop();
    }
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
    ctx.statement().forEach(this::apply);
    EffesParser.BlockStopContext blockStop = ctx.blockStop();
    if (blockStop != null) {
      if (blockStop.BREAK() != null) {
        String label = breakLabels.peek();
        if (label == null) {
          throw new CompilationException(ctx, "no break target available");
        }
        cc.out.gotoAbs(label);
      } else if (blockStop.CONTINUE() != null) {
        String label = continueLabels.peek();
        if (label == null) {
          throw new CompilationException(ctx, "no continue target available");
        }
        cc.out.gotoAbs(label);
      } else if (blockStop.RETURN() != null) {
        if (blockStop.expression() != null) {
          expressionCompiler.apply(blockStop.expression());
        } else if (blockStop.expressionMultiline() != null) {
          compileExpressionMultiline(blockStop.expressionMultiline());
        }
        cc.out.rtrn();
      } else {
        throw new CompilationException(ctx, "unsupported block stop: " + blockStop.getClass().getSimpleName());
      }
    }
  }

  private void compileExpressionMultiline(EffesParser.ExpressionMultilineContext ctx) {
    throw new UnsupportedOperationException(); // TODO
  }

  private VarRef getVar(EffesParser.QualifiedIdentNameContext ctx) {
    return Dispatcher.dispatch(EffesParser.QualifiedIdentNameStartContext.class, EffesParser.class, VarRef.class)
      .when(EffesParser.QualifiedIdentTypeContext.class, c -> {
        throw new CompilationException(ctx.start, ctx.stop, "static vars not supported");
      })
      .when(EffesParser.QualifiedIdentThisContext.class, c -> {
        if (!ctx.qualifiedIdentNameMiddle().isEmpty()) {
          throw new CompilationException(ctx.start, ctx.stop, "unsupported");
        }
        return getInstanceField(ctx);
      })
      .whenNull(() -> {
        // just a var name; it's either a local var or an instance var on "this"
        String varName = ctx.IDENT_NAME().getText();
        VarRef result = cc.scope.lookUp(varName);
        if (result == null) {
          result = getInstanceField(ctx);
        }
        return result;
      })
      .on(ctx.qualifiedIdentNameStart());
  }

  private VarRef getInstanceField(EffesParser.QualifiedIdentNameContext ctx) {
    String fieldName = ctx.IDENT_NAME().getText();
    VarRef instanceVar = cc.getInstanceContextVar(ctx.getStart(), ctx.getStart());
    String varType = instanceVar.getType();
    if (cc.typeInfo.hasField(varType, fieldName)) {
      throw new CompilationException(ctx.IDENT_NAME().getSymbol(), ctx.IDENT_NAME().getSymbol(), "unknown field " + fieldName + " on type " + varType);
    }
    return new VarRef.InstanceAndFieldVar(instanceVar, fieldName, null);
  }

}
