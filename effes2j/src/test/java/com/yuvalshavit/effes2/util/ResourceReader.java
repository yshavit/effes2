package com.yuvalshavit.effes2.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.yaml.snakeyaml.Yaml;

import com.google.common.io.ByteStreams;

public class ResourceReader {
  private static final String ONLY_PARAM_KEY = "test.param";
  private static final String ONLY_PARAM_VALUE = System.getProperty(ONLY_PARAM_KEY);
  private static final Predicate<TestCaseRead<?>> ONLY_PARAM_PREDICATE = tcr -> ONLY_PARAM_VALUE == null || ONLY_PARAM_VALUE.equals(tcr.payload.toString());

  private ResourceReader() {}

  public static String read(Class<?> context, String name) {
    URL url = context.getResource(name);
    if (url == null) {
      throw new RuntimeException(String.format("resource not found in %s: %s", context.getName(), name));
    }
    try (InputStream is = url.openStream()) {
      byte[] bytes = ByteStreams.toByteArray(is);
      return new String(bytes, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static Object[][] testCases(Class<?> testClass, Class<?> readAs, String[] files) {
    return Stream.of(files)
      .filter(f -> f.endsWith(".yaml"))
      .sorted()
      .flatMap(f -> StreamSupport
        .stream(new Yaml().loadAll(read(testClass, f)).spliterator(), false)
        .filter(Objects::nonNull)
        .map(o -> new TestCaseRead<>(f.replaceAll("\\.yaml$", ""), o)))
      .map(read -> read.convert(readAs))
      .filter(ONLY_PARAM_PREDICATE)
      .map(o -> new Object[] {o.fileName, o.payload})
      .toArray(Object[][]::new);
  }

  public static Object[][] testCases(Class<?> testClass, Class<?> readAs, String file, String... files) {
    return testCases(testClass, readAs, Stream.concat(Stream.of(file), Stream.of(files)).toArray(String[]::new));
  }

  public static <R> R convert(Object from, Class<R> to) {
    Yaml mapper = new Yaml();
    String yaml = mapper.dump(from);
    return mapper.loadAs(yaml, to);
  }

  public static class TestCaseRead<T> {
    public final String fileName;
    public final T payload;

    public TestCaseRead(String fileName, T payload) {
      this.fileName = fileName;
      this.payload = payload;
    }

    public <R> TestCaseRead<R> convert(Class<R> to) {
      R converted = ResourceReader.convert(payload, to);
      return new TestCaseRead<>(fileName, converted);
    }
  }
}
