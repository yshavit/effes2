package com.yuvalshavit.effes2.compile;

import java.util.Map;

import org.yaml.snakeyaml.Yaml;

import com.yuvalshavit.effes2.parse.EffesParser;
import com.yuvalshavit.effes2.util.ResourceReader;

public class MethodCompilerTest extends CompilerTestBase<EffesParser.MethodDeclarationContext> {

  public MethodCompilerTest() {
    super(EffesParser::methodDeclaration, "methods.yaml");
  }

  @Override
  protected void compile(CompilerContext compilerContext, EffesParser.MethodDeclarationContext rule, Map<String,?> options) {
    MethodCompiler.compile(rule, compilerContext);
  }

  @Override
  protected Preload preload() {
    return new Yaml().loadAs(ResourceReader.read(getClass(), "methods.preload.yaml"), Preload.class);
  }
}
