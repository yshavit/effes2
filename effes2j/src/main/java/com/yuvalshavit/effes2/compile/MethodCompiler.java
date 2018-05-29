package com.yuvalshavit.effes2.compile;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.tree.TerminalNode;

import com.yuvalshavit.effes2.parse.EffesParser;

public class MethodCompiler {

  private MethodCompiler() {}

  public static void compile(EffesParser.MethodDeclarationContext ctx, CompilerContextGenerator ccGen, Name.UnqualifiedType instanceType) {
    List<TerminalNode> argNodes = ctx.argsDeclaration().IDENT_NAME();
    if (argNodes == null) {
      argNodes = Collections.emptyList();
    }
    List<String> argNames = argNodes.stream().map(n -> {
      return n.getSymbol().getText();
    }).collect(Collectors.toList());
    Name.EvmScope declarationScope = instanceType == null
      ? ccGen.getModule()
      : new Name.QualifiedType(ccGen.getModule(), instanceType);
    ccGen.declarations.methodDeclaration(declarationScope, ctx.IDENT_NAME().getSymbol().getText(), argNames.size(), ctx.ARROW() != null);
    Scope scope = new Scope();
    scope.inNewScope(() -> {
      Name.QualifiedType instanceQualifiedName = ((Name.QualifiedType) declarationScope);
      VarRef thisVar = instanceType == null
        ? null
        : scope.allocateLocal("<this>", false, instanceQualifiedName);
      argNames.forEach(name -> scope.allocateLocal(name, false));
      if (thisVar != null) {
        for (int i = 0, nFields = ccGen.typeInfo.fieldsCount(instanceQualifiedName); i < nFields; ++i) {
          String fieldName = ccGen.typeInfo.fieldName(instanceQualifiedName, i);
          scope.allocateLocal(fieldName, false, new VarRef.InstanceAndFieldVar(thisVar, fieldName, null));
        }
      }
      CompilerContext context = new CompilerContext(scope, new LabelAssigner(ccGen.ops), ccGen.ops, ccGen.typeInfo, ccGen.module, thisVar);
      if (!new StatementCompiler(context).compileBlock(ctx.block())) {
        ccGen.ops.rtrn(); // this may be unreachable in some cases (e.g. "if c then return a, else return b"), but it's never harmful
      }
    });
    ccGen.declarations.endMethodDeclaration();
  }
}
