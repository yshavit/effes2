package com.yuvalshavit.effes2.compile;

public interface TypeInfo {
  int fieldsCount(Name.QualifiedType type);
  boolean hasField(Name.QualifiedType type, String fieldName);
  String fieldName(Name.QualifiedType type, int fieldIndex);
  MethodInfo getMethod(Name.EvmScope targetType, String methodName);
  Name.QualifiedType qualify(Name.UnqualifiedType unqualified);
}
