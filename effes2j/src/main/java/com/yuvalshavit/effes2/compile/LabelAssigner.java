package com.yuvalshavit.effes2.compile;

import java.util.HashSet;
import java.util.Set;

import com.yuvalshavit.effesvm.runtime.EffesOps;

public class LabelAssigner {
  private final Set<String> names = new HashSet<>();
  private final EffesOps<Void> out;

  public String allocate(String description) {
    throw new UnsupportedOperationException();
  }

  public void place(String name) {
    if (!names.remove(name)) {
      throw new IllegalArgumentException("unknown (or already-placed) label: " + name);
    }
    out.label(name);
  }
}
