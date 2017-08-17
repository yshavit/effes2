package com.yuvalshavit.effes2.compile;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TreeMap;

public class Scope {

  private Frame frame = new Frame(null);

  public Symbol lookUp(String symbolName) {
    Symbol symbol = tryLookUp(symbolName);
    if (symbol == null) {
      throw new NoSuchElementException("no variable named " + symbolName);
    }
    return symbol;
  }

  private Symbol tryLookUp(String symbolName) {
    Frame lookIn = frame;
    if (CompilerUtil.isForEnclosingScope(symbolName)) {
      lookIn = lookIn.parent;
      symbolName = CompilerUtil.plainVariableName(symbolName);
    }
    for (; lookIn != null; lookIn = lookIn.parent) {
      Symbol symbol = lookIn.symbols.get(symbolName);
      if (symbol != null) {
        return symbol;
      }
    }
    return null;
  }

  public Symbol allocateOrLookUp(String symbolName, boolean allowShadowing) {
    Symbol result = tryLookUp(symbolName);
    if (result == null) {
      if (CompilerUtil.isForEnclosingScope(symbolName)) {
        throw new IllegalArgumentException("no variable in enclosing scope: " + symbolName);
      }
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

  public void allocateLocal(String symbolName, boolean allowShadowing, Symbol symbol) {
    if (CompilerUtil.isForEnclosingScope(symbolName)) {
      throw new IllegalArgumentException("can't call allocateLocal on an enclosing-scope variable name: " + symbolName);
    }
    if (allowShadowing) {
      if (frame.symbols.containsKey(symbolName)) {
        // even with shadowing, we can't replace a variable in this same scope; can only shadow enclosing scopes
        throw new IllegalStateException("symbol name already taken: " + symbolName);
      }
    }
    else if (lookUp(symbolName) != null) {
      throw new IllegalStateException("symbol name already taken: " + symbolName);
    }
    frame.symbols.put(symbolName, symbol);
  }

  public Symbol allocateLocal(String symbolName, boolean allowShadowing) {
    Symbol symbol = new Symbol.LocalVar(frame.firstAvailableReg++, null);
    allocateLocal(symbolName, allowShadowing, symbol);
    return symbol;
  }

  private static class Frame {
    private final Map<String,Symbol> symbols = new HashMap<>();
    private final Frame parent;
    private int firstAvailableReg;

    Frame(Frame parent) {
      this.parent = parent;
      firstAvailableReg = (parent == null)
        ? 0
        : (parent.firstAvailableReg + 1);
    }

    @Override
    public String toString() {
      return new TreeMap<>(symbols).toString();
    }
  }
}
