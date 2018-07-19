package com.yuvalshavit.effes2.compile;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

import org.antlr.v4.runtime.Token;

import com.yuvalshavit.effes2.parse.EffesParser;
import com.yuvalshavit.effes2.util.Dispatcher;
import com.yuvalshavit.effes2.util.EvmStrings;

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
    EffesParser.QualifiedIdentNameContext name = ctx.qualifiedIdentName();
    VarRef toVar = getVarForAssign(name, null);
    cc.scope.inNewScope(() -> compileExpressionMultiline(ctx.expressionMultiline(), toVar, name.start));
  }

  @Dispatched
  public void apply(EffesParser.StatForContext ctx) {
    Token iterVarToken = ctx.IDENT_NAME().getSymbol();
    EffesParser.ExpressionContext iterateOver = ctx.expression();
    EffesParser.BlockContext body = ctx.block();
    Token forToken = ctx.FOR().getSymbol();
    Token endToken = ctx.COLON().getSymbol();

    cc.scope.inNewScope(() -> {
      VarRef iterVar = cc.scope.allocateLocal(iterVarToken.getText(), false);
      VarRef iterLen = cc.scope.allocateAnonymous(Name.QualifiedType.forBuiltin(EffesBuiltinType.INTEGER));
      VarRef iterIdx = cc.scope.allocateAnonymous(Name.QualifiedType.forBuiltin(EffesBuiltinType.INTEGER));

      // Evaluate the iterateOver expression and get its length. Then initialize the idx var
      expressionCompiler.apply(iterateOver);
      cc.out.copy(iterateOver.start);
      cc.out.arrayLen(forToken);
      iterLen.store(forToken, cc.module, cc.out);
      cc.out.pushInt(forToken, "0");
      iterIdx.store(forToken, cc.module, cc.out);
      // Now the loop. At the top of each iteration, the stack's top contains the iterateOver value.
      String loopTopLabel = cc.labelAssigner.allocate("loopTop");
      String loopDoneLabel = cc.labelAssigner.allocate("loopDone");
      breakLabels.push(new BreakLabels(loopDoneLabel, loopTopLabel));
      cc.labelAssigner.place(forToken, loopTopLabel);
      // if arr.len >= idx goto done
      iterLen.push(forToken, cc.module, cc.out);
      iterIdx.push(forToken, cc.module, cc.out);
      cc.out.ge(forToken);
      cc.out.gotoIfNot(forToken, loopDoneLabel);
      // otherwise: var, body, increment, and then back to the top. Remember, as of now, the stack's top is the iterateOver value
      cc.out.copy(forToken);
      iterIdx.push(forToken, cc.module, cc.out);
      cc.out.arrayGet(forToken);
      iterVar.store(iterVarToken, cc.module, cc.out);
      compileBlock(body);
      iterIdx.push(forToken, cc.module, cc.out);
      cc.out.pushInt(forToken, "1");
      cc.out.iAdd(forToken);
      iterIdx.store(forToken, cc.module, cc.out);
      cc.out.gotoAbs(forToken, loopTopLabel);
      // end the loop
      cc.labelAssigner.place(endToken, loopDoneLabel);
      breakLabels.pop();
    });
    cc.out.pop(endToken); // the array being indexed
  }

  @Dispatched
  public void apply(EffesParser.StatAssignContext ctx) {
    final Name.QualifiedType type = (ctx.expression() instanceof EffesParser.ExprInstantiationContext)
      ? cc.type(((EffesParser.ExprInstantiationContext) ctx.expression()).IDENT_TYPE())
      : null;
    VarRef var = getVarForAssign(ctx.qualifiedIdentName(), type);
    cc.scope.inNewScope(() -> expressionCompiler.apply(ctx.expression()));
    var.store(ctx.qualifiedIdentName().start, cc.module, cc.out);
  }

  @Dispatched
  public void apply(EffesParser.StatIfContext ctx) {
    Dispatcher.dispatchConsumer(EffesParser.StatementIfConditionAndBodyContext.class)
      .when(EffesParser.IfElseSimpleContext.class, c -> {
        String ifNotLabel = cc.labelAssigner.allocate("ifNot");
        // Easy approach for now: always drop the ifChainEndLabel, even if we could have done without it. Not worrying about the extra GOTO
        String ifChainEndLabel = cc.labelAssigner.allocate("ifChainEnd");
        Token ifChainEndToken = ctx.stop;
        // Each expression+block is in its own scope. That way, for instance:
        //   if foo is One(val):
        //     youCanUse(val)
        //   else
        //     valIsNotAvailableHere()
        EffesParser.ExpressionContext condition = ctx.expression();
        Token ifNotDebugToken = elseNextToken(c.elseStat(), ifChainEndToken);
        cc.scope.inNewScope(() -> {
          expressionCompiler.apply(condition);
          cc.out.gotoIfNot(ifNotDebugToken, ifNotLabel);
          compileBlock(c.block());
          });
        compileElseStatement(c.elseStat(), ifNotDebugToken, ifNotLabel, ifChainEndLabel, ifChainEndToken);
        cc.labelAssigner.place(ifChainEndToken, ifChainEndLabel);
      })
      .when(EffesParser.IfMatchMultiContext.class, c-> {
        cc.scope.inNewScope(() -> {
          EffesParser.ExpressionContext condition = ctx.expression();
          expressionCompiler.apply(condition);
          String endLabel = cc.labelAssigner.allocate("matchersEnd");
          compileBlockMatchers(ctx.IF().getSymbol(), c.blockMatchers(), endLabel, endLabel, ExpressionCompiler.tryGetLocalVar(condition));
          cc.labelAssigner.place(ctx.IF().getSymbol(), endLabel);
        });
      })
      .on(ctx.statementIfConditionAndBody());
  }

  private static Token elseNextToken(EffesParser.ElseStatContext ctx, Token chainEndToken) {
    return Dispatcher.dispatch(EffesParser.ElseStatContext.class, Token.class)
      .when(EffesParser.IfElifContext.class, elseC1 -> elseC1.expression().start)
      .when(EffesParser.IfElseContext.class, elseC -> elseC.block().statement(0).start)
      .whenNull(() -> chainEndToken)
      .on(ctx);
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
      cc.out.pop(ExpressionCompiler.debugSymbolTokenForMethodInvocation(targetCtx));
    }
  }

  @Dispatched
  public void apply(EffesParser.StatWhileContext ctx) {
    String loopTopLabel = cc.labelAssigner.allocate("whileLoopTop");
    String loopEndLabel = cc.labelAssigner.allocate("whileLoopEnd");
    breakLabels.push(new BreakLabels(loopEndLabel, loopTopLabel));
    cc.scope.inNewScope(() -> {
      cc.labelAssigner.place(ctx.start, loopTopLabel);
      EffesParser.ExpressionContext condition = ctx.expression();
      String conditionVar = ExpressionCompiler.tryGetLocalVar(condition);
      expressionCompiler.apply(condition); // inside this scope, in case it's a "while foo is One(bar)" statement. We want the bar available here.
      Dispatcher.dispatchConsumer(EffesParser.StatementWhileConditionAndBodyContext.class)
        .when(EffesParser.WhileBodySimpleContext.class, c -> {
          cc.out.gotoIfNot(c.COLON().getSymbol(), loopEndLabel);
          if (!compileBlock(c.block())) {
            cc.out.gotoAbs(condition.start, loopTopLabel);
          }
        })
        .when(EffesParser.WhileBodyMultiMatchersContext.class, c -> {
          compileBlockMatchers(ctx.WHILE().getSymbol(), c.blockMatchers(), loopTopLabel, loopEndLabel, conditionVar);
        })
        .on(ctx.statementWhileConditionAndBody());
    });
    cc.labelAssigner.place(ctx.WHILE().getSymbol(), loopEndLabel);
  }

  @Dispatched
  public void apply(EffesParser.StatNoopContext ctx) {
    // nothing
  }

  @Dispatched
  public void apply(EffesParser.StatTypeAssertionContext ctx) {
    // for now, not even any bytecode; just assert it
    String varName = ctx.IDENT_NAME().getSymbol().getText();
    Name.QualifiedType qualifiedType = cc.type(ctx.IDENT_TYPE());
    cc.scope.replaceType(varName, qualifiedType);
  }

  @Dispatched
  public void apply(EffesParser.StatMatchContext ctx) {
    String endLabel = cc.labelAssigner.allocate("statMatcherEnd");
    EffesParser.ExpressionContext targetExpression = ctx.expression();
    expressionCompiler.apply(targetExpression);
    cc.scope.inNewScope(() -> MatcherCompiler.compile(ctx.matcher(), endLabel, endLabel, ctx.stop, false, cc, ExpressionCompiler.tryGetLocalVar(targetExpression)));
    cc.labelAssigner.place(ctx.stop, endLabel);
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
        cc.out.gotoAbs(c.start, label.labelForBreak);
      })
      .when(EffesParser.BlockStopContinueContext.class, c -> {
        BreakLabels label = breakLabels.peek();
        if (label == null) {
          throw new CompilationException(ctx, "no continue target available");
        }
        cc.out.gotoAbs(c.start, label.labelForContinue);
      })
      .when(EffesParser.BlockStopReturnContext.class, c -> {
        if (c.expression() != null) {
          expressionCompiler.apply(c.expression());
        } else if (c.expressionMultiline() != null) {
          VarRef rv = cc.scope.allocateAnonymous(null);
          compileExpressionMultiline(c.expressionMultiline(), rv, c.RETURN().getSymbol());
          rv.push(c.expressionMultiline().start, cc.module, cc.out);
        }
        cc.out.rtrn(c.RETURN().getSymbol());
      })
      .whenNull(() -> {})
      .on(blockStop);
    return blockStop != null;
  }

  private void compileBlockMatchers(
    Token endDebugSymbol,
    EffesParser.BlockMatchersContext ctx,
    String gotoAfterMatchLabel,
    String gotoAfterNoMatchesLabel,
    String targetVar)
  {
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
        cc.labelAssigner.place(blockMatcherContext.start, nextMatcherLabel);
      }
      if (iterator.hasNext()) {
        nextMatcherLabel = cc.labelAssigner.allocate("whileMultiTryNext");
      } else {
        nextMatcherLabel = gotoAfterNoMatchesLabel;
      }
      // Okay, labels are done. Reminder: stack here is just [valueToLookAt]. So in a new scope, evaluate the matcher and jump accordingly.
      final String nextMatcherLabelClosure = nextMatcherLabel;
      cc.scope.inNewScope(() -> {
        String matchedLabel = cc.labelAssigner.allocate("whileMultiMatched");
        Token colon = blockMatcherContext.COLON().getSymbol();
        MatcherCompiler.compile(blockMatcherContext.matcher(), matchedLabel, nextMatcherLabelClosure, colon, iterator.hasNext(), cc, targetVar);
        cc.out.label(blockMatcherContext.matcher().start, matchedLabel);
        if (!compileBlock(blockMatcherContext.block())) {
          cc.out.gotoAbs(endDebugSymbol, gotoAfterMatchLabel);
        }
      });
    }
  }

  private void compileElseStatement(EffesParser.ElseStatContext ctx, Token ifNotToken, String ifNotLabel, String ifChainEndLabel, Token ifChainEndToken) {
    if (ctx == null) {
      cc.labelAssigner.place(ifNotToken, ifNotLabel);
      return;
    }
    cc.out.gotoAbs(ifChainEndToken, ifChainEndLabel); // previous block falls through to here, then jumps to end
    Dispatcher.dispatchConsumer(EffesParser.ElseStatContext.class)
      .when(EffesParser.IfElifContext.class, c -> {
        String nextIfNotLabel = cc.labelAssigner.allocate("elseIfNot");
        Token nextIfNotToken = elseNextToken(c.elseStat(), ifChainEndToken);
        cc.labelAssigner.place(ifNotToken, ifNotLabel);
        // See apply(StatIfContext) above for why we set up the scope this way
        cc.scope.inNewScope(() -> {
          expressionCompiler.apply(c.expression());
          cc.out.gotoIfNot(nextIfNotToken, nextIfNotLabel);
          compileBlock(c.block());
        });
        compileElseStatement(c.elseStat(), nextIfNotToken, nextIfNotLabel, ifChainEndLabel, ifChainEndToken);
      })
      .when(EffesParser.IfElseContext.class, c -> {
        cc.labelAssigner.place(ifNotToken, ifNotLabel);
        compileBlock(c.block());
      })
      .whenNull(() -> { })
      .on(ctx);
  }

  private void compileExpressionMultiline(EffesParser.ExpressionMultilineContext ctx, VarRef toVar, Token assignmentTokenForDebugSymbol) {
    // coming in, stack is []
    // going out, stack will be [], and toVar will be written to

    // put [expr] on the stack, and then just set up a bunch of matchers
    String nextMatcherLabel = null;
    String matchersDoneLabel = cc.labelAssigner.allocate("exprMultiDone");
    EffesParser.ExpressionContext targetExpression = ctx.expression();
    String targetVar = ExpressionCompiler.tryGetLocalVar(targetExpression);
    expressionCompiler.apply(targetExpression);
    for (Iterator<EffesParser.ExpressionMatcherContext> iter = ctx.expressionMatchers().expressionMatcher().iterator(); iter.hasNext(); ) {
      EffesParser.ExpressionMatcherContext exprMatcher = iter.next();
      if (nextMatcherLabel != null) {
        cc.labelAssigner.place(exprMatcher.start, nextMatcherLabel);
      }
      nextMatcherLabel = cc.labelAssigner.allocate("exprMultiTryNext");
      String nextMatcherLabelClosure = nextMatcherLabel;
      cc.scope.inNewScope(() -> {
        String ifMatched = cc.labelAssigner.allocate("exprMultiMatched");
        Token colon = exprMatcher.COLON().getSymbol();
        MatcherCompiler.compile(exprMatcher.matcher(), ifMatched, nextMatcherLabelClosure, colon, iter.hasNext(), cc, targetVar);
        cc.out.label(exprMatcher.expression().start, ifMatched);
        expressionCompiler.apply(exprMatcher.expression());
        toVar.store(assignmentTokenForDebugSymbol, cc.module, cc.out);
        cc.out.gotoAbs(assignmentTokenForDebugSymbol, matchersDoneLabel);
      });
    }
    assert nextMatcherLabel != null : ctx.getText();
    // nextMatcherLabel is the goto after failure for the last expression. There's no reasonable behavior in that case, so just fail.
    // The [expr] would have been popped off the stack now, because keepIfNoMatch is false for the last exprMatcher
    cc.labelAssigner.place(assignmentTokenForDebugSymbol, nextMatcherLabel);
    cc.out.fail(assignmentTokenForDebugSymbol, EvmStrings.escape("no alternatives matched"));
    // And finally, drop the "done" label so the previous expressions have somewhere to go to.
    cc.labelAssigner.place(assignmentTokenForDebugSymbol, matchersDoneLabel);
  }

  private VarRef getVarForAssign(EffesParser.QualifiedIdentNameContext ctx, Name.QualifiedType inferredType) {
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
    Name.QualifiedType varType = instanceVar.getType();
    if (!cc.typeInfo.hasField(varType, fieldName)) {
      return null;
    }
    return new VarRef.InstanceAndFieldVar(instanceVar, fieldName, varType);
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
