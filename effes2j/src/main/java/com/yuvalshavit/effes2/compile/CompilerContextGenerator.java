package com.yuvalshavit.effes2.compile;

import org.antlr.v4.runtime.Token;

import com.yuvalshavit.effesvm.runtime.EffesOps;

import lombok.Data;

@Data
class CompilerContextGenerator {
  final Name.Module module;
  final EffesOps<Token> ops;
  final CompilerContext.EfctDeclarations declarations;
  final TypeInfo typeInfo;
}
