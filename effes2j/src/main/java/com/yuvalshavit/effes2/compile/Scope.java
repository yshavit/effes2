package com.yuvalshavit.effes2.compile;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TreeMap;

public class Scope {

  private Frame frame = new Frame(null);

  public Symbol lookUp(String symbolName) {
    for (Frame lookIn = frame; lookIn != null; lookIn = lookIn.parent) {
      Symbol symbol = lookIn.symbols.get(symbolName);
      if (symbol != null) {
        return symbol;
      }
    }
    throw new NoSuchElementException(symbolName);
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

  public void allocate(String symbolName, Symbol symbol) {
    if (frame.symbols.putIfAbsent(symbolName, symbol) != null) {
      throw new IllegalStateException("symbol name already taken: " + symbolName);
    }
  }

  private static class Frame {
    private final Map<String,Symbol> symbols = new HashMap<>();
    private final Frame parent;

    Frame(Frame parent) {
      this.parent = parent;
    }

    @Override
    public String toString() {
      return new TreeMap<>(symbols).toString();
    }
  }
}
