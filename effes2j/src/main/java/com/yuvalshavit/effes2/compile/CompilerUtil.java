package com.yuvalshavit.effes2.compile;

public class CompilerUtil {
  private CompilerUtil() { }

  public static boolean isForEnclosingScope(String variableName) {
    return variableName.startsWith("@");
  }

  public static String plainVariableName(String variableName) {
    return isForEnclosingScope(variableName)
      ? variableName.substring(1)
      : variableName;
  }
}
