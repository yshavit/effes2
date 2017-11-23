package com.yuvalshavit.effes2.compile;

import java.util.Map;

import com.yuvalshavit.effes2.parse.EffesParser;

public class StatementCompilerTest extends CompilerTestBase<EffesParser.StatementContext> {

  public StatementCompilerTest() {
    super(EffesParser::statement, "statements.yaml");
  }

  @Override
  protected void compile(CompilerContext compilerContext, EffesParser.StatementContext rule, Map<String,?> options) {
    new StatementCompiler(compilerContext).apply(rule);
  }
}
