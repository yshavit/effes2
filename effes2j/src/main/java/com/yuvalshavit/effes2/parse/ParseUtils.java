package com.yuvalshavit.effes2.parse;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.tree.Tree;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import com.google.common.collect.Iterables;
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
        parseAndPrint(EffesParser::file, CharStreams.fromFileName(arg));
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

  public static <T extends ParserRuleContext> T parse(CharStream charStream, Function<EffesParser, T> rule, ANTLRErrorListener... errorListeners) {
    Lexer lexer = new EffesLexer(charStream);
    lexer.removeErrorListeners();
    for (ANTLRErrorListener errorListener : errorListeners) {
      lexer.addErrorListener(errorListener);
    }
    CommonTokenStream tokens = new CommonTokenStream(lexer);
    tokens.fill();
    EffesParser parser = new EffesParser(tokens);
    parser.removeErrorListeners();
    for (ANTLRErrorListener errorListener : errorListeners) {
      parser.addErrorListener(errorListener);
    }
    T ast = rule.apply(parser);
    if (ast == null) {
      throw new RuntimeException("couldn't parse input");
    }
    lookForTokenAtEnd(tokens.getTokens(), ast.getStop());
    return ast;
  }

  private static void lookForTokenAtEnd(List<Token> allTokens, Token needle) {
    // To handle things like expressions, which don't end in newlines, we'll look all the way to the last non-newline/dedent/EOF
    ListIterator<Token> lastTokensIter = allTokens.listIterator(allTokens.size());
    while (lastTokensIter.hasPrevious()) {
      Token token = lastTokensIter.previous();
      if (token == needle) {
        return;
      }
      int tokType = token.getType();
      boolean isEndingWhitespace = tokType == EffesLexer.EOF || tokType == EffesLexer.NL || tokType == EffesLexer.DEDENT;
      if (!isEndingWhitespace) {
        break;
      }
    }
    // Either we ran out of tokens, or we found a non-dedent, non-newline, non-needle token
    throw new RuntimeException("not all input consumed");
  }

  public static <T extends ParserRuleContext> T parse(String input, Function<EffesParser,T> rule, ANTLRErrorListener... errorListeners) {
    return parse(CharStreams.fromString(input), rule, errorListeners);
  }

  private static void parseAndPrint(Function<EffesParser,ParserRuleContext> rule, CharStream charStream) {
    final boolean verbose = false;
    Tree tree = parse(charStream, rule, new StderrParseListener(verbose));

    if (tree != null) {
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

  public static class ToObjectPrinter extends AbstractAstPrinter {
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
    protected void indent() {
      addNode("INDENT");
    }

    @Override
    protected void dedent() {
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
      dedent();
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
      String exactDesc = exact ? "exact" : "inexact";
      System.err.printf("%s ambiguity at %s (starting from %s): %s%n", exactDesc, token(recognizer, stopIndex), token(recognizer, startIndex), ambigAlts);
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

    private String token(Parser recognizer, int idx) {
      Token token = recognizer.getTokenStream().get(idx);
      if (token == null) {
        return "<unknown token>";
      }
      return String.format("%s at %d:%d", EffesParser.VOCABULARY.getDisplayName(token.getType()), token.getLine(), token.getCharPositionInLine());
    }

  }
}
