package com.yuvalshavit.effes2.compile;

import org.antlr.v4.runtime.ParserRuleContext;

import com.yuvalshavit.effes2.util.VoidDispatcher;

public class CompileDispatcher<T extends ParserRuleContext> extends VoidDispatcher<T> {
  public CompileDispatcher(Class<T> parentClass) {
    super(parentClass, CompilationException::normalize);
  }
}
