package com.yuvalshavit.effes2.compile;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

import com.yuvalshavit.effes2.parse.EffesParser;
import com.yuvalshavit.effes2.util.Dispatcher;
import com.yuvalshavit.effes2.util.EvmStrings;
import com.yuvalshavit.effesvm.runtime.EffesNativeType;

@Dispatcher.SubclassesAreIn(EffesParser.class)
public class StatementCompiler extends CompileDispatcher<EffesParser.StatementContext> {

  private final Deque<BreakLabels> breakLabels;
  private final ExpressionCompiler expressionCompiler;
  private final CompilerContext cc;

  public StatementCompiler(CompilerContext cc) {
    super(EffesParser.StatementContext.class);
    this.cc = cc;
    breakLabels = new ArrayDeque<>();
    expressionCompiler = new ExpressionCompiler(cc);
  }

  @Dispatched
  public void apply(EffesParser.StatAssignMultilineContext ctx) {
    VarRef toVar = getVarForAssign(ctx.qualifiedIdentName(), null);
    cc.scope.inNewScope(() -> compileExpressionMultiline(ctx.expressionMultiline(), toVar));
  }

  @Dispatched
  public void apply(EffesParser.StatForContext ctx) {
    String iterVarname = ctx.IDENT_NAME().getSymbol().getText();
    EffesParser.ExpressionContext iterateOver = ctx.expression();
    EffesParser.BlockContext body = ctx.block();
    cc.scope.inNewScope(() -> {
      VarRef iterVar = cc.scope.allocateLocal(iterVarname, false);
      VarRef iterLen = cc.scope.allocateAnonymous(EffesNativeType.STRING.getEvmType());
      VarRef iterIdx = cc.scope.allocateAnonymous(EffesNativeType.STRING.getEvmType());

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
      breakLabels.push(new BreakLabels(loopDoneLabel, loopTopLabel));
      cc.labelAssigner.place(loopTopLabel);
      // if arr.len >= idx goto done
      iterLen.push(cc.out);
      iterIdx.push(cc.out);
      cc.out.ge();
      cc.out.gotoIfNot(loopDoneLabel);
      // otherwise: var, body, increment, and then back to the top. Remember, as of now, the stack's top is the iterateOver value
      cc.out.copy();
      iterIdx.push(cc.out);
      cc.out.arrayGet();
      iterVar.store(cc.out);
      compileBlock(body);
      iterIdx.push(cc.out);
      cc.out.pushInt("1");
      cc.out.iAdd();
      iterIdx.store(cc.out);
      cc.out.gotoAbs(loopTopLabel);
      // end the loop
      cc.labelAssigner.place(loopDoneLabel);
      breakLabels.pop();
    });
    cc.out.pop(); // the array being indexed
  }

  @Dispatched
  public void apply(EffesParser.StatAssignContext ctx) {
    final String type = (ctx.expression() instanceof EffesParser.ExprInstantiationContext)
      ? ((EffesParser.ExprInstantiationContext) ctx.expression()).IDENT_TYPE().getSymbol().getText()
      : null;
    VarRef var = getVarForAssign(ctx.qualifiedIdentName(), type);
    cc.scope.inNewScope(() -> expressionCompiler.apply(ctx.expression()));
    var.store(cc.out);
  }

  @Dispatched
  public void apply(EffesParser.StatIfContext ctx) {
    Dispatcher.dispatchConsumer(EffesParser.StatementIfConditionAndBodyContext.class)
      .when(EffesParser.IfElseSimpleContext.class, c -> {
        String ifNotLabel = cc.labelAssigner.allocate("ifNot");
        // Easy approach for now: always drop the ifChainEndLabel, even if we could have done without it. Not worrying about the extra GOTO
        String ifChainEndLabel = cc.labelAssigner.allocate("ifChainEnd");
        // Each expression+block is in its own scope. That way, for instance:
        //   if foo is One(val):
        //     youCanUse(val)
        //   else
        //     valIsNotAvailableHere()
        EffesParser.ExpressionContext condition = ctx.expression();
        cc.scope.inNewScope(() -> {
          expressionCompiler.apply(condition);
            cc.out.gotoIfNot(ifNotLabel);
            compileBlock(c.block());
          });
        compileElseStatement(c.elseStat(), ifNotLabel, ifChainEndLabel);
        cc.labelAssigner.place(ifChainEndLabel);
      })
      .when(EffesParser.IfMatchMultiContext.class, c-> {
        cc.scope.inNewScope(() -> {
          EffesParser.ExpressionContext condition = ctx.expression();
          expressionCompiler.apply(condition);
          String endLabel = cc.labelAssigner.allocate("matchersEnd");
          compileBlockMatchers(c.blockMatchers(), endLabel, endLabel, ExpressionCompiler.tryGetLocalVar(condition));
          cc.labelAssigner.place(endLabel);
        });
      })
      .on(ctx.statementIfConditionAndBody());
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
    String loopTopLabel = cc.labelAssigner.allocate("whileLoopTop");
    String loopEndLabel = cc.labelAssigner.allocate("whileLoopEnd");
    breakLabels.push(new BreakLabels(loopEndLabel, loopTopLabel));
    cc.scope.inNewScope(() -> {
      cc.labelAssigner.place(loopTopLabel);
      EffesParser.ExpressionContext condition = ctx.expression();
      String conditionVar = ExpressionCompiler.tryGetLocalVar(condition);
      expressionCompiler.apply(condition); // inside this scope, in case it's a "while foo is One(bar)" statement. We want the bar available here.
      Dispatcher.dispatchConsumer(EffesParser.StatementWhileConditionAndBodyContext.class)
        .when(EffesParser.WhileBodySimpleContext.class, c -> {
          cc.out.gotoIfNot(loopEndLabel);
          if (!compileBlock(c.block())) {
            cc.out.gotoAbs(loopTopLabel);
          }
        })
        .when(EffesParser.WhileBodyMultiMatchersContext.class, c -> {
          compileBlockMatchers(c.blockMatchers(), loopTopLabel, loopEndLabel, conditionVar);
        })
        .on(ctx.statementWhileConditionAndBody());
    });
    cc.labelAssigner.place(loopEndLabel);
  }

  @Dispatched
  public void apply(EffesParser.StatNoopContext ctx) {
    // nothing
  }

  @Dispatched
  public void apply(EffesParser.StatTypeAssertionContext ctx) {
    // for now, not even any bytecode; just assert it
    String varName = ctx.IDENT_NAME().getSymbol().getText();
    String type = ctx.IDENT_TYPE().getSymbol().getText();
    cc.scope.replaceType(varName, type);
  }

  @Dispatched
  public void apply(EffesParser.StatMatchContext ctx) {
    String endLabel = cc.labelAssigner.allocate("statMatcherEnd");
    EffesParser.ExpressionContext targetExpression = ctx.expression();
    expressionCompiler.apply(targetExpression);
    cc.scope.inNewScope(() -> MatcherCompiler.compile(ctx.matcher(), null, endLabel, false, cc, ExpressionCompiler.tryGetLocalVar(targetExpression)));
    cc.labelAssigner.place(endLabel);
  }

  /**
   * compiles a block, and returns whether it ended in a blockStop
   */
  public boolean compileBlock(EffesParser.BlockContext ctx) {
    ctx.statement().forEach(element -> {
      if (element instanceof EffesParser.StatTypeAssertionContext) {
        runAainst(element, () -> apply((EffesParser.StatTypeAssertionContext) element));
      } else if (element instanceof EffesParser.StatAssignContext || element instanceof EffesParser.StatAssignMultilineContext) {
        apply(element);
      } else {
        cc.scope.inNewScope(() -> apply(element));
      }
    });
    EffesParser.BlockStopContext blockStop = ctx.blockStop();
    Dispatcher.dispatchConsumer(EffesParser.BlockStopContext.class)
      .when(EffesParser.BlockStopBreakContext.class, c -> {
        BreakLabels label = breakLabels.peek();
        if (label == null) {
          throw new CompilationException(ctx, "no break target available");
        }
        cc.out.gotoAbs(label.labelForBreak);
      })
      .when(EffesParser.BlockStopContinueContext.class, c -> {
        BreakLabels label = breakLabels.peek();
        if (label == null) {
          throw new CompilationException(ctx, "no continue target available");
        }
        cc.out.gotoAbs(label.labelForContinue);
      })
      .when(EffesParser.BlockStopReturnContext.class, c -> {
        if (c.expression() != null) {
          expressionCompiler.apply(c.expression());
        } else if (c.expressionMultiline() != null) {
          VarRef rv = cc.scope.allocateAnonymous(null);
          compileExpressionMultiline(c.expressionMultiline(), rv);
          rv.push(cc.out);
        }
        cc.out.rtrn();
      })
      .whenNull(() -> {})
      .on(blockStop);
    return blockStop != null;
  }

  private void compileBlockMatchers(EffesParser.BlockMatchersContext ctx, String gotoAfterMatchLabel, String gotoAfterNoMatchesLabel, String targetVar) {
    // When this bit starts, the assumption is that the top of the stack contains just one element (that we care about): the value to be matched.
    // When this bit ends, that element will be cleared.
    //
    // At any given blockMatcher, we have two possibilities:
    // 1) This could be the last blockMatcher: If it matches, fall through, else go to the "done" label.
    // 2) Otherwise: If it matches, fall through and then go to the "done" label. If it doesn't match, go to the "nextMatcher" label.
    // As a simplification, we'll treat these two cases the same. In the first case, the "nextMatcher" label will just be the "done" label.
    String nextMatcherLabel = null;
    for (Iterator<EffesParser.BlockMatcherContext> iterator = ctx.blockMatcher().iterator(); iterator.hasNext(); ) {
      EffesParser.BlockMatcherContext blockMatcherContext = iterator.next();
      // First, set up the labels. We'll drop our label, and then compute the next one.
      if (nextMatcherLabel != null) {
        cc.labelAssigner.place(nextMatcherLabel);
      }
      if (iterator.hasNext()) {
        nextMatcherLabel = cc.labelAssigner.allocate("matcher");
      } else {
        nextMatcherLabel = gotoAfterNoMatchesLabel;
      }
      // Okay, labels are done. Reminder: stack here is just [valueToLookAt]. So in a new scope, evaluate the matcher and jump accordingly.
      final String nextMatcherLabelClosure = nextMatcherLabel;
      cc.scope.inNewScope(() -> {
        MatcherCompiler.compile(blockMatcherContext.matcher(), null, nextMatcherLabelClosure, true, cc, targetVar);
        if (!compileBlock(blockMatcherContext.block())) {
          cc.out.gotoAbs(gotoAfterMatchLabel);
        }
      });
    }
  }

  private void compileElseStatement(EffesParser.ElseStatContext ctx, String ifNotLabel, String ifChainEndLabel) {
    if (ctx == null) {
      return;
    }
    cc.out.gotoAbs(ifChainEndLabel); // previous block falls through to here, then jumps to end
    Dispatcher.dispatchConsumer(EffesParser.ElseStatContext.class)
      .when(EffesParser.IfElifContext.class, c -> {
        String nextIfNotLabel = cc.labelAssigner.allocate("elseIfNot");
        cc.labelAssigner.place(ifNotLabel);
        // See apply(StatIfContext) above for why we set up the scope this way
        cc.scope.inNewScope(() -> {
          expressionCompiler.apply(c.expression());
          cc.out.gotoIfNot(nextIfNotLabel);
          compileBlock(c.block());
        });
        compileElseStatement(c.elseStat(), nextIfNotLabel, ifChainEndLabel);
      })
      .when(EffesParser.IfElseContext.class, c -> {
        cc.labelAssigner.place(ifNotLabel);
        compileBlock(c.block());
      })
      .whenNull(() -> { })
      .on(ctx);
  }

  private void compileExpressionMultiline(EffesParser.ExpressionMultilineContext ctx, VarRef toVar) {
    // coming in, stack is []
    // going out, stack will be [], and toVar will be written to

    // put [expr] on the stack, and then just set up a bunch of matchers
    String nextMatcherLabel = null;
    String matchersDoneLabel = cc.labelAssigner.allocate("exprMatcherDone");
    EffesParser.ExpressionContext targetExpression = ctx.expression();
    String targetVar = ExpressionCompiler.tryGetLocalVar(targetExpression);
    expressionCompiler.apply(targetExpression);
    for (Iterator<EffesParser.ExpressionMatcherContext> iter = ctx.expressionMatchers().expressionMatcher().iterator(); iter.hasNext(); ) {
      EffesParser.ExpressionMatcherContext exprMatcher = iter.next();
      if (nextMatcherLabel != null) {
        cc.labelAssigner.place(nextMatcherLabel);
      }
      nextMatcherLabel = cc.labelAssigner.allocate("exprMatcher");
      String nextMatcherLabelClosure = nextMatcherLabel;
      cc.scope.inNewScope(() -> {
        MatcherCompiler.compile(exprMatcher.matcher(), null, nextMatcherLabelClosure, iter.hasNext(), cc, targetVar);
        expressionCompiler.apply(exprMatcher.expression());
        toVar.store(cc.out);
        cc.out.gotoAbs(matchersDoneLabel);
      });
    }
    assert nextMatcherLabel != null : ctx.getText();
    // nextMatcherLabel is the goto after failure for the last expression. There's no reasonable behavior in that case, so just fail.
    // The [expr] would have been popped off the stack now, because keepIfNoMatch is false for the last exprMatcher
    cc.labelAssigner.place(nextMatcherLabel);
    cc.out.fail(EvmStrings.escape("no alternatives matched"));
    // And finally, drop the "done" label so the previous expressions have somewhere to go to.
    cc.labelAssigner.place(matchersDoneLabel);
  }

  private VarRef getVarForAssign(EffesParser.QualifiedIdentNameContext ctx, String inferredType) {
    return Dispatcher.dispatch(EffesParser.QualifiedIdentNameStartContext.class, EffesParser.class, VarRef.class)
      .when(EffesParser.QualifiedIdentTypeContext.class, c -> {
        throw new CompilationException(ctx.start, ctx.stop, "static vars not supported");
      })
      .when(EffesParser.QualifiedIdentThisContext.class, c -> {
        if (ctx.qualifiedIdentNameMiddle() != null) {
          throw new CompilationException(ctx.start, ctx.stop, "unsupported");
        }
        VarRef field = getInstanceField(ctx, cc.getInstanceContextVar(ctx.IDENT_NAME().getSymbol(), ctx.IDENT_NAME().getSymbol()));
        if (field == null) {
          throw new CompilationException(ctx.IDENT_NAME().getSymbol(), ctx.IDENT_NAME().getSymbol(), "unknown field " + ctx.IDENT_NAME().getText());
        }
        return field;
      })
      .whenNull(() -> {
        // just a var name; it's either a local var or an instance var on "this"
        String varName = ctx.IDENT_NAME().getText();
        VarRef result = cc.scope.tryLookUp(varName);
        if (result == null) {
          VarRef instanceVar = cc.tryGetInstanceContextVar();
          if (instanceVar != null) {
            result = getInstanceField(ctx, instanceVar);
          }
          if (result == null) {
            result = cc.scope.allocateLocal(varName, false, inferredType);
          }
        }
        return result;
      })
      .on(ctx.qualifiedIdentNameStart());
  }

  private VarRef getInstanceField(EffesParser.QualifiedIdentNameContext ctx, VarRef instanceVar) {
    String fieldName = ctx.IDENT_NAME().getText();
    String varType = instanceVar.getType();
    if (!cc.typeInfo.hasField(varType, fieldName)) {
      return null;
    }
    return new VarRef.InstanceAndFieldVar(instanceVar, fieldName, cc.typeModuleName(varType), varType);
  }

  private static class BreakLabels {
    private final String labelForBreak;
    private final String labelForContinue;

    public BreakLabels(String labelForBreak, String labelForContinue) {
      this.labelForBreak = labelForBreak;
      this.labelForContinue = labelForContinue;
    }

    @Override
    public String toString() {
      return String.format("break=%s, continue=%s", labelForBreak, labelForContinue);
    }
  }
}
