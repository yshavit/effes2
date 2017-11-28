package com.yuvalshavit.effes2.compile;

import java.util.Map;

import com.yuvalshavit.effes2.parse.EffesParser;

public class BlockTest extends CompilerTestBase<EffesParser.BlockContext> {
  public BlockTest() {
    super(EffesParser::block, "blocks.yaml");
  }

  @Override
  protected void compile(CompilerContextGenerator ccGen, CompilerContext compilerContext, EffesParser.BlockContext rule, Map<String,?> options) {
    new StatementCompiler(compilerContext).compileBlock(rule);
  }
}
