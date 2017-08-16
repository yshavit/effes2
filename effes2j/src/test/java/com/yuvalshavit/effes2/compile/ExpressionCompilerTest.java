package com.yuvalshavit.effes2.compile;

import static org.testng.Assert.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.yuvalshavit.effes2.parse.EffesParser;
import com.yuvalshavit.effes2.util.ParseChecker;
import com.yuvalshavit.effes2.util.ResourceReader;

public class ExpressionCompilerTest {

  @Test
  public void dataProviderWorks() throws IOException {
    assertNotNull(readParseFiles());
  }

  @DataProvider(name = "test")
  public static Object[][] readParseFiles() throws IOException {
    return ResourceReader.testCases(ExpressionCompilerTest.class, TestCase.class, "expressions.yaml");
  }

  @Test(dataProvider = "test")
  public void compile(String fileName, TestCase testCase) throws Exception {
    ParseChecker.check(fileName, testCase.input, EffesParser::expression, expr -> {
      StringBuilder sb = new StringBuilder();
      ExpressionCompiler compiler = new ExpressionCompiler(testCase.symbols(), op -> sb.append(op).append('\n'));
      compiler.apply(expr);
      assertEquals(sb.toString(), testCase.expect);
    });
  }

  public static class TestCase {
    public String input;
    public String name;
    public HashMap<String,Map<?,?>> symbols;
    public String expect;

    @Override
    public String toString() {
      return name == null ? input : name;
    }

    Scope symbols() {
      Scope scope = new Scope();
      if (symbols != null) {
        for (Map.Entry<String,Map<?,?>> entry : symbols.entrySet()) {
          Symbol symbol = ResourceReader.convert(entry.getValue(), Symbol.class);
          scope.allocate(entry.getKey(), symbol);
        }
      }
      return scope;
    }
  }
}
