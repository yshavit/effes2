package com.yuvalshavit.effes2.compile;

import static java.util.Objects.requireNonNull;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import org.antlr.v4.runtime.Token;

import com.yuvalshavit.effesvm.ops.OperationFactory;
import com.yuvalshavit.effesvm.runtime.EffesOps;

public class Op {
  private final Token token;
  private final String toString;

  private Op(Token token, String opCode, List<String> arguments) {
    this.token = token;
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

  public static EffesOps<Token> factory(Consumer<? super Op> handler) {
    requireNonNull(handler);
    @SuppressWarnings({"unchecked", "rawtypes"})
    EffesOps<Token> instance = (EffesOps<Token>) Proxy.newProxyInstance(Op.class.getClassLoader(), new Class[] {EffesOps.class}, ((proxy, method, args) -> {
      if (method.getDeclaringClass() == Object.class && method.getName().equals("toString")) {
        return handler.toString();
      }
      OperationFactory opFactory = method.getAnnotation(OperationFactory.class);
      if (opFactory == null) {
        throw new RuntimeException("not an " + OperationFactory.class.getSimpleName());
      }
      String opName = requireNonNull(opFactory.value());
      Token token;
      List<String> opArgs;
      if (args == null) {
        throw new IllegalArgumentException("no args provided");
      } else {
        token = (Token) args[0];
        String[] stringArgs = new String[args.length - 1];
        for (int i = 1; i < args.length; i++) {
          stringArgs[i - 1] = (String) requireNonNull(args[i]); // this shouldn't throw, but if it does, it's what we want it to do
        }
        opArgs = Arrays.asList(stringArgs);
      }
      Op op = new Op(token, opName, opArgs);
      handler.accept(op);
      return null;
    }));
    return instance;
  }
}
