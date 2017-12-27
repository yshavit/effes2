package com.yuvalshavit.effes2.compile;

public interface TypeInfo {
  /**
   * Prefix for a "type" name that actually correspond to a module.
   */
  String MODULE_PREFIX = ":";

  int fieldsCount(String type);
  boolean hasField(String type, String fieldName);
  String fieldName(String type, int fieldIndex);
  MethodInfo getMethod(String targetType, String methodName);
  String getModule(String typeName);
}
