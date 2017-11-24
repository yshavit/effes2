package com.yuvalshavit.effes2.compile;

public interface TypeInfo {
  int fieldsCount(String type);
  boolean hasField(String type, String fieldName);
  String fieldName(String type, int fieldIndex);
  MethodInfo getMethod(String targetType, String methodName);
}
