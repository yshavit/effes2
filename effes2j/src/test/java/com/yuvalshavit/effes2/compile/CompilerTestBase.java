package com.yuvalshavit.effes2.compile;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.ParserRuleContext;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.google.common.collect.Maps;
import com.yuvalshavit.effes2.parse.EffesParser;
import com.yuvalshavit.effes2.util.ParseChecker;
import com.yuvalshavit.effes2.util.ResourceReader;
import com.yuvalshavit.effesvm.runtime.EffesOps;

public abstract class CompilerTestBase<T extends ParserRuleContext> {

  private static final Name.Module MODULE = new Name.Module("TestModule");
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
    public Name.UnqualifiedType type;
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
    Map<Name.QualifiedType,SerTypeInfo> testTypes = new HashMap<>(testCase.types.size());
    testCase.types.forEach((k, v) -> testTypes.put(new Name.QualifiedType(MODULE, new Name.UnqualifiedType(k)), v));
    if (preload != null) {
      preload.types.forEach((k, v) -> {
        Name.QualifiedType qualifiedPreloadType = new Name.QualifiedType(MODULE, new Name.UnqualifiedType(k));
        if (testTypes.containsKey(qualifiedPreloadType)) {
          throw new RuntimeException("duplicate type: " + qualifiedPreloadType);
        }
        testTypes.put(qualifiedPreloadType, v);
      });
    }
    TypeInfo typeInfo = createTypeInfo(testTypes);
    EffesOps<Void> outOps = TUtils.opsToString(out);
    CompilerContext.EfctDeclarations efctDecls = CompilerContext.efctDeclarationsFor(out);
    return new CompilerContextGenerator(MODULE, outOps, efctDecls, typeInfo);
  }

  private static CompilerContext compilerContext(TestCase testCase, CompilerContextGenerator ccGen) {
    Scope scope = new Scope();
    scope.push();
    testCase.localVars.forEach((name, var) -> {
      VarRef.LocalVar varRef = new VarRef.LocalVar(var.reg, new Name.QualifiedType(Name.Module.BUILT_IN, var.type));
      scope.allocateLocal(name, false, varRef);
    });
    scope.push();

    LabelAssigner labelAssigner = new LabelAssigner(ccGen.ops);
    VarRef.LocalVar instanceVar = testCase.instanceContextType == null
      ? null
      : new VarRef.LocalVar(0, new Name.QualifiedType(MODULE, new Name.UnqualifiedType(testCase.instanceContextType)));
    return new CompilerContext(scope, labelAssigner, ccGen.ops, ccGen.typeInfo, MODULE, instanceVar);
  }

  private static TypeInfo createTypeInfo(Map<Name.QualifiedType,SerTypeInfo> types) {
    Map<Name.QualifiedType,List<String>> fields = new HashMap<>();
    Map<Name.QualifiedType,Map<String,MethodInfo>> methodInfo = new HashMap<>();
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

  private static MethodInfo toMethodInfo(SerMethodInfo serMethodInfo, Name.QualifiedType typeName, String methodName) {
    return new Compiler.UserlandMethodInfo(
      Objects.requireNonNull(serMethodInfo.declaredArgs, "declaredArgs can't be null"),
      Objects.requireNonNull(serMethodInfo.hasRv, "hasRv can't be null"),
      typeName,
      methodName);
  }

  private static class MockTypeInfo implements TypeInfo {
    private final Map<Name.QualifiedType,List<String>> fields;
    private final Map<Name.QualifiedType,Map<String,MethodInfo>> methodInfo;

    public MockTypeInfo(Map<Name.QualifiedType,List<String>> fields, Map<Name.QualifiedType,Map<String,MethodInfo>> methodInfo) {
      this.fields = fields;
      this.methodInfo = methodInfo;
    }

    @Override
    public int fieldsCount(Name.QualifiedType type) {
      return fieldsFor(type).size();
    }

    @Override
    public boolean hasField(Name.QualifiedType type, String fieldName) {
      return fieldsFor(type).indexOf(fieldName) >= 0;
    }

    @Override
    public String fieldName(Name.QualifiedType type, int fieldIndex) {
      return fieldsFor(type).get(fieldIndex);
    }

    @Override
    public MethodInfo getMethod(Name.QualifiedType type, String methodName) {
      Map<String,MethodInfo> methods = Objects.requireNonNull(methodInfo.get(type), String.format("no type named %s", type));
      return Objects.requireNonNull(methods.get(methodName), String.format("no method named %s on %s", methodName, type));
    }

    @Override
    public Name.QualifiedType qualify(Name.UnqualifiedType unqualified) {
      List<Name.QualifiedType> possible = fields.keySet().stream().filter(k -> unqualified.equals(k.getUnqualifiedType())).collect(Collectors.toList());
      if (possible.isEmpty()) {
        throw new NoSuchElementException(unqualified.getName());
      } else if (possible.size() > 1) {
        throw new RuntimeException("multiple matches: " + possible);
      }
      return possible.get(0);
    }

    private List<String> fieldsFor(Name.QualifiedType type) {
      List<String> fieldsForType = fields.get(type);
      return fieldsForType == null ? Collections.emptyList() : fieldsForType;
    }
  }
}
