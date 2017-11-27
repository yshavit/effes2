package com.yuvalshavit.effes2.compile;

import java.util.Map;

import com.yuvalshavit.effes2.parse.EffesParser;

public class ExpressionCompilerTest extends CompilerTestBase<EffesParser.ExpressionContext> {

  public ExpressionCompilerTest() {
    super(EffesParser::expression, "expressions.yaml");
  }

  @Override
  protected void compile(CompilerContextGenerator ccGen, CompilerContext compilerContext, EffesParser.ExpressionContext rule, Map<String,?> options) {
    new ExpressionCompiler(compilerContext).apply(rule);
  }
}
