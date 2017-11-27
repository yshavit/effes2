package com.yuvalshavit.effes2.compile;

import java.util.Collections;
import java.util.List;

import org.antlr.v4.runtime.tree.TerminalNode;

import com.google.common.collect.Lists;
import com.yuvalshavit.effes2.parse.EffesParser;

public class MethodCompiler {

  private MethodCompiler() {}

  public static void compile(EffesParser.MethodDeclarationContext ctx, CompilerContextGenerator ccGen, String instanceType) {
    List<TerminalNode> argNodes = ctx.argsDeclaration().IDENT_NAME();
    if (argNodes == null) {
      argNodes = Collections.emptyList();
    }
    List<String> argNames = Lists.transform(argNodes, n -> {
      assert n != null;
      return n.getSymbol().getText();
    });
    String scopeName = instanceType == null
      ? ":"
      : (":" + instanceType);
    ccGen.declarations.methodDeclaration(scopeName, ctx.IDENT_NAME().getSymbol().getText(), argNames.size(), ctx.ARROW() != null);
    Scope scope = new Scope();
    scope.inNewScope(() -> {
      VarRef.LocalVar thisVar = instanceType == null
        ? null
        : scope.allocateLocal("<this>", false, instanceType);
      argNames.forEach(name -> scope.allocateLocal(name, false));
      CompilerContext context = new CompilerContext(scope, new LabelAssigner(ccGen.ops), ccGen.ops, ccGen.typeInfo, thisVar);
      if (!new StatementCompiler(context).compileBlock(ctx.block())) {
        ccGen.ops.rtrn(); // this may be unreachable in some cases (e.g. "if c then return a, else return b"), but it's never harmful
      }
    });
    ccGen.declarations.endMethodDeclaration();
  }
}
