package com.yuvalshavit.effes2.compile;

import com.yuvalshavit.effesvm.runtime.EffesOps;

public final class TUtils {
  private TUtils() {}

  static EffesOps<Void> opsToString(StringBuilder sb) {
    return Op.factory(op -> sb.append(op).append('\n'));
  }
}
