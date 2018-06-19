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
  private static final Predicate<Object> ONLY_PARAM_PREDICATE = tcr -> ONLY_PARAM_VALUE == null || ONLY_PARAM_VALUE.equals(tcr.toString());

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

  public static Object[][] testCases(Class<?> testClass, Class<? extends FileBound> readAs, String[] files) {
    return Stream.of(files)
      .filter(f -> f.endsWith(".yaml"))
      .sorted()
      .flatMap(f -> StreamSupport
        .stream(new Yaml().loadAll(read(testClass, f)).spliterator(), false)
        .filter(Objects::nonNull)
        .map(o -> {
          String fileName = f.replaceAll("\\.yaml$", "");
          FileBound converted = ResourceReader.convert(o, readAs);
          converted.setFile(fileName);
          return converted;
        }))
      .filter(ONLY_PARAM_PREDICATE)
      .map(o -> new Object[] {o})
      .toArray(Object[][]::new);
  }

  public static Object[][] testCases(Class<?> testClass, Class<? extends FileBound> readAs, String file, String... files) {
    return testCases(testClass, readAs, Stream.concat(Stream.of(file), Stream.of(files)).toArray(String[]::new));
  }

  public static <R> R convert(Object from, Class<R> to) {
    Yaml mapper = new Yaml();
    String yaml = mapper.dump(from);
    return mapper.loadAs(yaml, to);
  }
}
