package com.yuvalshavit.effes2.parse;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.tree.Tree;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import com.yuvalshavit.effes2.util.StdinHelper;

public class ParseUtils {
  public static void main(String[] args) throws IOException {
    if (args.length == 0) {
      while (true) {
        String ruleName = StdinHelper.readLine("rule> ");
        if (ruleName == null) {
          break;
        }
        StringBuilder sb = new StringBuilder();
        System.out.println("enter \"~\" on a line by itself to end segment");
        StdinHelper.readUntil("> ", "~+").forEachRemaining(l -> sb.append(l).append('\n'));
        Function<EffesParser,ParserRuleContext> rule = ruleByName(ruleName);
        if (rule == null) {
          continue;
        }
        parseAndPrint(rule, CharStreams.fromString(sb.toString()));
      }
    }
    else {
      for (String arg : args) {
        parseAndPrintFile(new File(arg));
      }
    }
  }

  private static void parseAndPrintFile(File file) throws IOException {
    File[] contents = file.listFiles();
    if (contents == null) {
      String path = file.getPath();
      if (path.endsWith(".ef")) {
        System.out.println(path);
        for (int i = 0; i < path.length(); ++i) {
          System.out.print('=');
        }
        System.out.println();
        parseAndPrint(EffesParser::file, CharStreams.fromFileName(path, StandardCharsets.UTF_8));
        System.out.println();
      }
    } else {
      Arrays.sort(contents, (a, b) -> {
        boolean aIsDir = a.isDirectory();
        boolean bIsDir = b.isDirectory();
        if (aIsDir == bIsDir) {
          return a.getName().compareTo(b.getName());
        } else if (aIsDir) {
          return -1;
        } else {
          return 1;
        }
      });
      for (File content : contents) {
        parseAndPrintFile(content);
      }
    }
  }

  public static Function<EffesParser,ParserRuleContext> ruleByName(String ruleName) {
    Method ruleMethod;
    try {
      ruleMethod = EffesParser.class.getDeclaredMethod(ruleName);
    } catch (NoSuchMethodException e) {
      System.err.println("no such rule: " + ruleName);
      return null;
    }
    return parser -> {
      try {
        return (ParserRuleContext) ruleMethod.invoke(parser);
      } catch (IllegalAccessException | InvocationTargetException e) {
        System.err.println(e);
        return null;
      }
    };
  }

  public static <T extends ParserRuleContext> T parse(CharStream charStream, Function<EffesParser, T> rule, SimpleParseErrorListener errorListener) {
    ErrorListenerAdapter errorListenerAdapter = new ErrorListenerAdapter(errorListener);
    Lexer lexer = new EffesLexer(charStream);
    lexer.removeErrorListeners();
    lexer.addErrorListener(errorListenerAdapter);

    CommonTokenStream tokens = new CommonTokenStream(lexer);
    tokens.fill();
    EffesParser parser = new EffesParser(tokens);
    parser.removeErrorListeners();
    parser.addErrorListener(errorListenerAdapter);
    T ast = rule.apply(parser);
    if (ast == null) {
      errorListener.error(0, 0, "couldn't parse any input");
    } else if (!lookForTokenAtEnd(tokens.getTokens(), ast.getStop())) {
      errorListener.error(ast.getStop().getLine(), ast.getStop().getCharPositionInLine() + 1, "Expected EOF but found extra input"); // charPos is 0-indexed
    }
    return ast;
  }

  private static boolean lookForTokenAtEnd(List<Token> allTokens, Token needle) {
    // To handle things like expressions, which don't end in newlines, we'll look all the way to the last non-newline/dedent/EOF
    ListIterator<Token> lastTokensIter = allTokens.listIterator(allTokens.size());
    while (lastTokensIter.hasPrevious()) {
      Token token = lastTokensIter.previous();
      if (token == needle) {
        return true;
      }
      int tokType = token.getType();
      boolean isEndingWhitespace = tokType == EffesLexer.EOF || tokType == EffesLexer.NL || tokType == EffesLexer.DEDENT;
      if (!isEndingWhitespace) {
        break;
      }
    }
    // Either we ran out of tokens, or we found a non-dedent, non-newline, non-needle token
    return false;
  }

  public static <T extends ParserRuleContext> T parse(String input, Function<EffesParser,T> rule, SimpleParseErrorListener errorListeners) {
    return parse(CharStreams.fromString(input), rule, errorListeners);
  }

  private static void parseAndPrint(Function<EffesParser,ParserRuleContext> rule, CharStream charStream) {
    Tree tree = parse(charStream, rule, ((line, charPositionInLine, msg) -> System.err.printf("%d:%d %s%n", line, charPositionInLine, msg)));
    if (tree != null && !systemPropertySet("noAst")) {
      ToObjectPrinter printer = new ToObjectPrinter();
      printer.walk(tree);
      Object get = printer.get();
      System.out.println(prettyPrint(get));
    }
    System.out.println();
  }

  public static String prettyPrint(Object obj) {
    DumperOptions options = new DumperOptions();
    options.setTags(Collections.emptyMap());
    options.setPrettyFlow(true);
    options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    options.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);
    Yaml yaml = new Yaml(options);
    return yaml.dump(obj);
  }

  private static boolean systemPropertySet(String name) {
    String value = System.getProperty(name);
    return value != null && (value.isEmpty() || Boolean.parseBoolean(value));
  }

  private static class ErrorListenerAdapter implements ANTLRErrorListener {
    private final SimpleParseErrorListener simpleHandler;

    public ErrorListenerAdapter(SimpleParseErrorListener simpleHandler) {
      this.simpleHandler = simpleHandler;
    }

    @Override
    public void syntaxError(Recognizer<?,?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException re) {
      simpleHandler.error(line, charPositionInLine + 1, msg); // charPos is 0-indexed
    }

    @Override
    public void reportAmbiguity(Parser recognizer, DFA dfa, int startIndex, int stopIndex, boolean exact, BitSet ambigAlts, ATNConfigSet configs) {
      // nothing
    }

    @Override
    public void reportAttemptingFullContext(Parser recognizer, DFA dfa, int startIndex, int stopIndex, BitSet conflictingAlts, ATNConfigSet configs) {
      // nothing
    }

    @Override
    public void reportContextSensitivity(Parser recognizer, DFA dfa, int startIndex, int stopIndex, int prediction, ATNConfigSet configs) {
      // nothing
    }
  }
}
