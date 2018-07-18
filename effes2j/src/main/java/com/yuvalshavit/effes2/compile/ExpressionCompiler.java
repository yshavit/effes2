package com.yuvalshavit.effes2.compile;

import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

import org.antlr.v4.runtime.Token;
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
    final Consumer<Token> op;
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
    pvar(input.stop, THIS);
  }

  @Dispatched
  public void apply(EffesParser.ExprStringLiteralContext input) {
    TerminalNode terminalNode = input.QUOTED_STRING();
    String quotedString = getQuotedString(terminalNode);
    cc.out.strPush(terminalNode.getSymbol(), EvmStrings.escape(quotedString));
  }

  @Dispatched
  public void apply(EffesParser.ExprNegationContext input) {
    apply(input.expression());
    cc.out.negate(input.stop);
  }

  @Dispatched
  public void apply(EffesParser.ExprInstantiationContext input) {
    Name.QualifiedType type = cc.type(input.IDENT_TYPE());
    int expectedArgs = cc.typeInfo.fieldsCount(type);
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
    type.instantiate(input.stop, cc.module, cc.out);
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
    EffesParser.QualifiedIdentNameMiddleContext qualifiedMiddle = qualifiedName.qualifiedIdentNameMiddle();
    Token fieldNameTok = qualifiedName.IDENT_NAME().getSymbol();
    String fieldName = fieldNameTok.getText();
    if (qualifiedStart == null) {
      // we're not in a case like "FooModule.staticField". Instead, we're in just "foo" or "foo.bar".
      // If it's "foo.bar", check whether we know the "foo" type, and whether "bar" is a field on that type.
      // If it's "foo", then just look up the field.
      if (qualifiedMiddle != null) {
        VarRef localVar = cc.scope.lookUp(qualifiedMiddle.IDENT_NAME().getSymbol().getText());
        Name.QualifiedType localVarType = localVar.getType();
        if (localVarType == null) {
          throw new CompilationException(qualifiedMiddle, "can't infer type");
        }
        if (!cc.typeInfo.hasField(localVarType, fieldName)) {
          throw new CompilationException(qualifiedMiddle, String.format("no field \"%s\" on type \"%s\"", fieldName, localVarType));
        }
        localVar.push(fieldNameTok, cc.module, cc.out);
        cc.out.pushField(fieldNameTok, localVarType.evmDescriptor(cc.module), fieldName);
      } else {
        TerminalNode finalName = qualifiedName.IDENT_NAME();
        Token finalNameToken = finalName.getSymbol();
        String symbolName = finalNameToken.getText();
        pvar(finalNameToken, symbolName);
      }
    } else if (qualifiedStart instanceof EffesParser.QualifiedIdentThisContext) {
      VarRef thisVar = cc.getInstanceContextVar(qualifiedStart.getStart(), qualifiedStart.getStop());
      Name.QualifiedType thisVarType = thisVar.getType();
      if (!cc.typeInfo.hasField(thisVarType, fieldName)) {
        throw new CompilationException(qualifiedMiddle, "no field \"" + fieldName + "\" on type " + thisVarType);
      }
      cc.out.pushField(fieldNameTok, thisVarType.evmDescriptor(cc.module), fieldName);
    } else if (qualifiedStart instanceof EffesParser.QualifiedIdentTypeContext) {
      EffesParser.QualifiedIdentTypeContext moduleNameCtx = (EffesParser.QualifiedIdentTypeContext) qualifiedStart;
      String moduleName = moduleNameCtx.IDENT_TYPE().getSymbol().getText();
      if (!moduleName.equals("Stdio")) {
        throw new CompilationException(moduleNameCtx, "static fields not supported (except on Stdio)");
      }
      switch (fieldName) {
        case "stdin":
          cc.out.stdin(fieldNameTok);
          break;
        case "stdout":
//          cc.out.stdout();
//          break;
          throw new UnsupportedOperationException(); // TODO need to refresh maven; can't do it offline, I guess
        default:
          throw new CompilationException(qualifiedMiddle, "no such field on Stdio");
      }
    } else {
      throw new CompilationException(qualifiedStart, "internal error (unexpected subclass " + qualifiedStart.getClass().getName() + ")");
    }
  }

  @Dispatched
  public void apply(EffesParser.ExprParenthesisContext input) {
    apply(input.expression());
  }

  @Dispatched
  public void apply(EffesParser.ExprIntLiteralContext input) {
    Token tok = input.INT().getSymbol();
    cc.out.pushInt(tok, tok.getText());
  }

  @Dispatched
  public void apply(EffesParser.ExprIsAContext input) {
    EffesParser.ExpressionContext expression = input.expression();
    apply(expression);
    boolean ifMatchedValue = input.NOT() == null;
    String isAFalse = cc.labelAssigner.allocate("isA_false");
    String isATrue = cc.labelAssigner.allocate("isA_true");
    String isADone = cc.labelAssigner.allocate("isA_done");
    MatcherCompiler.compile(input.matcher(), isATrue, isAFalse, input.stop, false, cc, tryGetLocalVar(expression));
    Token tok = input.stop;
    cc.labelAssigner.place(tok, isATrue);
    bool(tok, ifMatchedValue); // since MatcherCompiler.compile's labelIfMatched is null, a match falls through to here
    cc.out.gotoAbs(tok, isADone);
    cc.labelAssigner.place(tok, isAFalse);
    bool(tok, !ifMatchedValue);
    cc.labelAssigner.place(tok, isADone);
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

  private void binaryExpr(List<EffesParser.ExpressionContext> exprs, Consumer<Token> op) {
    if (exprs.size() != 2) {
      throw new IllegalArgumentException("require exactly two expressions: " + exprs);
    }
    apply(exprs.get(1));
    apply(exprs.get(0));
    op.accept(exprs.get(0).stop);
  }

  private void pvar(Token tok, String symbolName) {
    VarRef var = cc.scope.tryLookUp(symbolName);
    if (var == null) {
      VarRef thisVar = cc.tryGetInstanceContextVar();
      if (thisVar == null) {
        throw Scope.noSuchVariableException(symbolName);
      }
      Name.QualifiedType thisType = thisVar.getType();
      if (!cc.typeInfo.hasField(thisType, symbolName)) {
        throw new NoSuchElementException("no local or instance variable named " + symbolName);
      }
      thisVar.push(tok, cc.module, cc.out);
      cc.out.pushField(tok, thisType.evmDescriptor(cc.module), symbolName);
    } else {
      var.push(tok, cc.module, cc.out);
    }
  }

  public boolean compileMethodInvocation(EffesParser.QualifiedIdentNameContext targetCtx, EffesParser.ArgsInvocationContext argsInvocation) {
    // First the target instance, if any.
    EffesParser.QualifiedIdentNameStartContext targetNameStartCtx = targetCtx.qualifiedIdentNameStart();
    EffesParser.QualifiedIdentNameMiddleContext targetNameMidCtx = targetCtx.qualifiedIdentNameMiddle();
    final String methodName = targetCtx.IDENT_NAME().getText();
    Name.EvmScope targetType = Dispatcher.dispatch(EffesParser.QualifiedIdentNameStartContext.class, Name.EvmScope.class)
      .when(EffesParser.QualifiedIdentTypeContext.class, c -> {
        // static method
        if (targetNameMidCtx != null) {
          throw new CompilationException(targetCtx, "can't have qualified static methods");
        }
        return CompilerContext.readModuleName(c.IDENT_TYPE());
      })
      .when(EffesParser.QualifiedIdentThisContext.class, c -> {
        // method explicitly on "this"
        if (targetNameMidCtx != null) {
          throw new CompilationException(targetCtx, "can't have multi-part qualified methods");
        }
        VarRef instanceVar = cc.getInstanceContextVar(targetCtx.getStart(), argsInvocation.getStop());
        instanceVar.push(argsInvocation.stop, cc.module, cc.out);
        return instanceVar.getType();
      })
      .whenNull(() -> {
        final Name.EvmScope result;
        if (targetNameMidCtx == null) {
          VarRef instanceVar = cc.tryGetInstanceContextVar();
          if (instanceVar == null) {
            result = cc.module;
          } else {
            result = instanceVar.getType();
            instanceVar.push(targetCtx.stop, cc.module, cc.out);
          }
        } else {
          String varName = targetNameMidCtx.IDENT_NAME().getText();
          VarRef targetVar = cc.scope.lookUp(varName);
          if (targetVar == null) {
            throw new CompilationException(targetCtx, "no local var named " + varName);
          }
          result = targetVar.getType();
          targetVar.push(targetCtx.stop, cc.module, cc.out);
        }
        return result;
      })
      .on(targetNameStartCtx);
    if (targetType == null) {
      throw new CompilationException(targetCtx, "couldn't determine type");
    }

    // Then the invocation args, in reverse order.
    Lists.reverse(argsInvocation.expression()).forEach(this::apply);

    MethodInfo methodInfo = cc.typeInfo.getMethod(targetType, methodName);
    if (methodInfo == null) {
      throw new CompilationException(targetCtx, "no method named " + methodName);
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

    methodInfo.invoke(argsInvocation.stop, cc);
    return methodInfo.hasReturnValue();
  }

  static String tryGetLocalVar(EffesParser.ExpressionContext targetExpression) {
    final String targetVar;
    if (targetExpression instanceof EffesParser.ExprVariableOrMethodInvocationContext) {
      EffesParser.ExprVariableOrMethodInvocationContext varOrMethod = (EffesParser.ExprVariableOrMethodInvocationContext) targetExpression;
      if (varOrMethod.argsInvocation() == null) {
        EffesParser.QualifiedIdentNameContext name = varOrMethod.qualifiedIdentName();
        if (name.qualifiedIdentNameStart() == null && name.qualifiedIdentNameMiddle() == null) {
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

  private void bool(Token tok, boolean value) {
    String valueString = value ? "True" : "False";
    cc.out.bool(tok, valueString);
  }
}
