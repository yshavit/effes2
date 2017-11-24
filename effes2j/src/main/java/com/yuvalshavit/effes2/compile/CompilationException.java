package com.yuvalshavit.effes2.compile;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

import com.yuvalshavit.effes2.parse.EffesParser;

public class CompilationException extends RuntimeException {
  private static final long serialVersionUID = -7591615499145910642L;

  public CompilationException(Token start, Token stop, Exception e) {
    super(message(start, stop, e.getMessage()), e);
  }

  public CompilationException(Token start, Token stop, String message) {
    super(message(start, stop, message));
  }

  public CompilationException(ParserRuleContext ctx, String message) {
    this(ctx.getStart(), ctx.getStop(), message);
  }

  public static CompilationException normalize(ParserRuleContext ctx, Exception e) {
    return (e instanceof CompilationException)
      ? (CompilationException) e
      : new CompilationException(ctx.start, ctx.stop, e);
  }

  private static String message(Token start, Token stop, String message) {
    int stopLen = stop.getStopIndex() - stop.getStartIndex();
    return String.format(
      "%s between %d:%d and %d:%d",
      message,
      start.getLine(),
      start.getCharPositionInLine() + 1,
      stop.getLine(),
      stop.getCharPositionInLine() + stopLen + 1);
  }

}
