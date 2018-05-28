package com.yuvalshavit.effes2.compile;

import com.google.common.base.Objects;
import com.yuvalshavit.effesvm.runtime.EffesOps;

public abstract class VarRef {
  private final Name.QualifiedType type;

  public VarRef(Name.QualifiedType type) {
    this.type = type;
  }

  public Name.QualifiedType getType() {
    return type;
  }

  public abstract void push(Name.Module context, EffesOps<?> ops);
  public abstract void store(Name.Module context, EffesOps<?> ops);
  public abstract void storeNoPop(Name.Module context, EffesOps<?> ops);
  public abstract VarRef withType(Name.QualifiedType type);

  public static class LocalVar extends VarRef {
    private final int reg;
    private final String regStr;

    public LocalVar(int reg, Name.QualifiedType type) {
      super(type);
      this.reg = reg;
      this.regStr = String.valueOf(reg);
    }

    public int reg() {
      return reg;
    }

    @Override
    public LocalVar withType(Name.QualifiedType type) {
      return new LocalVar(reg, type);
    }

    @Override
    public void push(Name.Module context, EffesOps<?> ops) {
      ops.pvar(regStr);
    }

    @Override
    public void store(Name.Module context, EffesOps<?> ops) {
      ops.svar(regStr);
    }

    @Override
    public void storeNoPop(Name.Module context, EffesOps<?> ops) {
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

    public InstanceAndFieldVar(VarRef instance, String fieldName, Name.QualifiedType fieldType) {
      super(fieldType);
      this.instance = instance;
      this.fieldName = fieldName;
    }

    @Override
    public VarRef withType(Name.QualifiedType type) {
      return new InstanceAndFieldVar(instance, fieldName, type);
    }

    @Override
    public void push(Name.Module context, EffesOps<?> ops) {
      instance.push(context, ops);
      ops.pushField(instance.getType().evmDescriptor(context), fieldName);
    }

    @Override
    public void store(Name.Module context, EffesOps<?> ops) {
      instance.push(context, ops);
      ops.storeField(instance.getType().evmDescriptor(context), fieldName);
    }

    @Override
    public void storeNoPop(Name.Module context, EffesOps<?> ops) {
      ops.copy();
      store(context, ops);
    }
  }
}
