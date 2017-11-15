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
}
