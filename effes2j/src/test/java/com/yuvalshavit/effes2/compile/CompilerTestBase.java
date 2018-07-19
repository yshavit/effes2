package com.yuvalshavit.effes2.compile;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.google.common.collect.Maps;
import com.yuvalshavit.effes2.parse.EffesParser;
import com.yuvalshavit.effes2.util.FileBound;
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
  public void compile(TestCase testCase) {
    assertNotNull(testCase.input, "no test input!");
    ParseChecker.check(testCase.toString(), testCase.input, rule, ast -> {
      StringBuilder sb = new StringBuilder();
      CompilerContextGenerator ccGen = compilerContextGenerator(testCase, sb, preload());
      CompilerContext compilerContext = compilerContext(testCase, ccGen);
      compile(ccGen, compilerContext, ast, testCase.options);
      assertEquals(sb.toString(), TUtils.trimExpectedOps(testCase.expect));
    });
  }

  protected boolean includeDebugSymbolsByDefault() {
    return false;
  }

  protected Preload preload() {
    return null;
  }

  public static class Preload {
    public Map<String,SerTypeInfo> types = Collections.emptyMap();
  }

  public static class TestCase implements FileBound {
    public String instanceContextType;
    public Boolean debugSymbols;
    public Map<String,SerTypeInfo> types = Collections.emptyMap();
    public Map<String,SerMethodInfo> staticMethods = Collections.emptyMap();
    public String input;
    public String name;
    public Map<String,TestLocalVar> localVars = Collections.emptyMap();
    public String expect;
    public Map<String,Object> options = Collections.emptyMap();

    @Override
    public String toString() {
      return name == null ? input : name;
    }

    @Override
    public void setFile(String fileName) {
      // nothing
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

  private CompilerContextGenerator compilerContextGenerator(TestCase testCase, StringBuilder out, Preload preload) {
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
    TypeInfo typeInfo = createTypeInfo(testTypes, testCase.staticMethods);
    boolean includeDebugSymbols = testCase.debugSymbols == null ? includeDebugSymbolsByDefault() : testCase.debugSymbols;
    EffesOps<Token> outOps = TUtils.opsToString(out, includeDebugSymbols);
    CompilerContext.EfctDeclarations efctDecls = CompilerContext.efctDeclarationsFor(out);
    return new CompilerContextGenerator(MODULE, outOps, efctDecls, typeInfo);
  }

  private static CompilerContext compilerContext(TestCase testCase, CompilerContextGenerator ccGen) {
    Scope scope = new Scope();
    scope.push();
    testCase.localVars.forEach((name, var) -> {
      VarRef.LocalVar varRef = new VarRef.LocalVar(var.reg, var.type == null ? null :ccGen.typeInfo.qualify(var.type));
      scope.allocateLocal(name, false, varRef);
    });
    scope.push();

    LabelAssigner labelAssigner = new LabelAssigner(ccGen.ops);
    VarRef.LocalVar instanceVar = testCase.instanceContextType == null
      ? null
      : new VarRef.LocalVar(0, new Name.QualifiedType(MODULE, new Name.UnqualifiedType(testCase.instanceContextType)));
    return new CompilerContext(scope, labelAssigner, ccGen.ops, ccGen.typeInfo, MODULE, instanceVar);
  }

  private static TypeInfo createTypeInfo(Map<Name.QualifiedType,SerTypeInfo> types, Map<String,SerMethodInfo> staticMethods) {
    Map<Name.QualifiedType,List<String>> fields = new HashMap<>();
    Map<Name.EvmScope,Map<String,MethodInfo>> methodInfo = new HashMap<>();
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
    Map<String, MethodInfo> staticMethodsInfos = staticMethods.entrySet()
      .stream()
      .map(e -> new AbstractMap.SimpleEntry<>(e.getKey(), toMethodInfo(e.getValue(), MODULE, e.getKey())))
      .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));
    methodInfo.put(MODULE, staticMethodsInfos);
    return new MockTypeInfo(fields, methodInfo);
  }

  private static MethodInfo toMethodInfo(SerMethodInfo serMethodInfo, Name.EvmScope typeName, String methodName) {
    return new Compiler.UserlandMethodInfo(
      Objects.requireNonNull(serMethodInfo.declaredArgs, "declaredArgs can't be null"),
      Objects.requireNonNull(serMethodInfo.hasRv, "hasRv can't be null"),
      typeName,
      methodName);
  }

  private static class MockTypeInfo implements TypeInfo {
    private final Map<Name.QualifiedType,List<String>> fields;
    private final Map<Name.EvmScope,Map<String,MethodInfo>> methodInfo;

    public MockTypeInfo(Map<Name.QualifiedType,List<String>> fields, Map<Name.EvmScope,Map<String,MethodInfo>> methodInfo) {
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
    public MethodInfo getMethod(Name.EvmScope type, String methodName) {
      Map<String,MethodInfo> methods = Objects.requireNonNull(methodInfo.get(type), String.format("no type named %s", type));
      return Objects.requireNonNull(methods.get(methodName), String.format("no method named %s on %s", methodName, type));
    }

    @Override
    public Name.QualifiedType qualify(Name.UnqualifiedType unqualified) {
      Set<Name.QualifiedType> possible = fields
        .keySet()
        .stream()
        .filter(k -> unqualified.equals(k.getUnqualifiedType()))
        .collect(Collectors.toCollection(HashSet::new));
      methodInfo.keySet().forEach(scope -> {
        if (scope instanceof Name.QualifiedType) {
          Name.QualifiedType qualifiedType = (Name.QualifiedType) scope;
          if (qualifiedType.getUnqualifiedType().equals(unqualified)) {
            possible.add(qualifiedType);
          }
        }
      });
      if (possible.isEmpty()) {
        throw new NoSuchElementException(unqualified.getName());
      } else if (possible.size() > 1) {
        throw new RuntimeException("multiple matches: " + possible);
      }
      return possible.iterator().next();
    }

    private List<String> fieldsFor(Name.QualifiedType type) {
      List<String> fieldsForType = fields.get(type);
      return fieldsForType == null ? Collections.emptyList() : fieldsForType;
    }
  }
}
