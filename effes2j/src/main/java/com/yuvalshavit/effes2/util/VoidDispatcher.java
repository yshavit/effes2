package com.yuvalshavit.effes2.util;

public abstract class VoidDispatcher<T> extends Dispatcher<T,Void> {
  public VoidDispatcher(Class<T> parentClass) {
    super(parentClass, Void.class);
  }
}
