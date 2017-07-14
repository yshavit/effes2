package com.yuvalshavit.effes2.parse.test;

import static org.testng.Assert.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

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
    Method ruleMethod = EffesParser.class.getDeclaredMethod(fileName);
    EffesParser parser = ParseUtils.parse(testCase.input);
    Tree ast = (Tree) ruleMethod.invoke(parser);
    ParseUtils.ToObjectPrinter toObjectPrinter = new ParseUtils.ToObjectPrinter();
    toObjectPrinter.walk(ast);
    Object result = toObjectPrinter.get();

    assertEquals(ParseUtils.prettyPrint(result), ParseUtils.prettyPrint(testCase.expected));
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
