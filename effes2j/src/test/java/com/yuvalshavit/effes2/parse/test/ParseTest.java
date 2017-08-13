package com.yuvalshavit.effes2.parse.test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.io.IOException;
import java.util.function.Function;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.Tree;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.yuvalshavit.effes2.parse.EffesParser;
import com.yuvalshavit.effes2.parse.ParseUtils;
import com.yuvalshavit.effes2.parse.ToObjectPrinter;
import com.yuvalshavit.effes2.util.ResourceReader;

public class ParseTest {
  public static final String PARSE_TESTS = "test1";

  @Test
  public void dataProviderWorks() throws IOException {
    assertNotNull(readParseFiles());
  }

  @DataProvider(name = PARSE_TESTS)
  public static Object[][] readParseFiles() throws IOException {
    return ResourceReader.testCases(ParseTest.class, TestCase.class);
  }

  @Test(dataProvider = PARSE_TESTS)
  public void parse(String fileName, TestCase testCase) throws Exception {
    Function<EffesParser,ParserRuleContext> rule = ParseUtils.ruleByName(fileName);
    StringBuilder errsSb = new StringBuilder();
    Tree ast = ParseUtils.parse(
      testCase.input,
      rule,
      ((line, lineOffset, msg) -> errsSb.append(String.format("%s error at <%s> %d:%d: %s%n", getClass().getSimpleName(), testCase, line, lineOffset, msg))));
    ToObjectPrinter toObjectPrinter = new ToObjectPrinter();
    toObjectPrinter.walk(ast);
    Object result = toObjectPrinter.get();

    String errMessages = errsSb.toString();
    if (!errMessages.isEmpty()) {
      System.err.println(errMessages); // we'll assertEquals on them later, but for now print them in case the prettyPrint fails first.
    }
    assertEquals(ParseUtils.prettyPrint(result), ParseUtils.prettyPrint(testCase.expected));
    assertEquals(errMessages, "", "error messages");
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
