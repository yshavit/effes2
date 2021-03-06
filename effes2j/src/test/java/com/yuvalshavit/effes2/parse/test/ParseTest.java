package com.yuvalshavit.effes2.parse.test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.io.IOException;
import java.util.function.Function;

import org.antlr.v4.runtime.ParserRuleContext;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.yuvalshavit.effes2.parse.EffesParser;
import com.yuvalshavit.effes2.parse.ParseUtils;
import com.yuvalshavit.effes2.parse.ToObjectPrinter;
import com.yuvalshavit.effes2.util.FileBound;
import com.yuvalshavit.effes2.util.ParseChecker;
import com.yuvalshavit.effes2.util.ResourceReader;

public class ParseTest {

  @Test
  public void dataProviderWorks() throws IOException {
    assertNotNull(readParseFiles());
  }

  @DataProvider(name = "test1")
  public static Object[][] readParseFiles() throws IOException {
    String[] files = ResourceReader.read(ParseTest.class, ".").split("\n");
    return ResourceReader.testCases(ParseTest.class, TestCase.class, files);
  }

  @Test(dataProvider = "test1")
  public void parse(TestCase testCase) throws Exception {
    Function<EffesParser,ParserRuleContext> rule = ParseUtils.ruleByName(testCase.fileName);
    ParseChecker.check(getClass().getSimpleName() + "." + testCase, testCase.input, rule, ast -> {
      ToObjectPrinter toObjectPrinter = new ToObjectPrinter();
      toObjectPrinter.walk(ast);
      Object result = toObjectPrinter.get();
      assertEquals(ParseUtils.prettyPrint(result), ParseUtils.prettyPrint(testCase.expected));
    });
  }

  static class TestCase implements FileBound {
    public String name;
    public String input;
    public Object expected;
    private String fileName;

    @Override
    public String toString() {
      return name == null ? input : name;
    }

    @Override
    public void setFile(String fileName) {
      this.fileName = fileName;
    }
  }
}
