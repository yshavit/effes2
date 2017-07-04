package com.yuvalshavit.effes2.parse;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.BitSet;
import java.util.function.Function;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.misc.Utils;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.tree.Tree;
import org.antlr.v4.runtime.tree.Trees;

import com.yuvalshavit.effes2.util.StdinHelper;

public class ParseAndPrint {
  public static void main(String[] args) throws IOException {
    if (args.length == 0) {
      while (true) {
        String ruleName = StdinHelper.readLine("rule> ");
        if (ruleName == null) {
          break;
        }
        Method ruleMethod;
        try {
          ruleMethod = EffesParser.class.getDeclaredMethod(ruleName);
        } catch (NoSuchMethodException e) {
          System.err.println("no such rule: " + ruleName);
          continue;
        }
        StringBuilder sb = new StringBuilder();
        System.out.println("enter \"~~~\" on a line by itself to end segment");
        StdinHelper.readUntil("> ", "~~~").forEachRemaining(l -> sb.append(l).append('\n'));
        Function<EffesParser,Tree> rule = parser -> {
          try {
            return (Tree) ruleMethod.invoke(parser);
          } catch (IllegalAccessException | InvocationTargetException e) {
            System.err.println(e);
            return null;
          }
        };
        parseAndPrint(rule, CharStreams.fromString(sb.toString()));
      }
    }
    else {
      for (String arg : args) {
        parseAndPrint(EffesParser::file, CharStreams.fromFileName(arg));
      }
    }
  }

  private static void parseAndPrint(Function<EffesParser,Tree> rule, CharStream charStream) {
    final boolean verbose = false;
    Lexer lexer = new EffesLexer(charStream);
    CommonTokenStream tokens = new CommonTokenStream(lexer);
    tokens.fill();
    if (verbose) {
      tokens.getTokens().forEach(tok -> System.out.printf(
        "line %d:%d %s \"%s\"%n",
        tok.getLine(),
        tok.getCharPositionInLine(),
        EffesParser.VOCABULARY.getSymbolicName(tok.getType()),
        Utils.escapeWhitespace(tok.getText(), true)));
    }
    EffesParser parser = new EffesParser(tokens);
    parser.removeErrorListeners();
    parser.addErrorListener(new StderrParseListener(verbose));
    Tree tree = rule.apply(parser);
    if (tree != null) {
      new TreePrinter(parser).walk(tree);
    }
    System.out.println();
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
        System.out.print(Utils.escapeWhitespace(Trees.getNodeText(tree, parser), true));
        System.out.print('\'');
      } else {
        System.out.print(tree.getClass().getSimpleName().replaceAll("Context$", ""));
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

  private static class StderrParseListener implements ANTLRErrorListener {
    private final boolean verbose;

    public StderrParseListener(boolean verbose) {
      this.verbose = verbose;
    }

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
      if (verbose) {
        System.err.printf("attempting full context between %d and %d%n", startIndex, stopIndex);
      }
    }

    @Override
    public void reportContextSensitivity(Parser recognizer, DFA dfa, int startIndex, int stopIndex, int prediction, ATNConfigSet configs) {
      if (verbose) {
        System.err.printf("context sensitivity between %d and %d%n", startIndex, stopIndex);
      }
    }
  }
}
