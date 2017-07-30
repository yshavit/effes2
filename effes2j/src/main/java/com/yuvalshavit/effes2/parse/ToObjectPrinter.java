package com.yuvalshavit.effes2.parse;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Iterables;

public class ToObjectPrinter extends AbstractAstPrinter {
  private List<Object> roots;
  private Deque<List<Object>> path;

  public ToObjectPrinter(boolean includeLiterals) {
    super(includeLiterals);
    roots = new ArrayList<>();
    path = new ArrayDeque<>();
  }

  public ToObjectPrinter() {
    this(false);
  }

  public Object get() {
    if (roots.isEmpty()) {
      return Collections.emptyList();
    } else if (roots.size() == 1) {
      return Iterables.getOnlyElement(roots);
    } else {
      return Collections.unmodifiableList(roots);
    }
  }

  @Override
  protected void tokenIndent() {
    addNode("INDENT");
  }

  @Override
  protected void tokenDedent() {
    path.pop();
  }

  @Override
  protected void token(String tokenName, String tokenText) {
    add(String.format("%s (%s)", tokenText, tokenName));
  }

  @Override
  protected void token(String tokenName) {
    add(tokenName);
  }

  @Override
  protected void error(String text) {
    add("error: " + text);
  }

  @Override
  protected void rule(String ruleName, boolean hasChildren) {
    if (hasChildren) {
      addNode(ruleName);
    } else {
      add(ruleName);
    }
  }

  @Override
  protected void endRuleWithChildren() {
    tokenDedent();
  }

  private void add(Object toAdd) {
    final List<Object> to;
    if (path.isEmpty()) {
      to = new ArrayList<>();
      path.add(to);
      roots.add(toAdd);
    } else {
      to = path.peek();
    }
    to.add(toAdd);
  }

  private void addNode(String name) {
    List<Object> children = new ArrayList<>();
    Map<?,?> node = Collections.singletonMap(name, children);
    add(node);
    path.push(children);
  }
}
