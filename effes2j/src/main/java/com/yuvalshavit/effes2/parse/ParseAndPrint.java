package com.yuvalshavit.effes2.parse;

import java.io.IOException;
import java.util.BitSet;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.dfa.DFA;
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
      tokens.fill();
      tokens.getTokens().forEach(tok -> System.out.printf("line %d:%d %s \"%s\"%n",
        tok.getLine(),
        tok.getCharPositionInLine(),
        EffesParser.VOCABULARY.getSymbolicName(tok.getType()),
        Utils.escapeWhitespace(tok.getText(), true)));
      EffesParser parser = new EffesParser(tokens);
      parser.removeErrorListeners();
      parser.addErrorListener(new StderrParseListener());
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
          System.out.printf(" (%s):", tree.getClass().getSimpleName().replaceAll("Context$", ""));
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

  private static class StderrParseListener implements ANTLRErrorListener {
    @Override
    public void syntaxError(Recognizer<?,?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
      System.err.printf("line %d:%d (%s) %s (%s)%n", line, charPositionInLine, offendingSymbol, msg, e);
    }

    @Override
    public void reportAmbiguity(Parser recognizer, DFA dfa, int startIndex, int stopIndex, boolean exact, BitSet ambigAlts, ATNConfigSet configs) {
      System.err.printf("ambiguity between %d and %d%n", startIndex, stopIndex);
    }

    @Override
    public void reportAttemptingFullContext(Parser recognizer, DFA dfa, int startIndex, int stopIndex, BitSet conflictingAlts, ATNConfigSet configs) {
      System.err.printf("attempting full context between %d and %d%n", startIndex, stopIndex);
    }

    @Override
    public void reportContextSensitivity(Parser recognizer, DFA dfa, int startIndex, int stopIndex, int prediction, ATNConfigSet configs) {
      System.err.printf("context sensitivity between %d and %d%n", startIndex, stopIndex);
    }
  }
}
