package com.yuvalshavit.effes2.compile;

import java.util.Formatter;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.antlr.v4.runtime.Token;

import com.yuvalshavit.effesvm.runtime.EffesOps;

import lombok.Data;

public final class TUtils {
  private TUtils() {}

  static EffesOps<Token> opsToString(StringBuilder sb) {
    return Op.factory(new Consumer<Op>() {
      private final Formatter formatter = new Formatter(sb);
      @Override
      public void accept(Op op) {
        formatter.format("%d:%d %s\n", op.token().getLine(), op.token().getCharPositionInLine() + 1, op); // +1 to 1-index it
      }

      @Override
      public String toString() {
        formatter.flush();
        return sb.toString();
      }
    });
  }

  static String trimExpectedOps(String ops) {
    if (ops == null) {
      return "";
    }
    Trim collapseSpacesAfterDebugSymbols = new Trim(Pattern.compile("^(\\d+:\\d+) {2,}", Pattern.MULTILINE), "$1 ");
    Trim removeComments = new Trim(Pattern.compile(" *#.*$", Pattern.MULTILINE), "");
    Trim removeBlankLines = new Trim(Pattern.compile("^\n*",  Pattern.MULTILINE), "");
    Trim nlUnicodeToNewline = new Trim(Pattern.compile("␤$", Pattern.MULTILINE), "\n");
    return collapseSpacesAfterDebugSymbols
      .andThen(removeComments)
      .andThen(removeBlankLines)
      .andThen(nlUnicodeToNewline)
      .apply(ops);
  }

  @Data
  private static class Trim implements Function<String, String> {
    private final Pattern pattern;
    private final String replacement;

    @Override
    public String apply(String o) {
      if (o == null) {
        return null;
      }
      return pattern.matcher(o).replaceAll(replacement);
    }
  }
}
