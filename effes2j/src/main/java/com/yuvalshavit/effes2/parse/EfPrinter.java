package com.yuvalshavit.effes2.parse;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

public class EfPrinter {
  private static final int INDENTATION_SIZE = 2;

  private EfPrinter() {
  }

  public static void write(PrintWriter out, ParseTree tree) {
    tree.accept(new PrintVisitor(out));
  }

  @SuppressWarnings("unused") // useful for debugging, if nothing else!
  public static String writeToString(ParseTree tree) {
    StringWriter stringWriter = new StringWriter();
    PrintWriter printer = new PrintWriter(stringWriter);
    write(printer, tree);
    printer.flush();
    return stringWriter.toString();
  }

  private static class PrintVisitor extends VoidEffesParserVisitor {
    private int indentation;
    /** or -1 for none */
    private int pendingIndent = -1;
    private final PrintWriter out;

    public PrintVisitor(PrintWriter out) {
      this.out = out;
    }

    private void nl(int indentChange) {
      indentation += indentChange;
      if (indentChange < 0) {
        pendingIndent = indentation;
      } else {
        for (int i = 0; i < pendingIndent * INDENTATION_SIZE; ++i) {
          out.write(' ');
        }
        if (indentChange == 0) {
          // we may have a dedent coming, so hold on
          pendingIndent = indentation;
        } else {
          flushNl();
          pendingIndent = indentation;
          flushNl();
        }
      }
    }

    private void nl() {
      nl(0);
    }

    private void dispatch(ParseTree ctx) {
      ctx.accept(this);
    }

    private PrintWriter flushNl() {
      if (pendingIndent >= 0) {
        out.println();
        for (int i = 0; i < indentation * INDENTATION_SIZE; ++i) {
          out.write(' ');
        }
        pendingIndent = -1;
      }
      return out;
    }

    private static <R> void handleMany(Iterable<R> ctxs, Consumer<R> handler, Runnable delimiter) {
      if (ctxs != null) {
        Iterator<R> iter = ctxs.iterator();
        if (iter.hasNext()) {
          handler.accept(iter.next());
        }
        while (iter.hasNext()) {
          delimiter.run();
          handler.accept(iter.next());
        }
      }
    }

    private <R> void handleManyWithCommas(Iterable<R> ctxs, Consumer<R> handler) {
      handleMany(ctxs, handler, () -> write(", "));
    }

    private PrintVisitor write(String format, Object... args) {
      for (int i = 0; i < args.length; ++i) {
        Object arg = args[i];
        if (arg instanceof TerminalNode) {
          args[i] = tokenText((TerminalNode) arg);
        }
      }
      flushNl().printf(format, args);
      return this;
    }

    private void write(TerminalNode terminalNode) {
      flushNl().print(tokenText(terminalNode));
    }

    private void write(char c) {
      flushNl().print(c);
    }

    private static <R> boolean handleIfNotNull(R rule, Consumer<R> handler) {
      if (rule != null) {
        handler.accept(rule);
        return true;
      } else {
        return false;
      }
    }

    private static <R> void handleMany(Iterable<R> rules, Consumer<R> handlers) {
      handleMany(rules, handlers, () -> {
      });
    }

    private static String tokenText(TerminalNode terminal) {
      return terminal.getSymbol().getText();
    }

    @Override
    protected void seeDeclaration(EffesParser.DeclarationContext ctx) {
      handleIfNotNull(ctx.typeDeclaration(), this::seeTypeDeclaration);
      handleIfNotNull(ctx.methodDeclaration(), this::seeMethodDeclaration);
    }

    @Override
    protected void seeFile(EffesParser.FileContext ctx) {
      handleMany(ctx.importLine(), this::seeImportLine);
      handleMany(ctx.declaration(), this::seeDeclaration);
    }

    @Override
    protected void seeImportLine(EffesParser.ImportLineContext ctx) {
      write("import %s: ", ctx.IDENT_TYPE());
      dispatch(ctx.importDeclarations());
      nl();
    }

    @Override
    protected void seeImportDeclaration(EffesParser.ImportDeclarationContext ctx) {
      // doesn't matter if it's a type or name
      TerminalNode nameTok = (TerminalNode) ctx.getChild(0);
      write(nameTok);
    }

    @Override
    protected void seeExplicitImports(EffesParser.ExplicitImportsContext ctx) {
      handleManyWithCommas(ctx.importDeclaration(), this::seeImportDeclaration);
    }

    @Override
    protected void seeAllImports(EffesParser.AllImportsContext ctx) {
      write('*');
    }

    @Override
    protected void seeArgsDeclaration(EffesParser.ArgsDeclarationContext ctx) {
      // TODO need a handler here if there are no names?
      write(ctx.IDENT_NAME().stream().map(PrintVisitor::tokenText).collect(Collectors.joining(", ", "(", ")")));
    }

    @Override
    protected void seeArgsInvocation(EffesParser.ArgsInvocationContext ctx) {
      // TODO need a handler here if there are none
      write('(');
      handleManyWithCommas(ctx.expression(), this::dispatch);
      write(')');
    }

    @Override
    protected void seeMethodDeclaration(EffesParser.MethodDeclarationContext ctx) {
      write(ctx.IDENT_NAME());
      seeArgsDeclaration(ctx.argsDeclaration());
      if (ctx.ARROW() != null) {
        write(" ->");
      }
      write(":");
      seeBlock(ctx.block());
      flushNl(); // flush the dedent from seeBlock, so that we can add a double space
      nl();
    }

    @Override
    protected void seeTypeDeclaration(EffesParser.TypeDeclarationContext ctx) {
      write("type %s", ctx.IDENT_TYPE());
      handleIfNotNull(ctx.argsDeclaration(), this::seeArgsDeclaration);
      List<EffesParser.MethodDeclarationContext> methodDeclarations = ctx.methodDeclaration();
      if (methodDeclarations == null || methodDeclarations.isEmpty()) {
        nl();
      } else {
        write(':');
        nl(1);
        methodDeclarations.forEach(this::seeMethodDeclaration);
        nl(-1);
      }
    }

    @Override
    protected void seeBlock(EffesParser.BlockContext ctx) {
      nl(1);
      List<EffesParser.StatementContext> normalStatements = ctx.statement();
      if (normalStatements == null) {
        visit(ctx.blockStop());
        dispatch(ctx.blockStop());
      } else {
        normalStatements.forEach(this::dispatch);
        handleIfNotNull(ctx.blockStop(), this::dispatch);
      }
      nl(-1);
    }

    @Override
    protected void seeBlockStopBreak(EffesParser.BlockStopBreakContext ctx) {
      write("break").nl();
    }

    @Override
    protected void seeBlockStopContinue(EffesParser.BlockStopContinueContext ctx) {
      write("continue").nl();
    }

    @Override
    protected void seeBlockStopReturn(EffesParser.BlockStopReturnContext ctx) {
      if (ctx.expression() == null && ctx.expressionMultiline() == null) {
        nl();
      } else {
        write(' ');
        if (handleIfNotNull(ctx.expression(), this::dispatch)) {
          nl();
        } else {
          handleIfNotNull(ctx.expressionMultiline(), this::dispatch);
        }
      }
    }

    @Override
    protected void seeIfElif(EffesParser.IfElifContext ctx) {
      write("elif ");
      dispatch(ctx.expression());
      write(':');
      dispatch(ctx.block());
      handleIfNotNull(ctx.elseStat(), this::dispatch);
    }

    @Override
    protected void seeIfElse(EffesParser.IfElseContext ctx) {
      write("else:");
      seeBlock(ctx.block());
    }

    @Override
    protected void seeStatNoop(EffesParser.StatNoopContext ctx) {
      write(":::").nl();
    }

    @Override
    protected void seeStatMatch(EffesParser.StatMatchContext ctx) {
      dispatch(ctx.expression());
      write(" ::: ");
      dispatch(ctx.matcher());
      nl();
    }

    @Override
    protected void seeStatWhile(EffesParser.StatWhileContext ctx) {
      write("while ");
      dispatch(ctx.expression());
      dispatch(ctx.statementWhileConditionAndBody());
    }

    @Override
    protected void seeStatFor(EffesParser.StatForContext ctx) {
      write("for %s in ", ctx.IDENT_NAME());
      dispatch(ctx.expression());
      write(':');
      seeBlock(ctx.block());
    }

    @Override
    protected void seeStatIf(EffesParser.StatIfContext ctx) {
      write("if ");
      dispatch(ctx.expression());
      dispatch(ctx.statementIfConditionAndBody());
    }

    @Override
    protected void seeStatMethodInvoke(EffesParser.StatMethodInvokeContext ctx) {
      seeQualifiedIdentName(ctx.qualifiedIdentName());
      seeArgsInvocation(ctx.argsInvocation());
      nl();
    }

    @Override
    protected void seeStatAssign(EffesParser.StatAssignContext ctx) {
      seeQualifiedIdentName(ctx.qualifiedIdentName());
      write(" = ");
      dispatch(ctx.expression());
      nl();
    }

    @Override
    protected void seeStatAssignMultiline(EffesParser.StatAssignMultilineContext ctx) {
      seeQualifiedIdentName(ctx.qualifiedIdentName());
      write(" = ");
      dispatch(ctx.expressionMultiline());
      nl();
    }

    @Override
    protected void seeStatVarDeclare(EffesParser.StatVarDeclareContext ctx) {
      write("%s = ?", ctx.IDENT_NAME()).nl();
    }
    
    @Override
    protected void seeIfElseSimple(EffesParser.IfElseSimpleContext ctx) {
      write(':');
      seeBlock(ctx.block());
      handleIfNotNull(ctx.elseStat(), this::dispatch);
    }

    @Override
    protected void seeIfMatchMulti(EffesParser.IfMatchMultiContext ctx) {
      write(" is:");
      seeBlockMatchers(ctx.blockMatchers());
    }

    @Override
    protected void seeWhileBodySimple(EffesParser.WhileBodySimpleContext ctx) {
      write(':');
      seeBlock(ctx.block());
    }

    @Override
    protected void seeWhileBodyMultiMatchers(EffesParser.WhileBodyMultiMatchersContext ctx) {
      write(" is:");
      seeBlockMatchers(ctx.blockMatchers());
    }

    @Override
    protected void seeCmp(EffesParser.CmpContext ctx) {
      // cheat a bit :) since this is really just a hack to give the lexer an enum
      write(((TerminalNode) ctx.getChild(0)));
    }

    @Override
    protected void seeExprIsA(EffesParser.ExprIsAContext ctx) {
      dispatch(ctx.expression());
      write(" is ");
      if (ctx.NOT() != null) {
        write("not ");
      }
      dispatch(ctx.matcher());
    }

    @Override
    protected void seeExprPlusOrMinus(EffesParser.ExprPlusOrMinusContext ctx) {
      dispatch(ctx.expression(0));
      write(' ');
      write(((TerminalNode) ctx.getChild(1))); // + or -
      write(' ');
      dispatch(ctx.expression(1));
    }

    @Override
    protected void seeExprCmp(EffesParser.ExprCmpContext ctx) {
      dispatch(ctx.expression(0));
      write(' ');
      seeCmp(ctx.cmp());
      write(' ');
      dispatch(ctx.expression(1));
    }

    @Override
    protected void seeExprInstantiation(EffesParser.ExprInstantiationContext ctx) {
      write(ctx.IDENT_TYPE());
      handleIfNotNull(ctx.argsInvocation(), this::seeArgsInvocation);
    }

    @Override
    protected void seeExprVariableOrMethodInvocation(EffesParser.ExprVariableOrMethodInvocationContext ctx) {
      seeQualifiedIdentName(ctx.qualifiedIdentName());
      handleIfNotNull(ctx.argsInvocation(), this::seeArgsInvocation);
    }

    @Override
    protected void seeExprMultOrDivide(EffesParser.ExprMultOrDivideContext ctx) {
      dispatch(ctx.expression(0));
      write(' ');
      write(((TerminalNode) ctx.getChild(1))); // * or -
      write(' ');
      dispatch(ctx.expression(1));
    }

    @Override
    protected void seeExprIntLiteral(EffesParser.ExprIntLiteralContext ctx) {
      write(ctx.INT());
    }

    @Override
    protected void seeExprThis(EffesParser.ExprThisContext ctx) {
      write("this");
    }

    @Override
    protected void seeExprNegation(EffesParser.ExprNegationContext ctx) {
      write("not ");
      dispatch(ctx.expression());
    }

    @Override
    protected void seeExprStringLiteral(EffesParser.ExprStringLiteralContext ctx) {
      write(ctx.QUOTED_STRING());
    }

    @Override
    protected void seeExprParenthesis(EffesParser.ExprParenthesisContext ctx) {
      write('(');
      dispatch(ctx.expression());
      write(')');
    }

    @Override
    public void seeExpressionMultiline(EffesParser.ExpressionMultilineContext ctx) {
      write("if ");
      dispatch(ctx.expression());
      write(" is:");
      seeExpressionMatchers(ctx.expressionMatchers());
    }

    @Override
    protected void seeQualifiedIdentName(EffesParser.QualifiedIdentNameContext ctx) {
      if (handleIfNotNull(ctx.qualifiedIdentNameStart(), this::dispatch)) {
        write('.');
      }
      for (EffesParser.QualifiedIdentNameMiddleContext middle : ctx.qualifiedIdentNameMiddle()) {
        seeQualifiedIdentNameMiddle(middle);
        write('.');
      }
      write(ctx.IDENT_NAME());
    }

    @Override
    protected void seeQualifiedIdentNameMiddle(EffesParser.QualifiedIdentNameMiddleContext ctx) {
      write(ctx.IDENT_NAME());
      // handleIfNotNull(ctx.argsInvocation(), this::seeArgsInvocation);
    }

    @Override
    protected void seeQualifiedIdentType(EffesParser.QualifiedIdentTypeContext ctx) {
      write(ctx.IDENT_TYPE());
    }

    @Override
    protected void seeQualifiedIdentThis(EffesParser.QualifiedIdentThisContext ctx) {
      write("this");
    }

    @Override
    protected void seeBlockMatcher(EffesParser.BlockMatcherContext ctx) {
      dispatch(ctx.matcher());
      write(':');
      seeBlock(ctx.block());
    }

    @Override
    protected void seeBlockMatchers(EffesParser.BlockMatchersContext ctx) {
      nl(1);
      handleMany(ctx.blockMatcher(), this::seeBlockMatcher);
      nl(-1);
    }

    @Override
    protected void seeExpressionMatchers(EffesParser.ExpressionMatchersContext ctx) {
      nl(1);
      handleMany(ctx.expressionMatcher(), this::seeExpressionMatcher);
      nl(-1);
    }

    @Override
    protected void seeExpressionMatcher(EffesParser.ExpressionMatcherContext ctx) {
      dispatch(ctx.matcher());
      write(": ");
      dispatch(ctx.expression());
      nl();
    }

    @Override
    protected void seeMatcherAny(EffesParser.MatcherAnyContext ctx) {
      write('*');
    }

    @Override
    protected void seeMatcherWithPattern(EffesParser.MatcherWithPatternContext ctx) {
      if (ctx.IDENT_NAME() != null) {
        if (ctx.AT() != null) {
          write('@');
        }
        write(ctx.IDENT_NAME());
        write(' ');
      }
      dispatch(ctx.matcherPattern());
    }

    @Override
    protected void seeMatcherJustName(EffesParser.MatcherJustNameContext ctx) {
      if (ctx.AT() != null) {
        write('@');
      }
      write(ctx.IDENT_NAME());
      if (ctx.SUCH_THAT() != null) {
        write(":? ");
        dispatch(ctx.expression());
      }
    }

    @Override
    protected void seePatternType(EffesParser.PatternTypeContext ctx) {
      write(ctx.IDENT_TYPE());
      if (ctx.PAREN_OPEN() != null) {
        write('(');
        handleManyWithCommas(ctx.matcher(), this::dispatch);
        write(')');
      }
    }

    @Override
    protected void seePatternRegex(EffesParser.PatternRegexContext ctx) {
      write("~/");
      handleIfNotNull(ctx.REGEX(), this::write);
      write('/');
    }

    @Override
    protected void seePatternStringLiteral(EffesParser.PatternStringLiteralContext ctx) {
      write(ctx.QUOTED_STRING());
    }
  }
}
