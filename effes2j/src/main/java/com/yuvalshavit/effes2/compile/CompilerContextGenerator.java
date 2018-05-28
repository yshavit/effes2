package com.yuvalshavit.effes2.compile;

import com.yuvalshavit.effesvm.runtime.EffesOps;

import lombok.Data;

@Data
class CompilerContextGenerator {
  final Name.Module module;
  final EffesOps<Void> ops;
  final CompilerContext.EfctDeclarations declarations;
  final TypeInfo typeInfo;
}
