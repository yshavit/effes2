package com.yuvalshavit.effes2.compile;

import java.util.Map;

import com.yuvalshavit.effes2.parse.EffesParser;

// Not really a unit test per se; more like a small compile-and-run test
public class MatcherCompilerTest extends CompilerTestBase<EffesParser.MatcherContext> {

  private static final String LABEL_IF_MATCHED = "@MATCHED";
  private static final String LABEL_IF_NOT_MATCHED = "@UNMATCHED";

  public MatcherCompilerTest() {
    super(EffesParser::matcher, "matchers.yaml");
  }

  @Override
  protected void compile(CompilerContextGenerator ccGen, CompilerContext compilerContext, EffesParser.MatcherContext rule, Map<String,?> options) {
    boolean keepIfNotMatched = (Boolean) options.get("keepIfNotMatched");
    MatcherCompiler.compile(rule, LABEL_IF_MATCHED, LABEL_IF_NOT_MATCHED, keepIfNotMatched, compilerContext, null);
  }
}
