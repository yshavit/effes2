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
import com.yuvalshavit.effesvm.runtime.EffesOps;

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
      EffesOps<Void> out = TUtils.opsToString(sb);
      FieldLookup fieldLookup = (type, index) -> {
        if (type.equals("One") && index == 0) {
          return "value";
        } else {
          throw new UnsupportedOperationException(String.format("unsupported field lookup on %s[%d]", type, index));
        }
      };
      LabelAssigner labelAssigner = new LabelAssigner(out);
      ExpressionCompiler compiler = new ExpressionCompiler(testCase.symbols(), fieldLookup, labelAssigner, out);
      compiler.apply(expr);
      assertEquals(sb.toString(), TUtils.trimExpectedOps(testCase.expect));
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
      scope.push();
      if (symbols != null) {
        for (Map.Entry<String,Map<?,?>> entry : symbols.entrySet()) {
          Map<?,?> symbolAsMap = entry.getValue();
          int reg = (Integer) symbolAsMap.get("reg");
          String type = (String) symbolAsMap.get("type");
          VarRef varRef = new VarRef.LocalVar(reg, type);
          scope.allocateLocal(entry.getKey(), false, varRef);
        }
      }
      return scope;
    }
  }
}
