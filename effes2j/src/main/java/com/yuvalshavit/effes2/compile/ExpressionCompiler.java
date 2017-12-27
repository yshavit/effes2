package com.yuvalshavit.effes2.compile;

import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

import org.antlr.v4.runtime.tree.TerminalNode;

import com.google.common.collect.Lists;
import com.yuvalshavit.effes2.parse.EffesLexer;
import com.yuvalshavit.effes2.parse.EffesParser;
import com.yuvalshavit.effes2.util.Dispatcher;
import com.yuvalshavit.effes2.util.EvmStrings;

@Dispatcher.SubclassesAreIn(EffesParser.class)
public class ExpressionCompiler extends CompileDispatcher<EffesParser.ExpressionContext> {
  public static final String THIS = "<this>";
  private final CompilerContext cc;

  public ExpressionCompiler(CompilerContext cc) {
    super(EffesParser.ExpressionContext.class);
    this.cc = cc;
  }

  @Dispatched
  public void apply(EffesParser.ExprCmpContext input) {
    final Runnable op;
    switch (input.cmp().getChild(TerminalNode.class, 0).getSymbol().getText()) {
      case "<":
        op = cc.out::lt;
        break;
      case "<=":
        op = cc.out::le;
        break;
      case "==":
        op = cc.out::eq;
        break;
      case "!=":
        op = cc.out::ne;
        break;
      case ">":
        op = cc.out::gt;
        break;
      case ">=":
        op = cc.out::ge;
        break;
      default:
        throw new IllegalArgumentException("unrecognized op: " + input);
    }
    binaryExpr(input.expression(), op);
  }

  @Dispatched
  public void apply(EffesParser.ExprThisContext input) {
    pvar(THIS);
  }

  @Dispatched
  public void apply(EffesParser.ExprStringLiteralContext input) {
    TerminalNode terminalNode = input.QUOTED_STRING();
    String quotedString = getQuotedString(terminalNode);
    cc.out.strPush(EvmStrings.escape(quotedString));
  }

  @Dispatched
  public void apply(EffesParser.ExprNegationContext input) {
    apply(input.expression());
    cc.out.negate();
  }

  @Dispatched
  public void apply(EffesParser.ExprInstantiationContext input) {
    String typeName = input.IDENT_TYPE().getSymbol().getText();
    int expectedArgs = cc.typeInfo.fieldsCount(typeName);
    List<EffesParser.ExpressionContext> args = input.argsInvocation() == null
      ? null
      : input.argsInvocation().expression();
    if (args == null) {
      args = Collections.emptyList();
    }
    if (expectedArgs != args.size()) {
      throw new CompilationException(input, String.format("expected %d argument%s, found %d", expectedArgs, expectedArgs == 1 ? "" : "s", args.size()));
    }
    Lists.reverse(args).forEach(this::apply);
    cc.out.call(cc.qualifyType(typeName), typeName);
  }

  @Dispatched
  public void apply(EffesParser.ExprVariableOrMethodInvocationContext input) {
    EffesParser.QualifiedIdentNameContext qualifiedName = input.qualifiedIdentName();
    EffesParser.ArgsInvocationContext argsInvocation = input.argsInvocation();

    if (argsInvocation != null) {
      if (!compileMethodInvocation(qualifiedName, argsInvocation)) {
        throw new CompilationException(input, "method does not specify a return value");
      }
      return;
    }

    EffesParser.QualifiedIdentNameStartContext qualifiedStart = qualifiedName.qualifiedIdentNameStart();
    List<EffesParser.QualifiedIdentNameMiddleContext> qualifiedMiddle = qualifiedName.qualifiedIdentNameMiddle();
    if (qualifiedStart == null) {
      // we're not in a case like "FooModule.staticField". Instead, we're in just "foo" or "foo.bar". Check that it's the former tpe, and if it is,
      // check that we know its type
      if (qualifiedMiddle.size() == 1) {
        EffesParser.QualifiedIdentNameMiddleContext localVarCtx = qualifiedMiddle.get(0);
        VarRef localVar = cc.scope.lookUp(localVarCtx.IDENT_NAME().getSymbol().getText());
        String localVarType = localVar.getType();
        if (localVarType == null) {
          throw new CompilationException(localVarCtx, "can't infer type");
        }
        String fieldName = qualifiedName.IDENT_NAME().getSymbol().getText();
        if (!cc.typeInfo.hasField(localVarType, fieldName)) {
          throw new CompilationException(localVarCtx, String.format("no field \"%s\" on type \"%s\"", fieldName, localVarType));
        }
        localVar.push(cc.out);
        cc.out.pushField(localVarType, fieldName);
        return;
      } else if (!qualifiedMiddle.isEmpty()) {
        throw new UnsupportedOperationException("qualified variables not supported");
      }
    }
    TerminalNode finalName = qualifiedName.IDENT_NAME();
    String symbolName = finalName.getSymbol().getText();
    pvar(symbolName);
  }

  @Dispatched
  public void apply(EffesParser.ExprParenthesisContext input) {
    apply(input.expression());
  }

  @Dispatched
  public void apply(EffesParser.ExprIntLiteralContext input) {
    cc.out.pushInt(input.INT().getSymbol().getText());
  }

  @Dispatched
  public void apply(EffesParser.ExprIsAContext input) {
    EffesParser.ExpressionContext expression = input.expression();
    apply(expression);
    boolean ifMatchedValue = input.NOT() == null;
    String isAFalse = cc.labelAssigner.allocate("isA_false");
    String isADone = cc.labelAssigner.allocate("isA_done");
    MatcherCompiler.compile(input.matcher(), null, isAFalse, false, cc, tryGetLocalVar(expression));
    cc.out.bool(Boolean.toString(ifMatchedValue)); // since MatcherCompiler.compile's labelIfMatched is null, a match falls through to here
    cc.out.gotoAbs(isADone);
    cc.labelAssigner.place(isAFalse);
    cc.out.bool(Boolean.toString(!ifMatchedValue));
    cc.labelAssigner.place(isADone);
  }

  @Dispatched
  public void apply(EffesParser.ExprPlusOrMinusContext input) {
    binaryExpr(input.expression(), input.PLUS() == null ? cc.out::iSub : cc.out::iAdd);
  }

  @Dispatched
  public void apply(EffesParser.ExprMultOrDivideContext input) {
    binaryExpr(input.expression(), input.ASTERISK() == null ? cc.out::iSub : cc.out::iMul);
  }

  public static String getQuotedString(TerminalNode node) {
    if (node.getSymbol().getType() != EffesLexer.QUOTED_STRING) {
      throw new IllegalArgumentException("wrong token type: " + EffesParser.VOCABULARY.getDisplayName(node.getSymbol().getType()));
    }
    String fullToken = node.getSymbol().getText();
    String withinQuotes = fullToken.substring(1, fullToken.length() - 1);
    return EvmStrings.unEscape(withinQuotes);
  }

  private void binaryExpr(List<EffesParser.ExpressionContext> exprs, Runnable op) {
    if (exprs.size() != 2) {
      throw new IllegalArgumentException("require exactly two expressions: " + exprs);
    }
    apply(exprs.get(0));
    apply(exprs.get(1));
    op.run();
  }

  private void pvar(String symbolName) {
    VarRef var = cc.scope.tryLookUp(symbolName);
    if (var == null) {
      VarRef.LocalVar thisVar = cc.tryGetInstanceContextVar();
      if (thisVar == null) {
        throw Scope.noSuchVariableException(symbolName);
      }
      String thisType = thisVar.getType();
      if (!cc.typeInfo.hasField(thisType, symbolName)) {
        throw new NoSuchElementException("no local or instance variable named " + symbolName);
      }
      thisVar.push(cc.out);
      cc.out.pushField(thisType, symbolName);
    } else {
      var.push(cc.out);
    }
  }

  public boolean compileMethodInvocation(EffesParser.QualifiedIdentNameContext targetCtx, EffesParser.ArgsInvocationContext argsInvocation) {
    // First the invocation args, in reverse order.
    Lists.reverse(argsInvocation.expression()).forEach(this::apply);

    // Then the target instance, if any.
    EffesParser.QualifiedIdentNameStartContext targetNameStartCtx = targetCtx.qualifiedIdentNameStart();
    List<EffesParser.QualifiedIdentNameMiddleContext> targetNameMidCtx = targetCtx.qualifiedIdentNameMiddle();
    final String methodName = targetCtx.IDENT_NAME().getText();
    String targetType = Dispatcher.dispatch(EffesParser.QualifiedIdentNameStartContext.class, String.class)
      .when(EffesParser.QualifiedIdentTypeContext.class, c -> {
        // static method
        if (targetNameMidCtx.size() == 1) {
          if (c.IDENT_TYPE().getSymbol().getText().equals("Stdio")) {
            String fieldName = targetNameMidCtx.get(0).IDENT_NAME().getSymbol().getText();
            switch (fieldName) {
              case "stdin":
                return EffesBuiltinType.CHAR_STREAM_IN.typeName();
              case "stdout":
                return EffesBuiltinType.CHAR_STREAM_OUT.typeName();
              default:
                throw new CompilationException(targetNameMidCtx.get(0), "Unrecognized Stdio field");
            }
          }
        }
        if (!targetNameMidCtx.isEmpty()) {
          throw new CompilationException(targetCtx, "can't have qualified static methods");
        }
        return c.IDENT_TYPE().getText() + TypeInfo.MODULE_PREFIX;
      })
      .when(EffesParser.QualifiedIdentThisContext.class, c -> {
        // method explicitly on "this"
        if (!targetNameMidCtx.isEmpty()) {
          throw new CompilationException(targetCtx, "can't have multi-part qualified methods");
        }
        VarRef instanceVar = cc.getInstanceContextVar(targetCtx.getStart(), argsInvocation.getStop());
        instanceVar.push(cc.out);
        return instanceVar.getType();
      })
      .whenNull(() -> {
        final String result;
        if (targetNameMidCtx.size() == 0) {
          VarRef.LocalVar instanceVar = cc.tryGetInstanceContextVar();
          if (instanceVar == null) {
            result = "";
          } else {
            result = instanceVar.getType();
            instanceVar.push(cc.out);
          }
        } else if (targetNameMidCtx.size() == 1) {
          String varName = targetNameMidCtx.get(0).IDENT_NAME().getText();
          VarRef targetVar = cc.scope.lookUp(varName);
          if (targetVar == null) {
            throw new CompilationException(targetCtx, "no local var named " + varName);
          }
          result = targetVar.getType();
          targetVar.push(cc.out);
        } else {
          throw new CompilationException(targetCtx, "unsupported multi-part qualified method invocation");
        }
        return result;
      })
      .on(targetNameStartCtx);

    if (targetType == null) {
      throw new CompilationException(targetCtx, "couldn't determine type");
    }

    MethodInfo methodInfo = cc.typeInfo.getMethod(targetType, methodName);
    if (methodInfo == null) {
      String description = targetType.isEmpty()
        ? ("module " + cc.moduleName)
        : ("type " + targetType);
      throw new CompilationException(targetCtx, description + " doesn't have a method named " + methodName);
    } else if (methodInfo.getDeclaredArgsCount() != argsInvocation.expression().size()) {
      throw new CompilationException(
        targetCtx,
        String.format(
          "%s.%s expects %d arg%s, found %d",
          targetType,
          methodName,
          methodInfo.getDeclaredArgsCount(),
          methodInfo.getDeclaredArgsCount() == 1 ? "" : "s",
          argsInvocation.expression().size()));
    }

    methodInfo.invoke(cc);
    return methodInfo.hasReturnValue();
  }

  static String tryGetLocalVar(EffesParser.ExpressionContext targetExpression) {
    final String targetVar;
    if (targetExpression instanceof EffesParser.ExprVariableOrMethodInvocationContext) {
      EffesParser.ExprVariableOrMethodInvocationContext varOrMethod = (EffesParser.ExprVariableOrMethodInvocationContext) targetExpression;
      if (varOrMethod.argsInvocation() == null) {
        EffesParser.QualifiedIdentNameContext name = varOrMethod.qualifiedIdentName();
        if (name.qualifiedIdentNameStart() == null && name.qualifiedIdentNameMiddle().isEmpty()) {
          targetVar = name.IDENT_NAME().getSymbol().getText();
        } else {
          targetVar = null;
        }
      } else {
        targetVar = null;
      }
    } else {
      targetVar = null;
    }
    return targetVar;
  }
}
