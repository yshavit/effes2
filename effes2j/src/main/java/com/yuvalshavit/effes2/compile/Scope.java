package com.yuvalshavit.effes2.compile;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TreeMap;

public class Scope {

  private Frame frame = new Frame(null);

  public VarRef.LocalVar lookUp(String symbolName) {
    VarRef.LocalVar varRef = tryLookUp(symbolName);
    if (varRef == null) {
      throw new NoSuchElementException("no variable named " + symbolName);
    }
    return varRef;
  }

  public VarRef.LocalVar tryLookUp(String symbolName) {
    return tryLookUp(symbolName, true);
  }

  public VarRef.LocalVar lookUpInParentScope(String symbolName) {
    VarRef.LocalVar varRef = tryLookUp(symbolName, false);
    if (varRef == null) {
      throw new NoSuchElementException("no variable named " + symbolName);
    }
    return varRef;
  }

  private VarRef.LocalVar tryLookUp(String symbolName, boolean includeTopFrame) {
    Frame lookIn = frame;
    if (!includeTopFrame) {
      lookIn = lookIn.parent;
      if (lookIn == null) {
        throw new IllegalStateException("no parent frame to look in");
      }
    }
    for (; lookIn != null; lookIn = lookIn.parent) {
      VarRef.LocalVar varRef = lookIn.symbols.get(symbolName);
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

  public VarRef.LocalVar allocateLocal(String symbolName, boolean allowShadowing, String type) {
    VarRef.LocalVar varRef = new VarRef.LocalVar(frame.firstAvailableReg, type);
    allocateLocal(symbolName, allowShadowing, varRef);
    return varRef;
  }

  public VarRef allocateLocal(String symbolName, boolean allowShadowing) {
    return allocateLocal(symbolName, allowShadowing, (String) null);
  }

  public VarRef.LocalVar tryLookUpInTopFrame(String symbolName) {
    return frame.symbols.get(symbolName);
  }

  /**
   * Allocates an anonymous local variable.
   */
  public VarRef.LocalVar allocateAnonymous(String typeName) {
    int varIdx = frame.firstAvailableAnonymousVar++;
    String varName = "$" + varIdx; // not a legal var name in Effes, so we don't need to check availability
    return allocateLocal(varName, false, typeName);
  }

  public void replaceType(String symbolName, String type) {
    VarRef.LocalVar var = lookUp(symbolName);
    frame.symbols.put(symbolName, new VarRef.LocalVar(var.reg(), type));
  }

  private static class Frame {
    private final Map<String,VarRef.LocalVar> symbols = new HashMap<>();
    private final Frame parent;
    private int firstAvailableReg;
    private int firstAvailableAnonymousVar;

    Frame(Frame parent) {
      this.parent = parent;
      firstAvailableReg = (parent == null)
        ? 0
        : parent.firstAvailableReg;
      firstAvailableAnonymousVar = (parent == null)
        ? 0
        : parent.firstAvailableAnonymousVar;
    }

    @Override
    public String toString() {
      return new TreeMap<>(symbols).toString();
    }
  }
}
