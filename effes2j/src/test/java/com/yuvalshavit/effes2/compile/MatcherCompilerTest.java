package com.yuvalshavit.effes2.compile;

import static org.testng.Assert.*;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.yuvalshavit.effes2.parse.EffesParser;
import com.yuvalshavit.effes2.util.ParseChecker;
import com.yuvalshavit.effes2.util.ResourceReader;
import com.yuvalshavit.effesvm.runtime.EffesOps;

// Not really a unit test per se; more like a small compile-and-run test
public class MatcherCompilerTest {
  private static final String LABEL_IF_MATCHED_ = "@MATCHED";
  private static final String LABEL_IF_NOT_MATCHED_ = "@UNMATCHED";

  @Test
  public void dataProviderWorks() throws IOException {
    assertNotNull(readParseFiles());
  }

  @DataProvider(name = "test")
  public static Object[][] readParseFiles() throws IOException {
    return ResourceReader.testCases(MatcherCompilerTest.class, TestCase.class, "matchers.yaml");
  }

  @Test(dataProvider = "test")
  public void run(String testName, TestCase testCase) {
    ParseChecker.check(testCase.name, testCase.match, EffesParser::matcher, matcherContext -> {
      StringBuilder sb = new StringBuilder();
      EffesOps<Void> ops = TUtils.opsToString(sb);
      Scope scope = new Scope();
      scope.push();
      for (String variable : testCase.variables) {
        scope.allocateLocal(variable, false);
      }
      LabelAssigner labelAssigner = new LabelAssigner(ops);
      scope.push();
      MatcherCompiler.compile(matcherContext, testCase.fieldLookup(), testCase.labelIfMatched, testCase.labelIfNotMatched, testCase.keepIfNotMatched, scope, labelAssigner, ops);
      assertEquals(sb.toString(), testCase.trimmedExpected(), testName);
    });
  }

  public static class TestCase {
    public String name;
    public String labelIfMatched = LABEL_IF_MATCHED_;
    public String labelIfNotMatched = LABEL_IF_NOT_MATCHED_;
    public List<String> variables = Collections.emptyList();
    public Map<String,List<String>> typeFields = Collections.emptyMap();
    public boolean keepIfNotMatched;
    public String match;
    public String expect;

    @Override
    public String toString() {
      return name;
    }

    FieldLookup fieldLookup() {
      return (type, index) -> {
        List<String> fields = typeFields.get(type);
        if (fields == null) {
          throw new RuntimeException("test error: undefined type " + type);
        }
        try {
          return fields.get(index);
        } catch (IndexOutOfBoundsException e) {
          throw new RuntimeException("test error: bad field access for type " + type, e);
        }
      };
    }

    private String trimmedExpected() {
      return expect
        .replaceAll(" *#.*\n", "\n")
        .replaceAll("^\n*", ""); // remove blank lines
    }
  }
}
