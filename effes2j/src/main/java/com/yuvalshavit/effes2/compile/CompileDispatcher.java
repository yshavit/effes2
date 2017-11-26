package com.yuvalshavit.effes2.compile;

import org.antlr.v4.runtime.ParserRuleContext;

import com.yuvalshavit.effes2.util.VoidDispatcher;

public class CompileDispatcher<T extends ParserRuleContext> extends VoidDispatcher<T> {
  protected final CompilerContext cc;

  public CompileDispatcher(Class<T> parentClass, CompilerContext cc) {
    super(parentClass, CompilationException::normalize);
    this.cc = cc;
  }
}
