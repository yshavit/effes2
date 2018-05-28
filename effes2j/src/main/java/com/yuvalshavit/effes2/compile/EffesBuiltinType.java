package com.yuvalshavit.effes2.compile;

import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;

import com.google.common.collect.ImmutableMap;
import com.yuvalshavit.effesvm.runtime.EffesNativeType;
import com.yuvalshavit.effesvm.runtime.EffesOps;

public enum EffesBuiltinType {
  CHAR_STREAM_IN("CharStreamIn", EffesNativeType.STREAM_IN, build()
    .put("readLine", new BuiltinMethodInfo(0, true, EffesOps::readLine))
    .build()),
  CHAR_STREAM_OUT("CharStreamOut", EffesNativeType.STREAM_OUT, build()
    .put("print", new BuiltinMethodInfo(1, false, EffesOps::sout))
    .build()),
  REGEX_MATCH("RegexMatch", EffesNativeType.MATCH, build()
    .put("group", new BuiltinMethodInfo(1, true, EffesOps::matchIndexedGroup))
    .build()),
  STRING("String", EffesNativeType.STRING, build()
    .put("length", new BuiltinMethodInfo(0, true, EffesOps::stringLen))
    .build()),
  INTEGER("Integer", EffesNativeType.INTEGER, Collections.emptyMap())
  ;


  EffesBuiltinType(String typeName, EffesNativeType evmType, Map<String, MethodInfo> methods) {
    this.typeName = typeName;
    this.evmType = evmType;
    this.methods = methods;
  }

  private final String typeName;
  private final EffesNativeType evmType;
  private final Map<String, MethodInfo> methods;

  public String typeName() {
    return typeName;
  }

  public EffesNativeType evmType() {
    return evmType;
  }

  public Map<String, MethodInfo> methods() {
    return methods;
  }

  private static ImmutableMap.Builder<String, MethodInfo> build() {
    return ImmutableMap.builder();
  }

  private static class BuiltinMethodInfo extends MethodInfo {
    private final Consumer<EffesOps<?>> invocation;

    public BuiltinMethodInfo(int nDeclaredArgs, boolean hasReturnValue, Consumer<EffesOps<?>> invocation) {
      super(nDeclaredArgs, hasReturnValue);
      this.invocation = invocation;
    }

    @Override
    public void invoke(CompilerContext cc) {
      invocation.accept(cc.out);
    }
  }
}
