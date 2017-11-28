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
  private final VarRef.LocalVar instanceVar;

  public CompilerContext(
    Scope scope,
    LabelAssigner labelAssigner,
    EffesOps<Void> out,
    TypeInfo typeInfo,
    VarRef.LocalVar instanceVar)
  {
    this.scope = scope;
    this.labelAssigner = labelAssigner;
    this.out = out;
    this.typeInfo = typeInfo;
    this.instanceVar = instanceVar;
  }

  VarRef.LocalVar getInstanceContextVar(Token start, Token stop) {
    VarRef.LocalVar instanceVarLocal = tryGetInstanceContextVar();
    if (instanceVarLocal == null) {
      throw new CompilationException(start, stop, "can't use \"this\" in static context");
    }
    return instanceVarLocal;
  }

  VarRef.LocalVar tryGetInstanceContextVar() {
    return instanceVar;
  }

  public static EfctDeclarations efctDeclarationsFor(Appendable appendable) {
    return new AppendableBackedEfctDeclarations(appendable);
  }

  public interface EfctDeclarations {
    void methodDeclaration(String scope, String functionName, int nArgs, boolean hasRv);
    void endMethodDeclaration();
    void typeDeclaration(String typeName, List<String> argNames);
  }

  public static class AppendableBackedEfctDeclarations implements EfctDeclarations {
    private final Appendable appendable;

    public AppendableBackedEfctDeclarations(Appendable appendable) {
      this.appendable = appendable;
    }

    @Override
    public void methodDeclaration(String scope, String functionName, int nArgs, boolean hasRv) {
      try {
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
        appendable.append("TYPE 0 ").append(typeName);
        if (!argNames.isEmpty()) {
          appendable.append(' ');
          appendable.append(argNames.stream().collect(Collectors.joining(" ")));
        }
      } catch (IOException e) {
        handleException(e);
      }
    }

    protected void handleException(IOException e) {
      throw new RuntimeException(e);
    }
  }
}
