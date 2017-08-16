package com.yuvalshavit.effes2.compile;

import com.google.common.base.Objects;

public class Symbol {
  private int reg;
  private String type;

  public Symbol(int reg, String type) {
    this.reg = reg;
    this.type = type;
  }

  public int getReg() {
    return reg;
  }

  public String getType() {
    return type;
  }

  @SuppressWarnings("unused") // snakeyaml
  private Symbol() { }

  @SuppressWarnings("unused") // snakeyaml
  public void setReg(int reg) {
    this.reg = reg;
  }

  @SuppressWarnings("unused") // snakeyaml
  public void setType(String type) {
    this.type = type;
  }

  @Override
  public String toString() {
    return String.format("<%d %s>", reg, Objects.firstNonNull(type, "?"));
  }
}
