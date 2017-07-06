package com.yuvalshavit.effes2.parse;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Utils;
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
          indent();
        } else if (symbolType == DEDENT_TOKEN_TYPE) {
          dedent();
        } else {
          String tokenName = EffesParser.VOCABULARY.getSymbolicName(symbolType);
          // newlines aren't literal, since they include the subsequent indentation; but we can treat them as literal, since the INDENT/DEDENT tell us the rest
          if (symbolType != EffesParser.NL && EffesParser.VOCABULARY.getLiteralName(symbolType) == null) {
            String tokenText = Utils.escapeWhitespace(symbol.getText(), true);
            token(tokenName, tokenText);
          } else if (includeLiterals) {
            token(tokenName);
          }
        }
      }
    } else if (tree instanceof RuleNode) {
      RuleNode ruleNode = (RuleNode) tree;
      int childCount = ruleNode.getChildCount();
      String ruleName = ruleNode.getClass().getSimpleName().replaceFirst("Context$", "");
      boolean hasChildren = childCount > 0;
      rule(ruleName, hasChildren);
      for (int i = 0; i < childCount; ++i) {
        walk(ruleNode.getChild(i));
      }
      if (hasChildren) {
        endRuleWithChildren();
      }

    } else {
      String typeDesc = tree == null ? "null" : tree.getClass().getName();
      error("unknown token Tree type: " + typeDesc);
    }
  }

  protected abstract void indent();
  protected abstract void dedent();
  protected abstract void token(String tokenName);
  protected abstract void token(String tokenName, String tokenText);
  protected abstract void error(String text);
  protected abstract void rule(String ruleName, boolean hasChildren);
  protected abstract void endRuleWithChildren();
}
