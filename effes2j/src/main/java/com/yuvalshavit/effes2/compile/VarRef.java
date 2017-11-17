package com.yuvalshavit.effes2.compile;

import com.google.common.base.Objects;
import com.yuvalshavit.effesvm.runtime.EffesOps;

public abstract class VarRef {
  private final String type;

  public VarRef(String type) {
    this.type = type;
  }

  public String getType() {
    return type;
  }

  public abstract void push(EffesOps<?> ops);
  public abstract void store(EffesOps<?> ops);
  public abstract void storeNoPop(EffesOps<?> out);

  public static class LocalVar extends VarRef {
    private final int reg;
    private String regStr;

    public LocalVar(int reg, String type) {
      super(type);
      this.reg = reg;
    }

    public int reg() {
      return reg;
    }

    @Override
    public void push(EffesOps<?> ops) {
      ops.pvar(regStr());
    }

    @Override
    public void store(EffesOps<?> ops) {
      ops.svar(regStr());
    }

    @Override
    public void storeNoPop(EffesOps<?> out) {
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
