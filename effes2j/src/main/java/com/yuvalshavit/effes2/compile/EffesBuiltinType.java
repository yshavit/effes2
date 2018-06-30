package com.yuvalshavit.effes2.compile;

import java.util.Collections;
import java.util.Map;
import java.util.function.BiConsumer;

import org.antlr.v4.runtime.Token;

import com.google.common.collect.ImmutableMap;
import com.yuvalshavit.effesvm.runtime.EffesNativeType;
import com.yuvalshavit.effesvm.runtime.EffesOps;

public enum EffesBuiltinType {
  ARRAY("Array", EffesNativeType.ARRAY,
    build()
      .put("get", new BuiltinMethodInfo(1, true, EffesOps::arrayGet))
      .put("length", new BuiltinMethodInfo(0, true, EffesOps::arrayLen))
      .build(),
    Collections.emptyMap()),
  CHAR_STREAM_IN("CharStreamIn", EffesNativeType.STREAM_IN,
    build()
      .put("readLine", new BuiltinMethodInfo(0, true, EffesOps::readLine))
      .build(),
    Collections.emptyMap()),
  CHAR_STREAM_OUT("CharStreamOut", EffesNativeType.STREAM_OUT,
    build()
      .put("print", new BuiltinMethodInfo(1, false, EffesOps::sout))
      .build(),
    build()
      .put("debug", new BuiltinMethodInfo(1, false, (ops, tok) -> {
        ops.nativeToString(tok);
        ops.sout(tok);
        ops.strPush(tok, "\\n");
        ops.sout(tok);
      }))
      .put("debugPretty", new BuiltinMethodInfo(1, false, (ops, tok)-> {
        ops.nativeToStringPretty(tok);
        ops.sout(tok);
        ops.strPush(tok, "\\n");
        ops.sout(tok);
      }))
      .build()),
  REGEX_MATCH("RegexMatch", EffesNativeType.MATCH,
    build()
      .put("group", new BuiltinMethodInfo(1, true, EffesOps::matchIndexedGroup))
      .put("tail", new BuiltinMethodInfo(0, true, EffesOps::matchTail))
      .build(),
    Collections.emptyMap()),
  STRING("String", EffesNativeType.STRING,
    build()
      .put("length", new BuiltinMethodInfo(0, true, EffesOps::stringLen))
      .build(),
    Collections.emptyMap()),
  STRING_BUILDER("StringBuilder", EffesNativeType.STRING_BUILDER,
    build()
      .put("append", new BuiltinMethodInfo(1, false, EffesOps::stringBuilderAdd))
      .put("get", new BuiltinMethodInfo(0, true, EffesOps::stringBuilderGet))
      .build(),
    build()
      .put("create", new BuiltinMethodInfo(0, true, EffesOps::sbld))
      .build()),
  TRUE("True", EffesNativeType.TRUE, Collections.emptyMap(), Collections.emptyMap(), (ops, tok) -> ops.bool(tok, "True")),
  FALSE("False", EffesNativeType.FALSE, Collections.emptyMap(), Collections.emptyMap(), (ops, tok) -> ops.bool(tok, "False")),
  INTEGER("Integer", EffesNativeType.INTEGER, Collections.emptyMap(), Collections.emptyMap());

  EffesBuiltinType(
    String typeName,
    EffesNativeType evmType,
    Map<String, MethodInfo> instanceMethods,
    Map<String, MethodInfo> staticMethods,
    BiConsumer<EffesOps<Token>,Token> constructor)
  {
    this.typeName = typeName;
    this.evmType = evmType;
    this.instanceMethods = instanceMethods;
    this.staticMethods = staticMethods;
    this.constructor = constructor;
  }

  EffesBuiltinType(String typeName, EffesNativeType evmType, Map<String, MethodInfo> instanceMethods, Map<String, MethodInfo> staticMethods) {
    this(typeName, evmType, instanceMethods, staticMethods, null);
  }

  private final String typeName;
  private final EffesNativeType evmType;
  private final Map<String, MethodInfo> instanceMethods;
  private final Map<String, MethodInfo> staticMethods;
  private final BiConsumer<EffesOps<Token>,Token> constructor;

  public String typeName() {
    return typeName;
  }

  public BiConsumer<EffesOps<Token>,Token> constructor() {
    return constructor;
  }

  public EffesNativeType evmType() {
    return evmType;
  }

  public Map<String, MethodInfo> instanceMethods() {
    return instanceMethods;
  }

  public Map<String, MethodInfo> staticMethods() {
    return staticMethods;
  }

  private static ImmutableMap.Builder<String, MethodInfo> build() {
    return ImmutableMap.builder();
  }

  private static class BuiltinMethodInfo extends MethodInfo {
    private final BiConsumer<EffesOps<Token>,Token> invocation;

    public BuiltinMethodInfo(int nDeclaredArgs, boolean hasReturnValue, BiConsumer<EffesOps<Token>,Token> invocation) {
      super(nDeclaredArgs, hasReturnValue);
      this.invocation = invocation;
    }

    @Override
    public void invoke(Token token, CompilerContext cc) {
      invocation.accept(cc.out, token);
    }
  }
}
