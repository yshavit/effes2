package com.yuvalshavit.effes2.compile;

import org.antlr.v4.runtime.Token;

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

  public abstract void push(Token token, Name.Module context, EffesOps<Token> ops);
  public abstract void store(Token token, Name.Module context, EffesOps<Token> ops);
  public abstract void storeNoPop(Token token, Name.Module context, EffesOps<Token> ops);
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
    public void push(Token token, Name.Module context, EffesOps<Token> ops) {
      ops.pvar(token, regStr);
    }

    @Override
    public void store(Token token, Name.Module context, EffesOps<Token> ops) {
      ops.svar(token, regStr);
    }

    @Override
    public void storeNoPop(Token token, Name.Module context, EffesOps<Token> ops) {
      ops.Svar(token, regStr);
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
    public void push(Token token, Name.Module context, EffesOps<Token> ops) {
      instance.push(token, context, ops);
      ops.pushField(token, instance.getType().evmDescriptor(context), fieldName);
    }

    @Override
    public void store(Token token, Name.Module context, EffesOps<Token> ops) {
      instance.push(token, context, ops);
      ops.storeField(token, instance.getType().evmDescriptor(context), fieldName);
    }

    @Override
    public void storeNoPop(Token token, Name.Module context, EffesOps<Token> ops) {
      ops.copy(token);
      store(token, context, ops);
    }
  }
}
