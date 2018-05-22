package com.yuvalshavit.effes2.compile;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

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

  private static final String MODULE_NAME = "TestModule";
  private final String yamlFile;
  private final Function<EffesParser,T> rule;

  protected abstract void compile(CompilerContextGenerator ccGen, CompilerContext compilerContext, T rule, Map<String,?> options);

  public CompilerTestBase(Function<EffesParser,T> rule, String yamlFile) {
    this.rule = rule;
    this.yamlFile = yamlFile;
  }

  @Test
  public void canReadYaml() {
    readParseFiles();
  }

  @DataProvider(name = "test")
  public Object[][] readParseFiles() {
    return ResourceReader.testCases(getClass(), TestCase.class, yamlFile);
  }

  @Test(dataProvider = "test")
  public void compile(String fileName, TestCase testCase) {
    assertNotNull(testCase.input, "no test input!");
    ParseChecker.check(fileName, testCase.input, rule, ast -> {
      StringBuilder sb = new StringBuilder();
      CompilerContextGenerator ccGen = compilerContextGenerator(testCase, sb, preload());
      CompilerContext compilerContext = compilerContext(testCase, ccGen);
      compile(ccGen, compilerContext, ast, testCase.options);
      assertEquals(sb.toString(), TUtils.trimExpectedOps(testCase.expect));
    });
  }

  protected Preload preload() {
    return null;
  }

  public static class Preload {
    public Map<String,SerTypeInfo> types = Collections.emptyMap();
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

  private static CompilerContextGenerator compilerContextGenerator(TestCase testCase, StringBuilder out, Preload preload) {
    Map<String,SerTypeInfo> testTypes = new HashMap<>(testCase.types.size());
    testCase.types.forEach((k, v) -> testTypes.put(":" + k, v));
    if (preload != null) {
      testTypes.keySet().stream().filter(preload.types::containsKey).findAny().ifPresent(dupe -> {
        throw new RuntimeException("duplicate type: " + dupe);
      });
      testTypes.putAll(preload.types);
    }
    TypeInfo typeInfo = createTypeInfo(testTypes);
    EffesOps<Void> outOps = TUtils.opsToString(out);
    CompilerContext.EfctDeclarations efctDecls = CompilerContext.efctDeclarationsFor(out);
    return new CompilerContextGenerator(MODULE_NAME, outOps, efctDecls, typeInfo);
  }

  private static CompilerContext compilerContext(TestCase testCase, CompilerContextGenerator ccGen) {
    Scope scope = new Scope();
    scope.push();
    testCase.localVars.forEach((name, var) -> {
      VarRef.LocalVar varRef = new VarRef.LocalVar(var.reg, var.type);
      scope.allocateLocal(name, false, varRef);
    });
    scope.push();

    LabelAssigner labelAssigner = new LabelAssigner(ccGen.ops);
    VarRef.LocalVar instanceVar = testCase.instanceContextType == null
      ? null
      : new VarRef.LocalVar(0, testCase.instanceContextType);
    return new CompilerContext(scope, labelAssigner, ccGen.ops, ccGen.typeInfo, MODULE_NAME, instanceVar);
  }

  private static TypeInfo createTypeInfo(Map<String,SerTypeInfo> types) {
    Map<String,List<String>> fields = new HashMap<>();
    Map<String,Map<String,MethodInfo>> methodInfo = new HashMap<>();
    types.forEach((typeName, typeInfoSer) -> {
      if (typeInfoSer.fields != null) {
        fields.put(typeName, typeInfoSer.fields);
      }
      if (typeInfoSer.methods != null) {
        Map<String,MethodInfo> methods = Maps.transformEntries(typeInfoSer.methods,
          (methodName, serMethodInfo) -> serMethodInfo == null ? null : toMethodInfo(serMethodInfo, typeName, methodName));
        methodInfo.put(typeName, methods);
      }
    });
    return new MockTypeInfo(fields, methodInfo);
  }

  private static MethodInfo toMethodInfo(SerMethodInfo serMethodInfo, String typeName, String methodName) {
    return new Compiler.UserlandMethodInfo(
      Objects.requireNonNull(serMethodInfo.declaredArgs, "declaredArgs can't be null"),
      Objects.requireNonNull(serMethodInfo.hasRv, "hasRv can't be null"),
      typeName,
      methodName);
  }

  private static class MockTypeInfo implements TypeInfo {
    private final Map<String,List<String>> fields;
    private final Map<String,Map<String,MethodInfo>> methodInfo;

    public MockTypeInfo(Map<String,List<String>> fields, Map<String,Map<String,MethodInfo>> methodInfo) {
      this.fields = fields;
      this.methodInfo = methodInfo;
    }

    @Override
    public int fieldsCount(String type) {
      return fieldsFor(type).size();
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
      Map<String,MethodInfo> methods = Objects.requireNonNull(methodInfo.get(type), String.format("no type named %s", type));
      return Objects.requireNonNull(methods.get(methodName), String.format("no method named %s on %s", methodName, type));
    }

    @Override
    public String getModule(String typeName) {
      return MODULE_NAME;
    }

    private List<String> fieldsFor(String type) {
      List<String> fieldsForType = fields.get(type);
      return fieldsForType == null ? Collections.emptyList() : fieldsForType;
    }
  }
}
