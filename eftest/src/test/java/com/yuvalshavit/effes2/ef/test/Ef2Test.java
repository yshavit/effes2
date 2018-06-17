package com.yuvalshavit.effes2.ef.test;

import static org.testng.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.reflections.Reflections;
import org.reflections.scanners.ResourcesScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.yaml.snakeyaml.Yaml;

import com.google.common.base.CaseFormat;
import com.google.common.io.Files;
import com.yuvalshavit.effesvm.load.EffesModule;
import com.yuvalshavit.effesvm.runtime.DebugServer;
import com.yuvalshavit.effesvm.runtime.EffesInput;
import com.yuvalshavit.effesvm.runtime.EffesIo;
import com.yuvalshavit.effesvm.runtime.EffesOutput;
import com.yuvalshavit.effesvm.runtime.EffesRuntimeException;
import com.yuvalshavit.effesvm.runtime.EvmRunner;

public class Ef2Test {

  private static final String PACKAGE_NAME =Ef2Test.class.getPackage().getName();
  private static final ThreadLocal<Yaml> yaml = ThreadLocal.withInitial(Yaml::new);
  public static final Map<EffesModule.Id, List<String>> efctFiles = findInputFiles();

  @DataProvider(name = "suite")
  public Object[][] loadTests() {
    Reflections reflections = new Reflections(
      new ConfigurationBuilder()
      .setUrls(ClasspathHelper.forPackage(PACKAGE_NAME))
      .setScanners(new ResourcesScanner())
      .filterInputsBy(r -> r != null && r.endsWith(".yaml")));
    return reflections.getResources(Pattern.compile(".*\\.yaml$"))
      .stream()
      .map(Ef2Test::readCases).map(c -> new Object[] {c}).toArray(Object[][]::new);
  }

  @Test
  public void canReadYaml() {
    loadTests();
  }

  @Test(dataProvider = "suite")
  public void test(Case testCase) {
    MemoryIo io = new MemoryIo(Arrays.asList(testCase.stdin.split("\\n")).iterator());
    int exitCode = EvmRunner.run(
      efctFiles,
      new EffesModule.Id(testCase.mainModule),
      new String[0],
      io,
      500,
      x -> DebugServer.noop);
    assertEquals(exitCode, 0, "exit code");
    assertEquals(io.stdout.toString(), testCase.stdout, "stdout");
    assertEquals(io.stderr.toString(), testCase.stderr, "stdout");
  }

  private static Map<EffesModule.Id, List<String>> findInputFiles() {
    File efctDir = new File("../efct");
    File[] efctFiles = efctDir.listFiles((File file) -> file.getName().endsWith(".efct"));
    if (efctFiles == null) {
      throw new NoSuchElementException("not a dir: " + efctDir.getAbsolutePath());
    }
    return Stream.of(efctFiles).collect(Collectors.toMap(
      file -> new EffesModule.Id(file.getName().substring(0, file.getName().length() - ".efct".length())),
      file -> {
        try {
          return Files.readLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    ));
  }

  private static Case readCases(String fileName) {
    fileName = fileName.substring(PACKAGE_NAME.length() + 1);
    try (InputStream is = Ef2Test.class.getResourceAsStream(fileName)) {
      Case testCase = yaml.get().loadAs(is, Case.class);
      String[] fileNameSplit = fileName.split("/", 2);
      if (fileNameSplit.length != 2) {
        throw new RuntimeException("expected a slash: " + fileName);
      }
      testCase.mainModule = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, fileNameSplit[0]);
      testCase.name = fileName.substring(0, fileName.length() - ".yaml".length());
      if (testCase.stdout == null) {
        throw new RuntimeException("no stdout specified for " + fileName);
      }
      return testCase;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static class Case {
    public String name;
    public String mainModule;
    public String stdin = "";
    public String stdout;
    public String stderr = "";

    @Override
    public String toString() {
      return name;
    }
  }

  private static class MemoryIo implements EffesIo {
    private final EffesInput stdin;
    private final StringBuilder stdout;
    private final StringBuilder stderr;

    public MemoryIo(Iterator<String> stdin) {
      this.stdin = () -> stdin.hasNext() ? stdin.next() : null;
      stdout = new StringBuilder();
      stderr = new StringBuilder();
    }

    @Override
    public EffesInput in() {
      return stdin;
    }

    @Override
    public EffesOutput out() {
      return stdout::append;
    }

    @Override
    public EffesOutput err() {
      return stderr::append;
    }

    @Override
    public InputStream readFile(String path) {
      throw new EffesRuntimeException("no such file: " + path);
    }

    @Override
    public OutputStream writeFile(String path) {
      throw new UnsupportedOperationException();
    }
  }
}
