package com.yuvalshavit.effes2.compile;

import com.yuvalshavit.effesvm.runtime.EffesOps;

class CompilerContextGenerator {
  final String module;
  final EffesOps<Void> ops;
  final CompilerContext.EfctDeclarations declarations;
  final TypeInfo typeInfo;

  public CompilerContextGenerator(String module, EffesOps<Void> ops, CompilerContext.EfctDeclarations declarations, TypeInfo typeInfo) {
    this.module = module;
    this.ops = ops;
    this.declarations = declarations;
    this.typeInfo = typeInfo;
  }
}
