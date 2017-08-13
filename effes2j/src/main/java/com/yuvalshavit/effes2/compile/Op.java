package com.yuvalshavit.effes2.compile;

public abstract class Op {
  private Op() {}

  // TODO or do I want to factor out an OpFactory<T> interface in effesvm_j, which EffesOps implements and then have this class implent that as well?
}
