package com.yuvalshavit.effes2.compile;

import java.util.Collections;
import java.util.List;

import com.google.common.collect.Lists;
import com.yuvalshavit.effes2.parse.EffesParser;
import com.yuvalshavit.effes2.util.MiscUtil;

public class DeclarationCompiler {

  private final CompilerContextGenerator ccGen;

  public DeclarationCompiler(CompilerContextGenerator ccGen) {
    this.ccGen = ccGen;
  }

  public void apply(EffesParser.DeclarationContext ctx) {
    MiscUtil.ifNotNull(ctx.typeDeclaration(), this::apply);
    MiscUtil.ifNotNull(ctx.methodDeclaration(), this::apply);
  }

  public void apply(EffesParser.TypeDeclarationContext ctx) {
    // Type and ctor args are already registered. Just compile.
    Name.UnqualifiedType type = CompilerContext.readUnqualifiedType(ctx.IDENT_TYPE());
    ccGen.declarations.typeDeclaration(type.getName(), getArgNames(ctx));
    List<EffesParser.MethodDeclarationContext> methods = ctx.methodDeclaration();
    if (methods != null) {
      methods.forEach(c -> MethodCompiler.compile(c, ccGen, type));
    }
  }

  public void apply(EffesParser.MethodDeclarationContext ctx) {
    MethodCompiler.compile(ctx, ccGen, null); // static method
  }

  public static List<String> getArgNames(EffesParser.TypeDeclarationContext ctx) {
    return ctx.argsDeclaration() == null
        ? Collections.emptyList()
        : Lists.transform(ctx.argsDeclaration().IDENT_NAME(), name -> {
          assert name != null;
          return name.getSymbol().getText();
        });
  }

}
