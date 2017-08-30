package com.yuvalshavit.effes2.compile;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.yuvalshavit.effesvm.runtime.EffesOps;

public class ScratchVars {
  private final List<Consumer<EffesOps<?>>> pending = new ArrayList<>();

  public void add(Symbol scratchVar, Symbol commitVar) {
    pending.add(ops -> {
      scratchVar.push(ops);
      commitVar.store(ops);
    });
  }

  public void commit(EffesOps<?> ops) {
    pending.forEach(action -> action.accept(ops));
  }
}
