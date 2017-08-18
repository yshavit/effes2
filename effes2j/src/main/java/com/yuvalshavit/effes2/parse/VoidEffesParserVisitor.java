package com.yuvalshavit.effes2.parse;

import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;

public abstract class VoidEffesParserVisitor implements EffesParserVisitor<Void> {
  @Override
  final public Void visitDeclaration(EffesParser.DeclarationContext ctx) { seeDeclaration(ctx); return null; }
  protected abstract void seeDeclaration(EffesParser.DeclarationContext ctx);

  @Override
  final public Void visitFile(EffesParser.FileContext ctx) { seeFile(ctx); return null; }
  protected abstract void seeFile(EffesParser.FileContext ctx);

  @Override
  final public Void visitImportLine(EffesParser.ImportLineContext ctx) { seeImportLine(ctx); return null; }
  protected abstract void seeImportLine(EffesParser.ImportLineContext ctx);

  @Override
  final public Void visitImportDeclaration(EffesParser.ImportDeclarationContext ctx) { seeImportDeclaration(ctx); return null; }
  protected abstract void seeImportDeclaration(EffesParser.ImportDeclarationContext ctx);

  @Override
  final public Void visitExplicitImports(EffesParser.ExplicitImportsContext ctx) { seeExplicitImports(ctx); return null; }
  protected abstract void seeExplicitImports(EffesParser.ExplicitImportsContext ctx);

  @Override
  final public Void visitAllImports(EffesParser.AllImportsContext ctx) { seeAllImports(ctx); return null; }
  protected abstract void seeAllImports(EffesParser.AllImportsContext ctx);

  @Override
  final public Void visitArgsDeclaration(EffesParser.ArgsDeclarationContext ctx) { seeArgsDeclaration(ctx); return null; }
  protected abstract void seeArgsDeclaration(EffesParser.ArgsDeclarationContext ctx);

  @Override
  final public Void visitArgsInvocation(EffesParser.ArgsInvocationContext ctx) { seeArgsInvocation(ctx); return null; }
  protected abstract void seeArgsInvocation(EffesParser.ArgsInvocationContext ctx);

  @Override
  final public Void visitMethodDeclaration(EffesParser.MethodDeclarationContext ctx) { seeMethodDeclaration(ctx); return null; }
  protected abstract void seeMethodDeclaration(EffesParser.MethodDeclarationContext ctx);

  @Override
  final public Void visitTypeDeclaration(EffesParser.TypeDeclarationContext ctx) { seeTypeDeclaration(ctx); return null; }
  protected abstract void seeTypeDeclaration(EffesParser.TypeDeclarationContext ctx);

  @Override
  final public Void visitBlock(EffesParser.BlockContext ctx) { seeBlock(ctx); return null; }
  protected abstract void seeBlock(EffesParser.BlockContext ctx);

  @Override
  final public Void visitBlockStop(EffesParser.BlockStopContext ctx) { seeBlockStop(ctx); return null; }
  protected abstract void seeBlockStop(EffesParser.BlockStopContext ctx);

  @Override
  final public Void visitIfElif(EffesParser.IfElifContext ctx) { seeIfElif(ctx); return null; }
  protected abstract void seeIfElif(EffesParser.IfElifContext ctx);

  @Override
  final public Void visitIfElse(EffesParser.IfElseContext ctx) { seeIfElse(ctx); return null; }
  protected abstract void seeIfElse(EffesParser.IfElseContext ctx);

  @Override
  final public Void visitStatNoop(EffesParser.StatNoopContext ctx) { seeStatNoop(ctx); return null; }
  protected abstract void seeStatNoop(EffesParser.StatNoopContext ctx);

  @Override
  public Void visitStatMatch(EffesParser.StatMatchContext ctx) { seeStatMatch(ctx); return null; }
  protected abstract void seeStatMatch(EffesParser.StatMatchContext ctx);

  @Override
  final public Void visitStatWhile(EffesParser.StatWhileContext ctx) { seeStatWhile(ctx); return null; }
  protected abstract void seeStatWhile(EffesParser.StatWhileContext ctx);

  @Override
  final public Void visitStatFor(EffesParser.StatForContext ctx) { seeStatFor(ctx); return null; }
  protected abstract void seeStatFor(EffesParser.StatForContext ctx);

  @Override
  final public Void visitStatIf(EffesParser.StatIfContext ctx) { seeStatIf(ctx); return null; }
  protected abstract void seeStatIf(EffesParser.StatIfContext ctx);

  @Override
  final public Void visitStatMethodInvoke(EffesParser.StatMethodInvokeContext ctx) { seeStatMethodInvoke(ctx); return null; }
  protected abstract void seeStatMethodInvoke(EffesParser.StatMethodInvokeContext ctx);

  @Override
  final public Void visitStatAssign(EffesParser.StatAssignContext ctx) { seeStatAssign(ctx); return null; }
  protected abstract void seeStatAssign(EffesParser.StatAssignContext ctx);

  @Override
  final public Void visitStatAssignMultiline(EffesParser.StatAssignMultilineContext ctx) { seeStatAssignMultiline(ctx); return null; }
  protected abstract void seeStatAssignMultiline(EffesParser.StatAssignMultilineContext ctx);

  @Override
  final public Void visitStatVarDeclare(EffesParser.StatVarDeclareContext ctx) { seeStatVarDeclare(ctx); return null; }
  protected abstract void seeStatVarDeclare(EffesParser.StatVarDeclareContext ctx);

  @Override
  final public Void visitStatQualifiedAssign(EffesParser.StatQualifiedAssignContext ctx) { seeStatQualifiedAssign(ctx); return null; }
  protected abstract void seeStatQualifiedAssign(EffesParser.StatQualifiedAssignContext ctx);

  @Override
  final public Void visitIfElseSimple(EffesParser.IfElseSimpleContext ctx) { seeIfElseSimple(ctx); return null; }
  protected abstract void seeIfElseSimple(EffesParser.IfElseSimpleContext ctx);

  @Override
  final public Void visitIfMatchMulti(EffesParser.IfMatchMultiContext ctx) { seeIfMatchMulti(ctx); return null; }
  protected abstract void seeIfMatchMulti(EffesParser.IfMatchMultiContext ctx);

  @Override
  final public Void visitWhileBodySimple(EffesParser.WhileBodySimpleContext ctx) { seeWhileBodySimple(ctx); return null; }
  protected abstract void seeWhileBodySimple(EffesParser.WhileBodySimpleContext ctx);

  @Override
  final public Void visitWhileBodyMultiMatchers(EffesParser.WhileBodyMultiMatchersContext ctx) { seeWhileBodyMultiMatchers(ctx); return null; }
  protected abstract void seeWhileBodyMultiMatchers(EffesParser.WhileBodyMultiMatchersContext ctx);

  @Override
  final public Void visitCmp(EffesParser.CmpContext ctx) { seeCmp(ctx); return null; }
  protected abstract void seeCmp(EffesParser.CmpContext ctx);

  @Override
  final public Void visitExprIsA(EffesParser.ExprIsAContext ctx) { seeExprIsA(ctx); return null; }
  protected abstract void seeExprIsA(EffesParser.ExprIsAContext ctx);

  @Override
  final public Void visitExprPlusOrMinus(EffesParser.ExprPlusOrMinusContext ctx) { seeExprPlusOrMinus(ctx); return null; }
  protected abstract void seeExprPlusOrMinus(EffesParser.ExprPlusOrMinusContext ctx);

  @Override
  final public Void visitExprCmp(EffesParser.ExprCmpContext ctx) { seeExprCmp(ctx); return null; }
  protected abstract void seeExprCmp(EffesParser.ExprCmpContext ctx);

  @Override
  final public Void visitExprInstantiation(EffesParser.ExprInstantiationContext ctx) { seeExprInstantiation(ctx); return null; }
  protected abstract void seeExprInstantiation(EffesParser.ExprInstantiationContext ctx);

  @Override
  final public Void visitExprVariableOrMethodInvocation(EffesParser.ExprVariableOrMethodInvocationContext ctx) { seeExprVariableOrMethodInvocation(ctx); return null; }
  protected abstract void seeExprVariableOrMethodInvocation(EffesParser.ExprVariableOrMethodInvocationContext ctx);

  @Override
  final public Void visitExprMultOrDivide(EffesParser.ExprMultOrDivideContext ctx) { seeExprMultOrDivide(ctx); return null; }
  protected abstract void seeExprMultOrDivide(EffesParser.ExprMultOrDivideContext ctx);

  @Override
  final public Void visitExprIntLiteral(EffesParser.ExprIntLiteralContext ctx) { seeExprIntLiteral(ctx); return null; }
  protected abstract void seeExprIntLiteral(EffesParser.ExprIntLiteralContext ctx);

  @Override
  final public Void visitExprThis(EffesParser.ExprThisContext ctx) { seeExprThis(ctx); return null; }
  protected abstract void seeExprThis(EffesParser.ExprThisContext ctx);

  @Override
  final public Void visitExprNegation(EffesParser.ExprNegationContext ctx) { seeExprNegation(ctx); return null; }
  protected abstract void seeExprNegation(EffesParser.ExprNegationContext ctx);

  @Override
  final public Void visitExprStringLiteral(EffesParser.ExprStringLiteralContext ctx) { seeExprStringLiteral(ctx); return null; }
  protected abstract void seeExprStringLiteral(EffesParser.ExprStringLiteralContext ctx);

  @Override
  final public Void visitExprParenthesis(EffesParser.ExprParenthesisContext ctx) { seeExprParenthesis(ctx); return null; }
  protected abstract void seeExprParenthesis(EffesParser.ExprParenthesisContext ctx);

  @Override
  final public Void visitExprIfIs(EffesParser.ExprIfIsContext ctx) { seeExprIfIs(ctx); return null; }
  protected abstract void seeExprIfIs(EffesParser.ExprIfIsContext ctx);

  @Override
  final public Void visitQualifiedIdentName(EffesParser.QualifiedIdentNameContext ctx) { seeQualifiedIdentName(ctx); return null; }
  protected abstract void seeQualifiedIdentName(EffesParser.QualifiedIdentNameContext ctx);

  @Override
  final public Void visitQualifiedIdentNameMiddle(EffesParser.QualifiedIdentNameMiddleContext ctx) { seeQualifiedIdentNameMiddle(ctx); return null; }
  protected abstract void seeQualifiedIdentNameMiddle(EffesParser.QualifiedIdentNameMiddleContext ctx);

  @Override
  final public Void visitQualifiedIdentType(EffesParser.QualifiedIdentTypeContext ctx) { seeQualifiedIdentType(ctx); return null; }
  protected abstract void seeQualifiedIdentType(EffesParser.QualifiedIdentTypeContext ctx);

  @Override
  final public Void visitQualifiedIdentThis(EffesParser.QualifiedIdentThisContext ctx) { seeQualifiedIdentThis(ctx); return null; }
  protected abstract void seeQualifiedIdentThis(EffesParser.QualifiedIdentThisContext ctx);

  @Override
  final public Void visitBlockMatcher(EffesParser.BlockMatcherContext ctx) { seeBlockMatcher(ctx); return null; }
  protected abstract void seeBlockMatcher(EffesParser.BlockMatcherContext ctx);

  @Override
  final public Void visitBlockMatchers(EffesParser.BlockMatchersContext ctx) { seeBlockMatchers(ctx); return null; }
  protected abstract void seeBlockMatchers(EffesParser.BlockMatchersContext ctx);

  @Override
  final public Void visitExpressionMatchers(EffesParser.ExpressionMatchersContext ctx) { seeExpressionMatchers(ctx); return null; }
  protected abstract void seeExpressionMatchers(EffesParser.ExpressionMatchersContext ctx);

  @Override
  final public Void visitExpressionMatcher(EffesParser.ExpressionMatcherContext ctx) { seeExpressionMatcher(ctx); return null; }
  protected abstract void seeExpressionMatcher(EffesParser.ExpressionMatcherContext ctx);

  @Override
  public Void visitMatcherAny(EffesParser.MatcherAnyContext ctx) { seeMatcherAny(ctx); return null; }
  protected abstract void seeMatcherAny(EffesParser.MatcherAnyContext ctx);

  @Override
  final public Void visitMatcherWithPattern(EffesParser.MatcherWithPatternContext ctx) { seeMatcherWithPattern(ctx); return null; }
  protected abstract void seeMatcherWithPattern(EffesParser.MatcherWithPatternContext ctx);

  @Override
  final public Void visitMatcherJustName(EffesParser.MatcherJustNameContext ctx) { seeMatcherJustName(ctx); return null; }
  protected abstract void seeMatcherJustName(EffesParser.MatcherJustNameContext ctx);

  @Override
  final public Void visitPatternType(EffesParser.PatternTypeContext ctx) { seePatternType(ctx); return null; }
  protected abstract void seePatternType(EffesParser.PatternTypeContext ctx);

  @Override
  final public Void visitPatternRegex(EffesParser.PatternRegexContext ctx) { seePatternRegex(ctx); return null; }
  protected abstract void seePatternRegex(EffesParser.PatternRegexContext ctx);

  @Override
  final public Void visitPatternStringLiteral(EffesParser.PatternStringLiteralContext ctx) { seePatternStringLiteral(ctx); return null; }
  protected abstract void seePatternStringLiteral(EffesParser.PatternStringLiteralContext ctx);

  @Override
  public Void visit(ParseTree parseTree) {
    return null;
  }

  @Override
  public Void visitChildren(RuleNode ruleNode) {
    return null;
  }

  @Override
  public Void visitTerminal(TerminalNode terminalNode) {
    return null;
  }

  @Override
  public Void visitErrorNode(ErrorNode errorNode) {
    return null;
  }
}
