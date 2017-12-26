package com.yuvalshavit.effes2.compile;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

public class CompilationException extends RuntimeException {
  private static final long serialVersionUID = -7591615499145910642L;

  private final String locationMessage;

  public CompilationException(Token start, Token stop, Exception e) {
    super(e);
    locationMessage = locationMessage(start, stop);
  }

  public CompilationException(Token start, Token stop, String message) {
    super(message);
    locationMessage = locationMessage(start, stop);
  }

  public CompilationException(ParserRuleContext ctx, String message) {
    this(ctx.getStart(), ctx.getStop(), message);
  }

  public static CompilationException normalize(ParserRuleContext ctx, Exception e) {
    return (e instanceof CompilationException)
      ? (CompilationException) e
      : new CompilationException(ctx.start, ctx.stop, e);
  }

  @Override
  public String getMessage() {
    return super.getMessage() + ' ' + locationMessage;
  }

  public String getLocationMessage() {
    return locationMessage;
  }

  private static String locationMessage(Token start, Token stop) {
    int stopLen = stop.getStopIndex() - stop.getStartIndex();
    return String.format(
      "between %d:%d and %d:%d",
      start.getLine(),
      start.getCharPositionInLine() + 1,
      stop.getLine(),
      stop.getCharPositionInLine() + stopLen + 1);
  }
}
