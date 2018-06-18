package com.yuvalshavit.effes2.compile;

import java.util.function.Consumer;

import com.yuvalshavit.effesvm.runtime.EffesOps;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

public class Name {

  public interface EvmScope {
    String evmDescriptor(Module context);
    Module getModule();
  }

  @Data
  public static class Module implements EvmScope {
    public static final Module BUILT_IN = new Module("Builtin");
    private final String name;

    @Override
    public String evmDescriptor(Module context) {
      String desc = this == context ? "" : name;
      return desc + ":";
    }

    @Override
    public Module getModule() {
      return this;
    }
  }

  @Data
  public static class UnqualifiedType {
    private final String name;
  }

  @Data
  @AllArgsConstructor(access = AccessLevel.PRIVATE)
  public static class QualifiedType implements EvmScope {
    private static UnqualifiedType UNKNOWN = new UnqualifiedType("<unknown>");

    private final Module module;
    private final UnqualifiedType unqualifiedType;
    private final String evmDescriptor;
    private final Consumer<EffesOps<?>> constructor;

    public QualifiedType(Module module, UnqualifiedType unqualifiedType) {
      this(module, unqualifiedType, null, null);
    }

    public static QualifiedType forBuiltin(EffesBuiltinType type) {
      return new QualifiedType(Module.BUILT_IN, new UnqualifiedType(type.typeName()), type.evmType().getEvmType(), type.constructor());
    }

    public void instantiate(Module context, EffesOps<?> out) {
      if (constructor == null) {
        out.call(evmDescriptor(context), getUnqualifiedType().getName());
      } else {
        constructor.accept(out);
      }
    }

    @Override
    public String evmDescriptor(Module context) {
      if (evmDescriptor != null) {
        return evmDescriptor;
      } else {
        return module.evmDescriptor(context) + unqualifiedType.getName();
      }
    }

    @Override
    public String toString() {
      return "qualified type " + evmDescriptor(null);
    }
  }
}
