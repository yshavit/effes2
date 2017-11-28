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
  public abstract void storeNoPop(EffesOps<?> ops);

  public static class LocalVar extends VarRef {
    private final int reg;
    private final String regStr;

    public LocalVar(int reg, String type) {
      super(type);
      this.reg = reg;
      this.regStr = String.valueOf(reg);
    }

    public int reg() {
      return reg;
    }

    @Override
    public void push(EffesOps<?> ops) {
      ops.pvar(regStr);
    }

    @Override
    public void store(EffesOps<?> ops) {
      ops.svar(regStr);
    }

    @Override
    public void storeNoPop(EffesOps<?> ops) {
      ops.Svar(regStr);
    }

    @Override
    public String toString() {
      return String.format("<%d %s>", reg, Objects.firstNonNull(getType(), "?"));
    }
  }

  public static class InstanceAndFieldVar extends VarRef {
    private final VarRef instance;
    private final String fieldName;
    private final String fieldTypeModule;

    public InstanceAndFieldVar(VarRef instance, String fieldName, String fieldTypeModule, String fieldType) {
      super(fieldType);
      this.instance = instance;
      this.fieldTypeModule = fieldTypeModule;
      this.fieldName = fieldName;
    }

    @Override
    public void push(EffesOps<?> ops) {
      instance.push(ops);
      ops.pushField(instance.getType(), fieldName);
    }

    @Override
    public void store(EffesOps<?> ops) {
      instance.push(ops);
      ops.storeField(fieldTypeModule + ':' + instance.getType(), fieldName);
    }

    @Override
    public void storeNoPop(EffesOps<?> ops) {
      ops.copy();
      store(ops);
    }
  }
}
