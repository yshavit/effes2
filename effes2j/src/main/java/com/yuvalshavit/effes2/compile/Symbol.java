package com.yuvalshavit.effes2.compile;

import com.google.common.base.Objects;
import com.yuvalshavit.effesvm.runtime.EffesOps;

public abstract class Symbol {
  private final String type;

  public Symbol(String type) {
    this.type = type;
  }

  public String getType() {
    return type;
  }

  public abstract void push(EffesOps<Void> ops);
  public abstract void store(EffesOps<Void> ops);
  public abstract void storeNoPop(EffesOps<Void> out);

  public static class LocalVar extends Symbol {
    private final int reg;
    private String regStr;

    public LocalVar(int reg, String type) {
      super(type);
      this.reg = reg;
    }

    @Override
    public void push(EffesOps<Void> ops) {
      ops.pvar(regStr());
    }

    @Override
    public void store(EffesOps<Void> ops) {
      ops.svar(regStr());
    }

    @Override
    public void storeNoPop(EffesOps<Void> out) {
      out.Svar(regStr());
    }

    @Override
    public String toString() {
      return String.format("<%d %s>", reg, Objects.firstNonNull(getType(), "?"));
    }

    private String regStr() {
      String rv = regStr;
      if (rv == null) {
        rv = String.valueOf(reg);
        regStr = rv;
      }
      return rv;
    }
  }
}
