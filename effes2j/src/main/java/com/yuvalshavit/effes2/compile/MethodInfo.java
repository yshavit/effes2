package com.yuvalshavit.effes2.compile;

import java.util.Objects;

public class MethodInfo {
  private final int nDeclaredArgs;
  private final boolean hasReturnValue;

  public MethodInfo(int nDeclaredArgs, boolean hasReturnValue) {
    this.nDeclaredArgs = nDeclaredArgs;
    this.hasReturnValue = hasReturnValue;
  }

  public int getDeclaredArgsCount() {
    return nDeclaredArgs;
  }

  public boolean hasReturnValue() {
    return hasReturnValue;
  }

  @Override
  public String toString() {
    return String.format(
      "%d arg%s, %s return value",
      nDeclaredArgs,
      nDeclaredArgs == 1 ? "" : "s",
      hasReturnValue ? "has" : "no");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    MethodInfo that = (MethodInfo) o;
    return nDeclaredArgs == that.nDeclaredArgs && hasReturnValue == that.hasReturnValue;
  }

  @Override
  public int hashCode() {
    return Objects.hash(nDeclaredArgs, hasReturnValue);
  }
}
