package com.yuvalshavit.effes2.parse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Utils;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.tree.Tree;

public abstract class AbstractAstPrinter {
  private static final int INDENT_TOKEN_TYPE = EffesLexer.INDENT;
  private static final int DEDENT_TOKEN_TYPE = EffesLexer.DEDENT;
  protected final boolean includeLiterals;

  protected AbstractAstPrinter(boolean includeLiterals) {
    this.includeLiterals = includeLiterals;
  }

  public final void walk(Tree tree) {
    if (tree instanceof TerminalNode) {
      TerminalNode terminalNode = (TerminalNode) tree;
      Token symbol = terminalNode.getSymbol();
      if (symbol != null) {
        int symbolType = symbol.getType();
        if (symbolType == INDENT_TOKEN_TYPE) {
          tokenIndent();
        } else if (symbolType == DEDENT_TOKEN_TYPE) {
          tokenDedent();
        } else {
          String tokenName = EffesParser.VOCABULARY.getSymbolicName(symbolType);
          // newlines aren't literal, since they include the subsequent indentation; but we can treat them as literal, since the INDENT/DEDENT tell us the rest
          if (isNonLiteralToken(symbol)) {
            String tokenText = Utils.escapeWhitespace(symbol.getText(), true);
            token(tokenName, tokenText);
          } else if (includeLiterals) {
            token(tokenName);
          }
        }
      }
    } else if (tree instanceof RuleNode) {
      RuleNode ruleNode = (RuleNode) tree;
      List<ParseTree> children = getChildren(ruleNode);
      String ruleName = ruleNode.getClass().getSimpleName().replaceFirst("Context$", "");
      final boolean hasChildren = ! children.isEmpty();
      rule(ruleName, hasChildren);
      if (hasChildren) {
        for (ParseTree child : children) {
          walk(child);
        }
        endRuleWithChildren();
      }
    } else {
      String typeDesc = tree == null ? "null" : tree.getClass().getName();
      error("unknown token Tree type: " + typeDesc);
    }
  }

  protected List<ParseTree> getChildren(RuleNode ruleNode) {
    int childrenCountRaw = ruleNode.getChildCount();
    if (childrenCountRaw == 0) {
      return Collections.emptyList();
    }
    List<ParseTree> children = new ArrayList<>(childrenCountRaw);
    for (int i = 0; i < childrenCountRaw; ++i) {
      ParseTree child = ruleNode.getChild(i);
      if (includeLiterals || (child instanceof RuleNode) || isNonLiteralToken(child)) {
        children.add(child);
      }
    }
    // If we have any children, then that's that. But if we don't, then either (a) t
    return children;
  }

  private boolean isNonLiteralToken(ParseTree child) {
    return (child instanceof TerminalNode) && isNonLiteralToken(((TerminalNode) child).getSymbol());
  }

  private boolean isNonLiteralToken(Token token) {
    int symbolType = token.getType();
    return symbolType != EffesParser.NL && EffesParser.VOCABULARY.getLiteralName(symbolType) == null;
  }

  protected abstract void token(String tokenName);
  protected abstract void token(String tokenName, String tokenText);
  protected abstract void tokenIndent();
  protected abstract void tokenDedent();
  protected abstract void rule(String ruleName, boolean hasChildren);
  protected abstract void error(String text);
  protected abstract void endRuleWithChildren();
}
