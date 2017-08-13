package com.yuvalshavit.effes2.parse.test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Function;

import org.antlr.v4.runtime.ParserRuleContext;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.yuvalshavit.effes2.parse.EffesParser;
import com.yuvalshavit.effes2.parse.ParseUtils;
import com.yuvalshavit.effes2.parse.ToObjectPrinter;
import com.yuvalshavit.effes2.util.ParseChecker;
import com.yuvalshavit.effes2.util.ResourceReader;

public class ParseTest {

  @Test
  public void dataProviderWorks() throws IOException {
    assertNotNull(readParseFiles());
  }

  @DataProvider(name = "test1")
  public static Object[][] readParseFiles() throws IOException {
    return ResourceReader.testCases(ParseTest.class, TestCase.class);
  }

  @Test(dataProvider = "test1")
  public void parse(String fileName, TestCase testCase) throws Exception {
    Function<EffesParser,ParserRuleContext> rule = ParseUtils.ruleByName(fileName);
    ParseChecker.check(getClass().getSimpleName() + "." + testCase, testCase.input, rule, ast -> {
      ToObjectPrinter toObjectPrinter = new ToObjectPrinter();
      toObjectPrinter.walk(ast);
      Object result = toObjectPrinter.get();
      assertEquals(ParseUtils.prettyPrint(result), ParseUtils.prettyPrint(testCase.expected));
    });
  }

  static class TestCase {
    public String name;
    public String input;
    public Object expected;

    @Override
    public String toString() {
      return name == null ? input : name;
    }
  }
}
