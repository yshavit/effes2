package com.yuvalshavit.effes2.compile;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

public class Name {
  @Data
  public static class Module {
    public static final Module BUILT_IN = new Module("Builtin");
    private final String name;
  }

  @Data
  public static class UnqualifiedType {
    private final String name;
  }

  @Data
  @AllArgsConstructor(access = AccessLevel.PRIVATE)
  public static class QualifiedType {
    private static UnqualifiedType UNKNOWN = new UnqualifiedType("<unknown>");
    private static final UnqualifiedType STATIC = new UnqualifiedType("<static>");

    private final Module module;
    private final UnqualifiedType unqualifiedType;
    private final String evmDescriptor;

    public QualifiedType(Module module, UnqualifiedType unqualifiedType) {
      this(module, unqualifiedType, null);
    }

    public static QualifiedType forStaticCalls(Module module) {
      return new QualifiedType(module, STATIC);
    }

    public static QualifiedType forBuiltin(EffesBuiltinType type) {
      return new QualifiedType(Module.BUILT_IN, new UnqualifiedType(type.typeName()), type.evmType().getEvmType());
    }

    public String evmDescriptor(Module context) {
      if (evmDescriptor != null) {
        return evmDescriptor;
      }

      String moduleDescriptor = module == context
        ? ""
        : module.getName();
      if (unqualifiedType == STATIC) {
        return moduleDescriptor + ":";
      } else {
        return String.format("%s:%s", moduleDescriptor, unqualifiedType.getName());
      }
    }

    @Override
    public String toString() {
      return "qualified type" + evmDescriptor(null);
    }
  }
}
