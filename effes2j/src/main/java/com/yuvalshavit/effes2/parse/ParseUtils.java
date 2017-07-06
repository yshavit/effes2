package com.yuvalshavit.effes2.parse;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.misc.Utils;
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
        Method ruleMethod;
        try {
          ruleMethod = EffesParser.class.getDeclaredMethod(ruleName);
        } catch (NoSuchMethodException e) {
          System.err.println("no such rule: " + ruleName);
          continue;
        }
        StringBuilder sb = new StringBuilder();
        System.out.println("enter \"~\" on a line by itself to end segment");
        StdinHelper.readUntil("> ", "~+").forEachRemaining(l -> sb.append(l).append('\n'));
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
      ToObjectPrinter printer = new ToObjectPrinter();
      printer.walk(tree);
      Object get = printer.get();
      DumperOptions options = new DumperOptions();
      options.setTags(Collections.emptyMap());
      options.setPrettyFlow(true);
      options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
      options.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);
      Yaml yaml = new Yaml(options);
      System.out.println(yaml.dump(get));
    }
    System.out.println();
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
      addNode("indent");
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
