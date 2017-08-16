package com.yuvalshavit.effes2.compile;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

public class CompilationException extends RuntimeException {

  public CompilationException(Token start, Token stop, Exception e) {
    super(message(start, stop, e), e);
  }

  public static CompilationException normalize(ParserRuleContext ctx, Exception e) {
    return (e instanceof CompilationException)
      ? (CompilationException) e
      : new CompilationException(ctx.start, ctx.stop, e);
  }

  private static String message(Token start, Token stop, Exception e) {
    return String.format(
      "%s between %d:%d and %d:%d",
      e.getMessage(),
      start.getLine(),
      start.getCharPositionInLine() + 1,
      stop.getLine(),
      stop.getCharPositionInLine() + 1);
  }

}
