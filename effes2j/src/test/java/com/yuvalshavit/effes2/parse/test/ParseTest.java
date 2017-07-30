package com.yuvalshavit.effes2.parse.test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.Tree;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.yaml.snakeyaml.Yaml;

import com.google.common.io.ByteStreams;
import com.yuvalshavit.effes2.parse.EffesParser;
import com.yuvalshavit.effes2.parse.ParseUtils;
import com.yuvalshavit.effes2.parse.ToObjectPrinter;

public class ParseTest {
  public static final String PARSE_TESTS = "test1";

  @Test
  public void dataProviderWorks() throws IOException {
    assertNotNull(readParseFiles());
  }

  @DataProvider(name = PARSE_TESTS)
  public static Object[][] readParseFiles() throws IOException {
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
    StringBuilder errsSb = new StringBuilder();
    Tree ast = ParseUtils.parse(
      testCase.input,
      rule,
      ((line, lineOffset, msg) -> errsSb.append(String.format("%s error at <%s> %d:%d: %s%n", getClass().getSimpleName(), testCase, line, lineOffset, msg))));
    ToObjectPrinter toObjectPrinter = new ToObjectPrinter();
    toObjectPrinter.walk(ast);
    Object result = toObjectPrinter.get();

    String errMessages = errsSb.toString();
    if (!errMessages.isEmpty()) {
      System.err.println(errMessages); // we'll assertEquals on them later, but for now print them in case the prettyPrint fails first.
    }
    assertEquals(ParseUtils.prettyPrint(result), ParseUtils.prettyPrint(testCase.expected));
    assertEquals(errMessages, "", "error messages");
  }

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
      return name == null ? input : name;
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
