package com.yuvalshavit.effes2.compile;

import java.util.Collections;
import java.util.List;

import com.google.common.collect.Lists;
import com.yuvalshavit.effes2.parse.EffesParser;
import com.yuvalshavit.effes2.util.Dispatcher;

@Dispatcher.SubclassesAreIn(EffesParser.class)
public class DeclarationCompiler extends CompileDispatcher<EffesParser.DeclarationContext> {

  private final CompilerContextGenerator ccGen;

  public DeclarationCompiler(CompilerContextGenerator ccGen) {
    super(EffesParser.DeclarationContext.class);
    this.ccGen = ccGen;
  }

  @Dispatched
  public void apply(EffesParser.TypeDeclarationContext ctx) {
    // Type and ctor args are already registered. Just compile.
    String typeName = ctx.IDENT_TYPE().getSymbol().getText();
    ccGen.declarations.typeDeclaration(typeName, getArgNames(ctx));
    List<EffesParser.MethodDeclarationContext> methods = ctx.methodDeclaration();
    if (methods != null) {
      methods.forEach(c -> MethodCompiler.compile(c, ccGen, typeName));
    }
  }

  public static List<String> getArgNames(EffesParser.TypeDeclarationContext ctx) {
    return ctx.argsDeclaration() == null
        ? Collections.emptyList()
        : Lists.transform(ctx.argsDeclaration().IDENT_NAME(), name -> {
          assert name != null;
          return name.getSymbol().getText();
        });
  }

  @Dispatched
  public void apply(EffesParser.MethodDeclarationContext ctx) {
    MethodCompiler.compile(ctx, ccGen, null); // static method
  }
}
