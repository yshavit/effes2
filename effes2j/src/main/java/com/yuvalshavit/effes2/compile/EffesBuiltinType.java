package com.yuvalshavit.effes2.compile;

import java.util.Map;
import java.util.function.Consumer;

import com.google.common.collect.ImmutableMap;
import com.yuvalshavit.effesvm.runtime.EffesOps;

public enum EffesBuiltinType {
  CHAR_STREAM_IN("CharStreamIn", build()
    .put("readLine", new BuiltinMethodInfo(0, true, EffesOps::readLine))
    .build()),
  CHAR_STREAM_OUT("CharStreamOut", build()
    .put("print", new BuiltinMethodInfo(1, false, EffesOps::sout))
    .build()),
  REGEX_MATCH("RegexMatch", build()
    .put("group", new BuiltinMethodInfo(1, true, EffesOps::matchIndexedGroup))
    .build()),
  STRING("String", build()
    .put("length", new BuiltinMethodInfo(0, true, EffesOps::stringLen))
    .build()),
  ;

  EffesBuiltinType(String typeName, Map<String, MethodInfo> methods) {
    this.typeName = typeName;
    this.methods = methods;
  }

  private final String typeName;
  private final Map<String, MethodInfo> methods;

  public String typeName() {
    return typeName;
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
