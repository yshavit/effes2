package com.yuvalshavit.effes2.compile;

import java.util.HashMap;
import java.util.Map;

import com.yuvalshavit.effesvm.runtime.EffesOps;

import lombok.Data;

public class ScratchVars {
  private final Name.Module context;
  private final Map<VarRef,Bind> scratchesToCommits = new HashMap<>();

  public ScratchVars(Name.Module context) {
    this.context = context;
  }

  public void add(String scratchVarName, VarRef scratchVar, VarRef commitVar) {
    if (scratchesToCommits.put(scratchVar, new Bind(scratchVarName, commitVar)) != null) {
      throw new RuntimeException("scratch var registered multiple times");
    }
  }

  public void commit(EffesOps<?> ops, Scope scope) {
    scratchesToCommits.forEach((scratchVar, bind) -> {
      scratchVar.push(context, ops);
      bind.commitVar.store(context, ops);
      scope.releaseLocal(bind.scratchVarName, scratchVar);
    });
  }

  @Data
  private static class Bind {
    private final String scratchVarName;
    private final VarRef commitVar;
  }
}
