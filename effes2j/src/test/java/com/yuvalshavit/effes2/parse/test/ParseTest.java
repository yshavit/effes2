package com.yuvalshavit.effes2.parse.test;

import static org.testng.Assert.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.tree.Tree;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.yaml.snakeyaml.Yaml;

import com.google.common.io.ByteStreams;
import com.yuvalshavit.effes2.parse.EffesParser;
import com.yuvalshavit.effes2.parse.ParseUtils;

public class ParseTest {
  public static final String PARSE_TESTS = "test1";

  @DataProvider(name = PARSE_TESTS)
  public Object[][] readParseFiles() throws IOException {
    String[] files = read(".").split("\n");
    return Stream.of(files)
      .filter(f -> f.endsWith(".yaml"))
      .sorted()
      .flatMap(f ->
        StreamSupport.stream(new Yaml().loadAll(read(f)).spliterator(), false)
          .map(o -> new TestCaseRead<>(f.replaceAll("\\.yaml$", ""), o)))
      .map(read -> read.convert(TestCase.class))
      .map(o -> new Object[] {o.fileName, o.payload})
      .toArray(Object[][]::new);
  }

  @Test(dataProvider = PARSE_TESTS)
  public void parse(String fileName, TestCase testCase) throws Exception {
    Function<EffesParser,ParserRuleContext> rule = ParseUtils.ruleByName(fileName);
    Tree ast = ParseUtils.parse(testCase.input, rule, throwOnError);
    ParseUtils.ToObjectPrinter toObjectPrinter = new ParseUtils.ToObjectPrinter();
    toObjectPrinter.walk(ast);
    Object result = toObjectPrinter.get();

    assertEquals(ParseUtils.prettyPrint(result), ParseUtils.prettyPrint(testCase.expected));
  }

  private static final ANTLRErrorListener throwOnError = new ANTLRErrorListener() {
    @Override
    public void syntaxError(Recognizer<?,?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
      throw new RuntimeException("syntax error at " + line + ":" + charPositionInLine);
    }

    @Override
    public void reportAmbiguity(Parser recognizer, DFA dfa, int startIndex, int stopIndex, boolean exact, BitSet ambigAlts, ATNConfigSet configs) {
      throw new RuntimeException("ambiguity");
    }

    @Override
    public void reportAttemptingFullContext(Parser recognizer, DFA dfa, int startIndex, int stopIndex, BitSet conflictingAlts, ATNConfigSet configs) { }

    @Override
    public void reportContextSensitivity(Parser recognizer, DFA dfa, int startIndex, int stopIndex, int prediction, ATNConfigSet configs) { }
  };

  private static String read(String name) {
    URL url = ParseTest.class.getResource(name);
    if (url == null) {
      throw new RuntimeException("resource not found: " + name);
    }
    try (InputStream is = url.openStream()) {
      byte[] bytes = ByteStreams.toByteArray(is);
      return new String(bytes, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  static class TestCase {
    public String name;
    public String input;
    public Object expected;

    @Override
    public String toString() {
      return name;
    }
  }

  private static class TestCaseRead<T> {
    private final String fileName;
    private final T payload;

    public TestCaseRead(String fileName, T payload) {
      this.fileName = fileName;
      this.payload = payload;
    }

    public <R> TestCaseRead<R> convert(Class<R> to) {
      Yaml mapper = new Yaml();
      String yaml = mapper.dump(payload);
      R converted = mapper.loadAs(yaml, to);
      return new TestCaseRead<>(fileName, converted);
    }
  }
}
