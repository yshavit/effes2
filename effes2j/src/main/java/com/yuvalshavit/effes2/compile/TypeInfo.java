package com.yuvalshavit.effes2.compile;

public interface TypeInfo {
  boolean hasField(String type, String fieldName);
  MethodInfo getMethod(String targetType, String methodName);
}
