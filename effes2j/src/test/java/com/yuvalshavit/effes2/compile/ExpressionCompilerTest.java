package com.yuvalshavit.effes2.compile;

import static org.testng.Assert.*;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

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

    Map<String,Symbol> symbols() {
      return symbols == null
        ? Collections.emptyMap()
        : symbols.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> ResourceReader.convert(e.getValue(), Symbol.class)));
    }
  }
}
