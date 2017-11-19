package com.yuvalshavit.effes2.compile;

import org.antlr.v4.runtime.Token;

import com.yuvalshavit.effesvm.runtime.EffesOps;

class CompilerContext {
  final Scope scope;
  final FieldLookup fieldLookup;
  final LabelAssigner labelAssigner;
  final EffesOps<Void> out;
  final TypeInfo typeInfo;
  private final VarRef instanceVar;

  public CompilerContext(
    Scope scope,
    FieldLookup fieldLookup,
    LabelAssigner labelAssigner,
    EffesOps<Void> out,
    TypeInfo typeInfo,
    VarRef instanceVar)
  {
    this.scope = scope;
    this.fieldLookup = fieldLookup;
    this.labelAssigner = labelAssigner;
    this.out = out;
    this.typeInfo = typeInfo;
    this.instanceVar = instanceVar;
  }

  VarRef getInstanceContextVar(Token start, Token stop) {
    if (instanceVar == null) {
      throw new CompilationException(start, stop, "can't use \"this\" in static context");
    }
    return instanceVar;
  }
}
