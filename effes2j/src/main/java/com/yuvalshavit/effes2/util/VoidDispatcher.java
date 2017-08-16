package com.yuvalshavit.effes2.util;

import java.util.function.BiFunction;

public abstract class VoidDispatcher<T> extends Dispatcher<T,Void> {
  public VoidDispatcher(Class<T> parentClass, BiFunction<? super T,? super Exception,? extends RuntimeException> exceptionHandler) {
    super(parentClass, Void.class, exceptionHandler);
  }
}
