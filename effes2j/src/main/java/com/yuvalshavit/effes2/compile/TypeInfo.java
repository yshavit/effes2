package com.yuvalshavit.effes2.compile;

public interface TypeInfo {
  boolean hasField(String type, String fieldName);
  String fieldName(String type, int fieldIndex);
  MethodInfo getMethod(String targetType, String methodName);
}
