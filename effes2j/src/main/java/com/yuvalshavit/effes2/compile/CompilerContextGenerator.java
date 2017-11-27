package com.yuvalshavit.effes2.compile;

import com.yuvalshavit.effesvm.runtime.EffesOps;

class CompilerContextGenerator {
  final EffesOps<Void> ops;
  final CompilerContext.EfctDeclarations declarations;
  final TypeInfo typeInfo;

  public CompilerContextGenerator(EffesOps<Void> ops, CompilerContext.EfctDeclarations declarations, TypeInfo typeInfo) {
    this.ops = ops;
    this.declarations = declarations;
    this.typeInfo = typeInfo;
  }
}
