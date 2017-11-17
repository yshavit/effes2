package com.yuvalshavit.effes2.util;

import static org.testng.Assert.assertEquals;

import java.util.function.Consumer;
import java.util.function.Function;

import org.antlr.v4.runtime.ParserRuleContext;

import com.yuvalshavit.effes2.parse.EffesParser;
import com.yuvalshavit.effes2.parse.ParseUtils;

public class ParseChecker {
  private ParseChecker() {}

  public static <R extends ParserRuleContext> void check(String description, String input, Function<EffesParser,R> rule, Consumer<R> astChecker) {
    StringBuilder errsSb = new StringBuilder();
    R ast = ParseUtils.parse(
      input,
      rule,
      (line, lineOffset, msg) -> errsSb.append(String.format("error at <%s> %d:%d: %s%n", description, line, lineOffset, msg)));

    String errMessages = errsSb.toString();
    if (!errMessages.isEmpty()) {
      System.err.println(errMessages); // we'll assertEquals on them later, but for now print them in case the prettyPrint fails first.
    }
    astChecker.accept(ast);
    assertEquals(errMessages, "", "error messages");
  }
}
