package com.yuvalshavit.effes2.compile;

import java.io.IOException;
import java.io.Writer;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.Token;

import com.google.common.annotations.VisibleForTesting;
import com.yuvalshavit.effes2.parse.EffesParser;
import com.yuvalshavit.effes2.parse.ParseUtils;
import com.yuvalshavit.effes2.util.MiscUtil;
import com.yuvalshavit.effesvm.runtime.EffesOps;

public class Compiler {

  private Compiler() { }

  public static void compile(Collection<? extends CompileUnit> compileUnits, Function<String,Writer> writers, Consumer<String> errors) {
    // first, parse
    Map<String,EffesParser.FileContext> parsed = parse(compileUnits, errors);
    TypeInfo typeInfo = scanForTypes(errors, parsed);
    parsed.forEach((moduleName, fileContext) -> {
      try (Writer writer = writers.apply(moduleName)) {
        EffesOps<Token> ops = Op.factory(op -> {
          try {
            writer.append(op.toString());
            writer.append('\n');
          } catch (IOException e) {
            errors.accept(MiscUtil.toStringWithStackTrace(e));
          }
        });
        CompilerContext.EfctDeclarations declarations = CompilerContext.efctDeclarationsFor(writer);
        CompilerContextGenerator ccg = new CompilerContextGenerator(new Name.Module(moduleName), ops, declarations, typeInfo);
        DeclarationCompiler compiler = new DeclarationCompiler(ccg);
        fileContext.declaration().forEach(compiler::apply);
      } catch (IOException e) {
        errors.accept(MiscUtil.toStringWithStackTrace(e));
      }
    });
  }

  public static Map<String,EffesParser.FileContext> parse(Collection<? extends CompileUnit> compileUnits, Consumer<String> errors) {
    Map<String,EffesParser.FileContext> parsed = new HashMap<>(compileUnits.size());
    compileUnits.forEach(unit -> {
      if (parsed.containsKey(unit.moduleName)) {
        errors.accept(String.format("Duplicate module named \"%s\"", unit.moduleName));
        return;
      }
      String code = unit.reader.get();
      EffesParser.FileContext file = ParseUtils.parse(
        code,
        EffesParser::file,
        (line, lineOffset, msg) -> errors.accept(String.format("error at <%s> %d:%d: %s%n", unit.sourceDescription, line, lineOffset, msg)));
      if (file.stop.getStopIndex() != code.length() - 1) {
        throw new CompilationException(file.stop, file.stop, "Expected EOF but found extra input");
      }
      parsed.put(unit.moduleName, file);
    });
    return parsed;
  }

  public static TypeInfo scanForTypes(Consumer<String> errors, Map<String,EffesParser.FileContext> parsed) {
    Map<Name.QualifiedType,SingleTypeInfo> typeInfos = new HashMap<>();
    Map<Name.EvmScope,Map<String, ? extends MethodInfo>> methodInfosByScope = new HashMap<>();

    parsed.entrySet().stream()
      .map(entry -> new AbstractMap.SimpleImmutableEntry<>(new Name.Module(entry.getKey()), entry.getValue().declaration()))
      .flatMap(entry -> entry
        .getValue().stream()
        .map(EffesParser.DeclarationContext::typeDeclaration).filter(Objects::nonNull)
        .map(typeDeclaration -> new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), typeDeclaration)))
      .forEach(entry -> {
        Name.Module moduleName = entry.getKey();
        EffesParser.TypeDeclarationContext typeDeclaration = entry.getValue();
        Name.QualifiedType qualifiedTypeName = new Name.QualifiedType(moduleName, new Name.UnqualifiedType(typeDeclaration.IDENT_TYPE().getSymbol().getText()));
        if (typeInfos.containsKey(qualifiedTypeName)) {
          errors.accept("Duplicate type name: " + qualifiedTypeName);
          return;
        }
        Map<String,MethodInfo> methods;
        List<EffesParser.MethodDeclarationContext> methodDeclarations = typeDeclaration.methodDeclaration();
        if (methodDeclarations == null) {
          methods = Collections.emptyMap();
        } else {
          methods = new HashMap<>(methodDeclarations.size());
          for (EffesParser.MethodDeclarationContext methodDeclaration : methodDeclarations) {
            UserlandMethodInfo methodInfo = createMethodInfo(qualifiedTypeName, methodDeclaration);
            if (methods.containsKey(methodInfo.methodName)) {
              errors.accept("duplicate method " + methodInfo.methodName + " in " + qualifiedTypeName);
              continue;
            }
            methods.put(methodInfo.methodName, methodInfo);
          }
        }
        SingleTypeInfo typeInfo = new SingleTypeInfo(moduleName, DeclarationCompiler.getArgNames(typeDeclaration));
        typeInfos.put(qualifiedTypeName, typeInfo);
        methodInfosByScope.put(qualifiedTypeName, methods);
      });
    for (Map.Entry<String, EffesParser.FileContext> entry : parsed.entrySet()) {
      Name.Module module = new Name.Module(entry.getKey());
      Map<String, UserlandMethodInfo> methodInfos = entry.getValue()
        .declaration()
        .stream()
        .map(EffesParser.DeclarationContext::methodDeclaration)
        .filter(Objects::nonNull)
        .map(methodDecl -> createMethodInfo(module, methodDecl))
        .collect(Collectors.toMap(method -> method.methodName, Function.identity()));
      methodInfosByScope.put(module, methodInfos);
    }
    // TODO refactor this so that the CompilerTestBase mocks can use it. Otherwise, builtin functions and type instantiation doesn't work
    for (EffesBuiltinType builtinType : EffesBuiltinType.values()) {
      SingleTypeInfo typeInfo = new SingleTypeInfo(Name.Module.BUILT_IN, Collections.emptyList());
      typeInfos.put(Name.QualifiedType.forBuiltin(builtinType), typeInfo);
      methodInfosByScope.put(Name.QualifiedType.forBuiltin(builtinType), builtinType.instanceMethods());
      methodInfosByScope.put(new Name.Module(builtinType.typeName()), builtinType.staticMethods());
    }
    return new TypeInfoImpl(typeInfos, methodInfosByScope);
  }

  private static UserlandMethodInfo createMethodInfo(Name.EvmScope scope, EffesParser.MethodDeclarationContext methodDeclaration) {
    String methodName = methodDeclaration.IDENT_NAME().getSymbol().getText();
    int nArgs = methodDeclaration.argsDeclaration().IDENT_NAME() == null
      ? 0
      : methodDeclaration.argsDeclaration().IDENT_NAME().size();
    return new UserlandMethodInfo(nArgs, methodDeclaration.ARROW() != null, scope, methodName);
  }

  public static class CompileUnit {
    public final String moduleName;
    public final String sourceDescription;
    public final Supplier<String> reader;

    public CompileUnit(String moduleName, String sourceDescription, Supplier<String> reader) {
      this.moduleName = moduleName;
      this.sourceDescription = sourceDescription;
      this.reader = reader;
    }
  }

  private static class SingleTypeInfo {
    final Name.Module module;
    final List<String> fields;

    public SingleTypeInfo(Name.Module module, List<String> fields) {
      this.module = module;
      this.fields = fields;
    }
  }

  @VisibleForTesting
  static class UserlandMethodInfo extends MethodInfo {
    private final Name.EvmScope targetType;
    private final String methodName;

    public UserlandMethodInfo(int nDeclaredArgs, boolean hasReturnValue, Name.EvmScope targetScope, String methodName) {
      super(nDeclaredArgs, hasReturnValue);
      this.targetType = targetScope;
      this.methodName = methodName;
    }

    @Override
    public void invoke(Token token, CompilerContext cc) {
      cc.out.call(token, targetType.evmDescriptor(cc.module), methodName);
    }
  }

  private static class TypeInfoImpl implements TypeInfo {
    private final Map<Name.QualifiedType,SingleTypeInfo> typeInfos;
    private final Map<Name.UnqualifiedType,Name.QualifiedType> globalNameLookup;
    private final Map<Name.EvmScope, Map<String, ? extends MethodInfo>> methodInfos;

    public TypeInfoImpl(Map<Name.QualifiedType, SingleTypeInfo> typeInfos, Map<Name.EvmScope, Map<String, ? extends MethodInfo>> methodInfos) {
      this.typeInfos = typeInfos;
      this.methodInfos = methodInfos;
      globalNameLookup = new HashMap<>(typeInfos.size());
      for (Name.QualifiedType qualifiedType : typeInfos.keySet()) {
        Name.UnqualifiedType unqualifiedType = qualifiedType.getUnqualifiedType();
        if (globalNameLookup.containsKey(unqualifiedType)) {
          throw new RuntimeException("duplicate unqualified types: " + qualifiedType + " would override " + globalNameLookup.get(unqualifiedType));
        }
        globalNameLookup.put(unqualifiedType, qualifiedType);
      }
    }

    @Override
    public int fieldsCount(Name.QualifiedType type) {
      SingleTypeInfo info = typeInfos.get(type);
      return info == null
        ? -1
        : info.fields.size();
    }

    @Override
    public boolean hasField(Name.QualifiedType type, String fieldName) {
      SingleTypeInfo info = typeInfos.get(type);
      return info != null && info.fields.contains(fieldName);
    }

    @Override
    public String fieldName(Name.QualifiedType type, int fieldIndex) {
      return typeInfos.get(type).fields.get(fieldIndex); // TODO need better errors
    }

    @Override
    public MethodInfo getMethod(Name.EvmScope scope, String methodName) {
      Map<String, ? extends MethodInfo> methods = methodInfos.get(scope);
      return methods == null ? null : methods.get(methodName);
    }

    @Override
    public Name.QualifiedType qualify(Name.UnqualifiedType unqualified) {
      Name.QualifiedType qualifiedType = globalNameLookup.get(unqualified);
      if (qualifiedType == null) {
        throw new NoSuchElementException(unqualified.getName());
      }
      return qualifiedType;
    }
  }
}
