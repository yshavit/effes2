package com.yuvalshavit.effes2.compile;

import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.yuvalshavit.effes2.parse.EffesParser;
import com.yuvalshavit.effes2.parse.ParseUtils;
import com.yuvalshavit.effesvm.runtime.EffesOps;

public class Compiler {

  private Compiler() {}

  public static void compile(List<CompileUnit> compileUnits, Function<String,Writer> writers, Consumer<String> errors) {
    // first, parse
    Map<String,EffesParser.FileContext> parsed = parse(compileUnits, errors);
    TypeInfo typeInfo = scanForTypes(errors, parsed);
    parsed.forEach((module,fileContext) -> {
      try (Writer writer = writers.apply(module)) {
        EffesOps<Void> ops = Op.factory(op -> {
          try {
            writer.append(op.toString());
            writer.append('\n');
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
        CompilerContext.EfctDeclarations declarations = CompilerContext.efctDeclarationsFor(writer);
        CompilerContextGenerator ccg = new CompilerContextGenerator(ops, declarations, typeInfo);
        DeclarationCompiler compiler = new DeclarationCompiler(ccg);
        fileContext.declaration().forEach(compiler::apply);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
  }

  public static Map<String,EffesParser.FileContext> parse(List<CompileUnit> compileUnits, Consumer<String> errors) {
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
      parsed.put(unit.moduleName, file);
    });
    return parsed;
  }

  public static TypeInfo scanForTypes(Consumer<String> errors, Map<String,EffesParser.FileContext> parsed) {
    Map<String,SingleTypeInfo> typeInfos = new HashMap<>();
    parsed.values().stream()
      .map(EffesParser.FileContext::declaration)
      .flatMap(decls -> decls.stream().map(EffesParser.DeclarationContext::typeDeclaration).filter(Objects::nonNull))
      .forEach(typeDeclaration -> {
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
            String methodName = methodDeclaration.IDENT_NAME().getSymbol().getText();
            if (methods.containsKey(methodName)) {
              errors.accept("duplicate method " + methodName + " in " + typeName);
              continue;
            }
            int nArgs = methodDeclaration.argsDeclaration().IDENT_NAME() == null
              ? 0
              : methodDeclaration.argsDeclaration().IDENT_NAME().size();
            MethodInfo methodInfo = new MethodInfo(nArgs, methodDeclaration.ARROW() != null);
            methods.put(methodName, methodInfo);
          }
        }
        SingleTypeInfo typeInfo = new SingleTypeInfo(DeclarationCompiler.getArgNames(typeDeclaration), methods);
        typeInfos.put(typeName, typeInfo);
      });
    return new TypeInfoImpl(typeInfos);
  }

  public static class CompileUnit {
    private final String moduleName;
    private final String sourceDescription;
    private final Supplier<String> reader;

    public CompileUnit(String moduleName, String sourceDescription, Supplier<String> reader) {
      this.moduleName = moduleName;
      this.sourceDescription = sourceDescription;
      this.reader = reader;
    }
  }

  private static class SingleTypeInfo {
    final List<String> fields;
    final Map<String,MethodInfo> methods;

    public SingleTypeInfo(List<String> fields, Map<String,MethodInfo> methods) {
      this.fields = fields;
      this.methods = methods;
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
  }
}