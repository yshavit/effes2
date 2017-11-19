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
  private final TypeInfo typeInfo;
  private final LabelAssigner labelAssigner;
  private final EffesOps<Void> out;
  private final Deque<String> breakLabels;
  private final Deque<String> continueLabels;
  private final VarRef thisVar;
  private final ExpressionCompiler expressionCompiler;

  public StatementCompiler(
    String instanceContextType,
    Scope scope,
    FieldLookup fieldLookup,
    TypeInfo typeInfo,
    LabelAssigner labelAssigner,
    EffesOps<Void> out)
  {
    super(EffesParser.StatementContext.class);
    this.scope = scope;
    this.fieldLookup = fieldLookup;
    this.typeInfo = typeInfo;
    this.labelAssigner = labelAssigner;
    this.out = out;
    breakLabels = new ArrayDeque<>();
    continueLabels = new ArrayDeque<>();
    thisVar = (instanceContextType == null)
      ? null
      : new VarRef.LocalVar(0, instanceContextType);
    expressionCompiler = new ExpressionCompiler(scope, fieldLookup, labelAssigner, out);
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
      expressionCompiler.apply(iterateOver);
      out.copy();
      out.arrayLen();
      iterLen.store(out);
      out.pushInt("0");
      iterIdx.store(out);
      // Now the loop. At the top of each iteration, the stack's top contains the iterateOver value.
      String loopTopLabel = labelAssigner.allocate("loopTop");
      String loopDoneLabel = labelAssigner.allocate("loopDone");
      breakLabels.push(loopDoneLabel);
      continueLabels.push(loopTopLabel);
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
      iterVar.store(out);
      compileBlock(body);
      out.gotoAbs(loopTopLabel);
      // end the loop
      labelAssigner.place(loopDoneLabel);
      breakLabels.pop();
      continueLabels.pop();
    });
  }

  @Dispatched
  public void apply(EffesParser.StatAssignContext ctx) {
    VarRef var = getVar(ctx.qualifiedIdentName());
    expressionCompiler.apply(ctx.expression());
    var.store(out);
  }

  @Dispatched
  public void apply(EffesParser.StatIfContext ctx) {
    throw new UnsupportedOperationException(); // TODO
  }

  @Dispatched
  public void apply(EffesParser.StatVarDeclareContext ctx) {
    scope.allocateLocal(ctx.IDENT_NAME().getText(), false);
  }

  @Dispatched
  public void apply(EffesParser.StatMethodInvokeContext ctx) {
    EffesParser.QualifiedIdentNameContext targetCtx = ctx.qualifiedIdentName();
    EffesParser.ArgsInvocationContext argsInvocation = ctx.argsInvocation();
    boolean hasRv = expressionCompiler.compileMethodInvocation(targetCtx, argsInvocation);

    if (hasRv) {
      out.pop();
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
        out.gotoAbs(label);
      } else if (blockStop.CONTINUE() != null) {
        String label = continueLabels.peek();
        if (label == null) {
          throw new CompilationException(ctx, "no continue target available");
        }
        out.gotoAbs(label);
      } else if (blockStop.RETURN() != null) {
        if (blockStop.expression() != null) {
          expressionCompiler.apply(blockStop.expression());
        } else if (blockStop.expressionMultiline() != null) {
          compileExpressionMultiline(blockStop.expressionMultiline());
        }
        out.rtrn();
      } else {
        throw new CompilationException(ctx, "unsupported block stop: " + blockStop.getClass().getSimpleName());
      }
    }
  }

  private void compileExpressionMultiline(EffesParser.ExpressionMultilineContext ctx) {
    throw new UnsupportedOperationException(); // TODO
  }

  private VarRef getVar(EffesParser.QualifiedIdentNameContext ctx) {
    EffesParser.QualifiedIdentNameStartContext start = ctx.qualifiedIdentNameStart();
    if (start instanceof EffesParser.QualifiedIdentTypeContext) {
      throw new CompilationException(ctx.start, ctx.stop, "static vars not supported");
    } else if (start instanceof EffesParser.QualifiedIdentThisContext) {
      if (!ctx.qualifiedIdentNameMiddle().isEmpty()) {
        throw new CompilationException(ctx.start, ctx.stop, "unsupported");
      }
      return getInstanceVar(ctx);
    } else if (start == null) {
      // just a var name; it's either a local var or an instance var on "this"
      String varName = ctx.IDENT_NAME().getText();
      VarRef result = scope.lookUp(varName);
      if (result == null) {
        result = getInstanceVar(ctx);
      }
      return result;
    } else {
      throw new UnsupportedOperationException("unrecognized AST class: " + ctx.getClass().getSimpleName());
    }
  }

  private VarRef getInstanceVar(EffesParser.QualifiedIdentNameContext ctx) {
    String fieldName = ctx.IDENT_NAME().getText();
    if (thisVar == null) {
      throw new CompilationException(ctx.getStart(), ctx.getStop(), "can't use \"this\" in static context");
    }
    String varType = thisVar.getType();
    if (typeInfo.hasField(varType, fieldName)) {
      throw new CompilationException(ctx.IDENT_NAME().getSymbol(), ctx.IDENT_NAME().getSymbol(), "unknown field " + fieldName + " on type " + varType);
    }
    return new VarRef.InstanceAndFieldVar(thisVar, fieldName, null);
  }

}
