package com.yuvalshavit.effes2.compile;

import java.util.Formatter;
import java.util.function.Consumer;

import org.antlr.v4.runtime.Token;

import com.yuvalshavit.effesvm.runtime.EffesOps;

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
    return ops
      .replaceAll(" *#.*\n", "\n")
      .replaceAll("^\n*", "") // remove blank lines
      .replaceAll("␤\n", "\n"); // turn ␤ into a blank line
  }
}
