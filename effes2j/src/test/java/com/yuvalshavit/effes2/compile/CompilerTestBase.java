package com.yuvalshavit.effes2.compile;

import static org.testng.Assert.assertEquals;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.antlr.v4.runtime.ParserRuleContext;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.google.common.collect.Maps;
import com.yuvalshavit.effes2.parse.EffesParser;
import com.yuvalshavit.effes2.util.ParseChecker;
import com.yuvalshavit.effes2.util.ResourceReader;
import com.yuvalshavit.effesvm.runtime.EffesOps;

public abstract class CompilerTestBase<T extends ParserRuleContext> {

  private final String yamlFile;
  private final Function<EffesParser,T> rule;

  protected abstract void compile(CompilerContext compilerContext, T rule, Map<String,?> options);

  public CompilerTestBase(Function<EffesParser,T> rule, String yamlFile) {
    this.rule = rule;
    this.yamlFile = yamlFile;
  }

  @Test
  public void canReadYaml() throws IOException {
    readParseFiles();
  }

  @DataProvider(name = "test")
  public Object[][] readParseFiles() throws IOException {
    return ResourceReader.testCases(getClass(), TestCase.class, yamlFile);
  }

  @Test(dataProvider = "test")
  public void compile(String fileName, TestCase testCase) throws Exception {
    ParseChecker.check(fileName, testCase.input, rule, ast -> {
      StringBuilder sb = new StringBuilder();
      CompilerContext compilerContext = compilerContext(testCase, sb);
      compile(compilerContext, ast, testCase.options);
      assertEquals(sb.toString(), TUtils.trimExpectedOps(testCase.expect));
    });
  }

  public static class TestCase {
    public String instanceContextType;
    public Map<String,SerTypeInfo> types = Collections.emptyMap();
    public String input;
    public String name;
    public Map<String,TestLocalVar> localVars = Collections.emptyMap();
    public String expect;
    public Map<String,Object> options = Collections.emptyMap();

    @Override
    public String toString() {
      return name == null ? input : name;
    }
  }

  public static class TestLocalVar {
    public int reg;
    public String type;
  }

  public static class SerTypeInfo {
    public Map<String,SerMethodInfo> methods;
    public List<String> fields;
  }

  public static class SerMethodInfo {
    public Integer declaredArgs;
    public Boolean hasRv;
  }

  private static CompilerContext compilerContext(TestCase testCase, StringBuilder out) {
    Scope scope = new Scope();
    scope.push();
    testCase.localVars.forEach((name, var) -> {
      VarRef.LocalVar varRef = new VarRef.LocalVar(var.reg, var.type);
      scope.allocateLocal(name, false, varRef);
    });
    scope.push();

    EffesOps<Void> outOps = TUtils.opsToString(out);
    LabelAssigner labelAssigner = new LabelAssigner(outOps);
    TypeInfo typeInfo = createTypeInfo(testCase.types);
    VarRef.LocalVar instanceVar = testCase.instanceContextType == null
      ? null
      : new VarRef.LocalVar(0, testCase.instanceContextType);
    return new CompilerContext(scope, labelAssigner, outOps, typeInfo, instanceVar);
  }

  private static TypeInfo createTypeInfo(Map<String,SerTypeInfo> types) {
    Map<String,List<String>> fields = new HashMap<>();
    Map<String,Map<String,MethodInfo>> methodInfo = new HashMap<>();
    types.forEach((typeName, typeInfoSer) -> {
      if (typeInfoSer.fields != null) {
        fields.put(typeName, typeInfoSer.fields);
      }
      if (typeInfoSer.methods != null) {
        HashMap<String,MethodInfo> methods = new HashMap<>(Maps.transformValues(typeInfoSer.methods, CompilerTestBase::toMethodInfo));
        methodInfo.put(typeName, methods);
      }
    });
    return new MockTypeInfo(fields, methodInfo);
  }

  private static MethodInfo toMethodInfo(SerMethodInfo serMethodInfo) {
    return new MethodInfo(
      Objects.requireNonNull(serMethodInfo.declaredArgs, "declaredArgs can't be null"),
      Objects.requireNonNull(serMethodInfo.hasRv, "hasRv can't be null"));
  }

  private static class MockTypeInfo implements TypeInfo {
    private final Map<String,List<String>> fields;
    private final Map<String,Map<String,MethodInfo>> methodInfo;

    public MockTypeInfo(Map<String,List<String>> fields, Map<String,Map<String,MethodInfo>> methodInfo) {
      this.fields = fields;
      this.methodInfo = methodInfo;
    }

    @Override
    public boolean hasField(String type, String fieldName) {
      return fieldsFor(type).indexOf(fieldName) >= 0;
    }

    @Override
    public String fieldName(String type, int fieldIndex) {
      return fieldsFor(type).get(fieldIndex);
    }

    @Override
    public MethodInfo getMethod(String type, String methodName) {
      Map<String,MethodInfo> methods = Objects.requireNonNull(methodInfo.get(type), "no type named " + type);
      return Objects.requireNonNull(methods.get(methodName), "no method named " + methodName);
    }

    private List<String> fieldsFor(String type) {
      return Objects.requireNonNull(fields.get(type), "no fields defined for " + type);
    }
  }
}
