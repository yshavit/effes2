package com.yuvalshavit.effes2.compile;

import java.io.IOException;
import java.io.Writer;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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
    parsed.forEach((module, fileContext) -> {
      try (Writer writer = writers.apply(module)) {
        EffesOps<Void> ops = Op.factory(op -> {
          try {
            writer.append(op.toString());
            writer.append('\n');
          } catch (IOException e) {
            errors.accept(MiscUtil.toStringWithStackTrace(e));
          }
        });
        CompilerContext.EfctDeclarations declarations = CompilerContext.efctDeclarationsFor(writer);
        CompilerContextGenerator ccg = new CompilerContextGenerator(module, ops, declarations, typeInfo);
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
    Map<String,SingleTypeInfo> typeInfos = new HashMap<>();
    parsed.entrySet().stream()
      .map(entry -> new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), entry.getValue().declaration()))
      .flatMap(entry -> entry
        .getValue().stream()
        .map(EffesParser.DeclarationContext::typeDeclaration).filter(Objects::nonNull)
        .map(typeDeclaration -> new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), typeDeclaration)))
      .forEach(entry -> {
        String moduleName = entry.getKey();
        EffesParser.TypeDeclarationContext typeDeclaration = entry.getValue();
        String typeName = typeDeclaration.IDENT_TYPE().getSymbol().getText();
        if (typeInfos.containsKey(typeName)) {
          errors.accept("Duplicate type name: " + typeName);
          return;
        }
        Map<String,MethodInfo> methods;
        List<EffesParser.MethodDeclarationContext> methodDeclarations = typeDeclaration.methodDeclaration();
        if (methodDeclarations == null) {
          methods = Collections.emptyMap();
        } else {
          methods = new HashMap<>(methodDeclarations.size());
          for (EffesParser.MethodDeclarationContext methodDeclaration : methodDeclarations) {
            UserlandMethodInfo methodInfo = createMethodInfo(typeName, methodDeclaration);
            if (methods.containsKey(methodInfo.methodName)) {
              errors.accept("duplicate method " + methodInfo.methodName + " in " + typeName);
              continue;
            }
            methods.put(methodInfo.methodName, methodInfo);
          }
        }
        SingleTypeInfo typeInfo = new SingleTypeInfo(moduleName, DeclarationCompiler.getArgNames(typeDeclaration), methods);
        typeInfos.put(typeName, typeInfo);
      });
    for (Map.Entry<String, EffesParser.FileContext> entry : parsed.entrySet()) {
      String moduleName = entry.getKey();
      Map<String, UserlandMethodInfo> methodInfos = entry.getValue()
        .declaration()
        .stream()
        .map(EffesParser.DeclarationContext::methodDeclaration)
        .filter(Objects::nonNull)
        .map(methodDecl -> createMethodInfo(moduleName, methodDecl))
        .collect(Collectors.toMap(method -> method.methodName, Function.identity()));
      typeInfos.put(moduleName + TypeInfo.MODULE_PREFIX, new SingleTypeInfo(moduleName, Collections.emptyList(), methodInfos));
    }
    for (EffesBuiltinType builtinType : EffesBuiltinType.values()) {
      SingleTypeInfo typeInfo = new SingleTypeInfo("Builtin", Collections.emptyList(), builtinType.methods());
      typeInfos.put(builtinType.typeName(), typeInfo);
    }
    return new TypeInfoImpl(typeInfos);
  }

  private static UserlandMethodInfo createMethodInfo(String typeName, EffesParser.MethodDeclarationContext methodDeclaration) {
    String methodName = methodDeclaration.IDENT_NAME().getSymbol().getText();
    int nArgs = methodDeclaration.argsDeclaration().IDENT_NAME() == null
      ? 0
      : methodDeclaration.argsDeclaration().IDENT_NAME().size();
    return new UserlandMethodInfo(nArgs, methodDeclaration.ARROW() != null, typeName, methodName);
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
    final String module;
    final List<String> fields;
    final Map<String,? extends MethodInfo> methods;

    public SingleTypeInfo(String module, List<String> fields, Map<String,? extends MethodInfo> methods) {
      this.module = module;
      this.fields = fields;
      this.methods = methods;
    }
  }

  @VisibleForTesting
  static class UserlandMethodInfo extends MethodInfo {

    private final String targetType;
    private final String methodName;

    public UserlandMethodInfo(int nDeclaredArgs, boolean hasReturnValue, String targetType, String methodName) {
      super(nDeclaredArgs, hasReturnValue);
      this.targetType = targetType;
      this.methodName = methodName;
    }

    @Override
    public void invoke(CompilerContext cc) {
      cc.out.call(cc.qualifyType(targetType), methodName);
    }
  }

  private static class TypeInfoImpl implements TypeInfo {
    private final Map<String,SingleTypeInfo> typeInfos;

    public TypeInfoImpl(Map<String,SingleTypeInfo> typeInfos) {
      this.typeInfos = typeInfos;
    }

    @Override
    public int fieldsCount(String type) {
      SingleTypeInfo info = typeInfos.get(type);
      return info == null
        ? -1
        : info.fields.size();
    }

    @Override
    public boolean hasField(String type, String fieldName) {
      SingleTypeInfo info = typeInfos.get(type);
      return info != null && info.fields.contains(fieldName);
    }

    @Override
    public String fieldName(String type, int fieldIndex) {
      return typeInfos.get(type).fields.get(fieldIndex); // TODO need better errors
    }

    @Override
    public MethodInfo getMethod(String targetType, String methodName) {
      SingleTypeInfo info = typeInfos.get(targetType);
      return info == null ? null : info.methods.get(methodName);
    }

    @Override
    public String getModule(String typeName) {
      return typeInfos.get(typeName).module; // TODO better errors here, too
    }
  }
}
