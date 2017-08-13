package com.yuvalshavit.effes2.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.yaml.snakeyaml.Yaml;

import com.google.common.io.ByteStreams;
import com.yuvalshavit.effes2.parse.test.ParseTest;

public class ResourceReader {
  private ResourceReader() {}

  public static String read(Class<?> context, String name) {
    URL url = context.getResource(name);
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

  public static Object[][] testCases(Class<?> testClass, Class<?> readAs) {
    String[] files = read(testClass, ".").split("\n");
    return Stream.of(files)
      .filter(f -> f.endsWith(".yaml"))
      .sorted()
      .flatMap(f -> StreamSupport.stream(new Yaml().loadAll(read(testClass, f)).spliterator(), false)
        .map(o -> new TestCaseRead<>(f.replaceAll("\\.yaml$", ""), o)))
      .map(read -> read.convert(readAs))
      .map(o -> new Object[] {o.fileName, o.payload})
      .toArray(Object[][]::new);
  }

  public static class TestCaseRead<T> {
    public final String fileName;
    public final T payload;

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
