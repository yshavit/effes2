package com.yuvalshavit.effes2.compile;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TreeMap;

public class Scope {

  private Frame frame = new Frame(null);

  public VarRef lookUp(String symbolName) {
    VarRef varRef = tryLookUp(symbolName, true);
    if (varRef == null) {
      throw new NoSuchElementException("no variable named " + symbolName);
    }
    return varRef;
  }

  public VarRef lookUpInParentScope(String symbolName) {
    VarRef varRef = tryLookUp(symbolName, false);
    if (varRef == null) {
      throw new NoSuchElementException("no variable named " + symbolName);
    }
    return varRef;
  }

  private VarRef tryLookUp(String symbolName, boolean includeTopFrame) {
    Frame lookIn = frame;
    if (!includeTopFrame) {
      lookIn = lookIn.parent;
      if (lookIn == null) {
        throw new IllegalStateException("no parent frame to look in");
      }
    }
    for (; lookIn != null; lookIn = lookIn.parent) {
      VarRef varRef = lookIn.symbols.get(symbolName);
      if (varRef != null) {
        return varRef;
      }
    }
    return null;
  }

  public VarRef allocateOrLookUp(String symbolName, boolean allowShadowing) {
    VarRef result = tryLookUp(symbolName, true);
    if (result == null) {
      result = allocateLocal(symbolName, allowShadowing);
    }
    return result;
  }

  public void push() {
    frame = new Frame(frame);
  }

  public void pop() {
    if (frame.parent == null) {
      throw new IllegalStateException("can't pop last frame");
    }
    frame = frame.parent;
  }

  public void inNewScope(Runnable action) {
    push();
    try {
      action.run();
    } finally {
      pop();
    }
  }

  public int depth() {
    int depth = 0;
    for (Frame f = frame; f != null; f = f.parent) {
      ++depth;
    }
    return depth;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder(depth() * 50); // rough guess
    sb.append('[');
    for (Frame f = frame; f != null; f = f.parent) {
      sb.append('(').append(f).append(')');
      if (f.parent != null) {
        sb.append(", ");
      }
    }
    sb.append(']');
    return sb.toString();
  }

  public void allocateLocal(String symbolName, boolean allowShadowing, VarRef.LocalVar varRef) {
    if (allowShadowing) {
      if (frame.symbols.containsKey(symbolName)) {
        // even with shadowing, we can't replace a variable in this same scope; can only shadow enclosing scopes
        throw new IllegalStateException("symbol name already taken: " + symbolName);
      }
    }
    else if (tryLookUp(symbolName, false) != null) {
      throw new IllegalStateException("symbol name already taken: " + symbolName);
    }
    frame.symbols.put(symbolName, varRef);
    frame.firstAvailableReg = Math.max(varRef.reg(), frame.firstAvailableReg) + 1;
  }

  public VarRef allocateLocal(String symbolName, boolean allowShadowing, String type) {
    VarRef.LocalVar varRef = new VarRef.LocalVar(frame.firstAvailableReg, type);
    allocateLocal(symbolName, allowShadowing, varRef);
    return varRef;
  }

  public VarRef allocateLocal(String symbolName, boolean allowShadowing) {
    return allocateLocal(symbolName, allowShadowing, (String) null);
  }

  private static class Frame {
    private final Map<String,VarRef> symbols = new HashMap<>();
    private final Frame parent;
    private int firstAvailableReg;

    Frame(Frame parent) {
      this.parent = parent;
      firstAvailableReg = (parent == null)
        ? 0
        : parent.firstAvailableReg;
    }

    @Override
    public String toString() {
      return new TreeMap<>(symbols).toString();
    }
  }
}
