package com.yuvalshavit.effes2.compile;

import java.util.function.Consumer;

import com.yuvalshavit.effesvm.runtime.EffesOps;

public final class TUtils {
  private TUtils() {}

  static EffesOps<Void> opsToString(StringBuilder sb) {
    return Op.factory(new Consumer<Op>() {
      @Override
      public void accept(Op op) {
        sb.append(op).append('\n');
      }

      @Override
      public String toString() {
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
      .replaceAll("\n␤\n", "\n\n"); // turn ␤ into a blank line
  }
}
