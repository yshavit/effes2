package com.yuvalshavit.effes2.compile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.Token;

import com.yuvalshavit.effesvm.runtime.EffesOps;

class CompilerContext {
  final Scope scope;
  final LabelAssigner labelAssigner;
  final EffesOps<Void> out;
  final TypeInfo typeInfo;
  final String moduleName;
  private final VarRef instanceVar;

  public CompilerContext(
    Scope scope,
    LabelAssigner labelAssigner,
    EffesOps<Void> out,
    TypeInfo typeInfo,
    String moduleName,
    VarRef instanceVar)
  {
    this.scope = scope;
    this.labelAssigner = labelAssigner;
    this.out = out;
    this.typeInfo = typeInfo;
    this.moduleName = moduleName;
    this.instanceVar = instanceVar;
  }

  VarRef getInstanceContextVar(Token start, Token stop) {
    VarRef instanceVarLocal = tryGetInstanceContextVar();
    if (instanceVarLocal == null) {
      throw new CompilationException(start, stop, "can't use \"this\" in static context");
    }
    return instanceVarLocal;
  }

  VarRef tryGetInstanceContextVar() {
    return instanceVar;
  }

  public static EfctDeclarations efctDeclarationsFor(Appendable appendable) {
    return new AppendableBackedEfctDeclarations(appendable);
  }

  public String qualifyType(String typeName) {
    String typeModule = typeModuleName(typeName);
    return qualified(typeModule, typeName);
  }

  public static String qualified(String typeModule, String typeName) {
    return typeModule + ':' + typeName;
  }

  public String typeModuleName(String typeName) {
    String typeModule = typeInfo.getModule(typeName);
    if (typeModule.equals(moduleName)) {
      typeModule = "";
    }
    return typeModule;
  }

  public interface EfctDeclarations {
    void methodDeclaration(String scope, String functionName, int nArgs, boolean hasRv);
    void endMethodDeclaration();
    void typeDeclaration(String typeName, List<String> argNames);
  }

  public static class AppendableBackedEfctDeclarations implements EfctDeclarations {
    private final Appendable appendable;
    private DeclarationType declarationType;

    public AppendableBackedEfctDeclarations(Appendable appendable) {
      this.appendable = appendable;
    }

    @Override
    public void methodDeclaration(String scope, String functionName, int nArgs, boolean hasRv) {
      try {
        start(DeclarationType.METHOD);
        appendable
          .append("FUNC ")
          .append(scope).append(' ')
          .append(functionName).append(' ')
          .append(String.valueOf(nArgs)).append(' ')
          .append(hasRv ? "1" : "0").append(' ')
          .append("0\n");
      } catch (IOException e) {
        handleException(e);
      }
    }

    @Override
    public void endMethodDeclaration() {
      try {
        appendable.append('\n');
      } catch (IOException e) {
        handleException(e);
      }
    }

    @Override
    public void typeDeclaration(String typeName, List<String> argNames) {
      try {
        start(DeclarationType.TYPE);
        appendable.append("TYPE 0 ").append(typeName);
        if (!argNames.isEmpty()) {
          appendable.append(' ');
          appendable.append(argNames.stream().collect(Collectors.joining(" ")));
        }
        appendable.append('\n');
      } catch (IOException e) {
        handleException(e);
      }
    }

    protected void handleException(IOException e) {
      throw new RuntimeException(e);
    }

    private void start(DeclarationType type) throws IOException {
      // FUNC blocks already have a trailing newline, so no need to add them.
      // But if we're transitioning from a string of TYPE declarations to something else, then put in a newline. It's not needed, just looks nicer.
      if (declarationType == DeclarationType.TYPE && type != DeclarationType.TYPE) {
        appendable.append('\n');
      }
      declarationType = type;
    }

    private enum DeclarationType {
      TYPE,
      METHOD,
    }

  }
}
