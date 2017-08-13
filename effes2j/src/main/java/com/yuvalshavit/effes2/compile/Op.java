package com.yuvalshavit.effes2.compile;

import static java.util.Objects.requireNonNull;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;

import com.yuvalshavit.effesvm.ops.OperationFactory;
import com.yuvalshavit.effesvm.runtime.EffesOps;

public class Op {
  private final String toString;

  private Op(String opCode, List<String> arguments) {
    int len = opCode.length() + 1;
    for (String arg : arguments) {
      len += (arg.length() + 1);
    }
    StringBuilder sb = new StringBuilder(len);
    sb.append(opCode);
    for (String arg : arguments) {
      sb.append(' ').append(arg);
    }
    toString = sb.toString();
  }

  @Override
  public String toString() {
    return toString;
  }

  public static final EffesOps<Op> factory = createFactory();

  private static EffesOps<Op> createFactory() {
    @SuppressWarnings("unchecked")
    EffesOps<Op> instance = (EffesOps<Op>) Proxy.newProxyInstance(Op.class.getClassLoader(), new Class[] {EffesOps.class}, ((proxy, method, args) -> {
      OperationFactory opFactory = method.getAnnotation(OperationFactory.class);
      if (opFactory == null) {
        throw new RuntimeException("not an " + OperationFactory.class.getSimpleName());
      }
      String opName = requireNonNull(opFactory.value());
      String[] stringArgs = new String[args.length];
      for (int i = 0; i < args.length; i++) {
        stringArgs[i] = (String) requireNonNull(args[i]); // this shouldn't throw, but if it does, it's what we want it to do
      }
      return new Op(opName, Arrays.asList(stringArgs));
    }));
    return instance;
  }
}
