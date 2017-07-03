package com.yuvalshavit.effes2.parse;

import java.io.IOException;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.misc.Utils;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.tree.Tree;
import org.antlr.v4.runtime.tree.Trees;

public class ParseAndPrint {
  public static void main(String[] args) throws IOException {
    for (String arg : args) {
      CharStream charStream = CharStreams.fromFileName(arg);
      Lexer lexer = new EffesLexer(charStream);
      CommonTokenStream tokens = new CommonTokenStream(lexer);
      EffesParser parser = new EffesParser(tokens);
      EffesParser.FileContext effesFile = parser.file();
      new TreePrinter(parser).walk(effesFile);
    }
  }

  public static class TreePrinter {
    private final EffesParser parser;
    private int depth;
    private boolean lastWasToken = true;

    public TreePrinter(EffesParser parser) {
      this.parser = parser;
    }

    public void walk(Tree tree) {
      if (lastWasToken) {
        System.out.print(' ');
      } else {
        indent();
      }
      boolean currentIsToken = (tree instanceof TerminalNode);
      if (lastWasToken && !currentIsToken) {
        System.out.println();
        indent();
      }
      lastWasToken = currentIsToken;
      if (lastWasToken) {
        System.out.print('\'');
      }
      System.out.print(Utils.escapeWhitespace(Trees.getNodeText(tree, parser), true));
      if (lastWasToken) {
        System.out.print('\'');
      } else {
        if (tree.getChildCount() > 0) {
          System.out.print(':');
        }
        System.out.println();
        ++depth;
        for (int i = 0, len = tree.getChildCount(); i < len; ++i) {
          walk(tree.getChild(i));
        }
        --depth;
      }
    }

    private void indent() {
      for (int i = 0; i < depth; ++i) {
        System.out.print("  ");
      }
    }
  }
}
