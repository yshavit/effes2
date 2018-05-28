package com.yuvalshavit.effes2.compile;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.yuvalshavit.effesvm.runtime.EffesOps;

public class ScratchVars {
  private final Name.Module context;
  private final List<Consumer<EffesOps<?>>> pending = new ArrayList<>();

  public ScratchVars(Name.Module context) {
    this.context = context;
  }

  public void add(VarRef scratchVar, VarRef commitVar) {
    pending.add(ops -> {
      scratchVar.push(context, ops);
      commitVar.store(context, ops);
    });
  }

  public void commit(EffesOps<?> ops) {
    pending.forEach(action -> action.accept(ops));
  }
}
