package com.yuvalshavit.effes2.compile;

import java.util.Collections;
import java.util.List;

import org.antlr.v4.runtime.tree.TerminalNode;

import com.google.common.collect.Lists;
import com.yuvalshavit.effes2.parse.EffesParser;

public class MethodCompiler {

  private MethodCompiler() {}

  public static void compile(EffesParser.MethodDeclarationContext ctx, CompilerContext cc) {
    List<TerminalNode> argNodes = ctx.argsDeclaration().IDENT_NAME();
    if (argNodes == null) {
      argNodes = Collections.emptyList();
    }
    List<String> argNames = Lists.transform(argNodes, n -> {
      assert n != null;
      return n.getSymbol().getText();
    });
    String scope = cc.tryGetInstanceContextVar() == null
      ? ":"
      : (":" + cc.tryGetInstanceContextVar().getType());
    cc.rawOut.methodDeclaration(scope, ctx.IDENT_NAME().getSymbol().getText(), argNames.size(), ctx.ARROW() != null);
    cc.scope.inNewScope(() -> {
      if (cc.tryGetInstanceContextVar() != null) {
        cc.scope.allocateLocal("<this>", false, cc.tryGetInstanceContextVar().getType());
      }
      argNames.forEach(name -> cc.scope.allocateLocal(name, false));
      if (!new StatementCompiler(cc).compileBlock(ctx.block())) {
        cc.out.rtrn(); // this may be unreachable in some cases (e.g. "if c then return a, else return b"), but it's never harmful
      }
    });
    cc.rawOut.endMethodDeclaration();
  }
}
