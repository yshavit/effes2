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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.reflections.Reflections;
import org.reflections.scanners.ResourcesScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.yaml.snakeyaml.Yaml;

import com.google.common.base.CaseFormat;
import com.google.common.io.Files;
import com.yuvalshavit.effes2.compile.CompilerMain;
import com.yuvalshavit.effesvm.load.EffesModule;
import com.yuvalshavit.effesvm.runtime.EffesInput;
import com.yuvalshavit.effesvm.runtime.EffesIo;
import com.yuvalshavit.effesvm.runtime.EffesOutput;
import com.yuvalshavit.effesvm.runtime.EffesRuntimeException;
import com.yuvalshavit.effesvm.runtime.EvmRunner;
import com.yuvalshavit.effesvm.runtime.coverage.CodeCoverageDebugServer;

import lombok.Getter;
import lombok.Setter;

public class Ef2Test {

  private static final String PACKAGE_NAME =Ef2Test.class.getPackage().getName();
  private static final ThreadLocal<Yaml> yaml = ThreadLocal.withInitial(Yaml::new);
  private static Map<EffesModule.Id, List<String>> efctFiles;
  private static final String CODE_COVERAGE_BASE_NAME = "efct-test-coverage";

  @BeforeClass
  public static void resetCodeCoverage() {
    String cumulativeFileName = CodeCoverageDebugServer.cumulativeFileNme(CODE_COVERAGE_BASE_NAME);
    File cumulativeFile = new File(cumulativeFileName);
    if (cumulativeFile.exists()) {
      if (!cumulativeFile.delete()) {
        throw new RuntimeException("couldn't delete " + cumulativeFileName);
      }
    }
  }

  @BeforeClass
  public static void recompile() {
    File efDir = new File("../ef");
    File efctDir = new File("../efct");
    if (!efDir.isDirectory()) {
      throw new RuntimeException("not a dir: " + efDir);
    }
    if (efctDir.exists()) {
      if (!efctDir.isDirectory()) {
        throw new RuntimeException("not a dir: " + efctDir);
      }
    } else if (!efctDir.mkdirs()) {
      throw new RuntimeException("couldn't create dir: " + efctDir);
    }
    CompilerMain.compile(efDir, efctDir);
    efctFiles = findInputFiles();
  }

  @DataProvider(name = "suite")
  public Object[][] loadTests() {
    Reflections reflections = new Reflections(
      new ConfigurationBuilder()
      .setUrls(ClasspathHelper.forPackage(PACKAGE_NAME))
      .setScanners(new ResourcesScanner())
      .filterInputsBy(r -> r != null && r.endsWith(".yaml") && !r.endsWith("_inherit.yaml")));
    return reflections.getResources(Pattern.compile(".*\\.yaml$"))
      .stream()
      .map(Ef2Test::readCase).map(c -> new Object[] {c}).toArray(Object[][]::new);
  }


  public void canReadYaml() {
    loadTests();
  }

  @Test(dataProvider = "suite")
  public void test(Case testCase) {
    MemoryIo io = new MemoryIo(Arrays.asList(testCase.stdin.split("\\n")).iterator());
    int exitCode = EvmRunner.run(
      efctFiles,
      new EffesModule.Id(testCase.mainModule),
      testCase.args,
      io,
      500,
      ctx -> Arrays.asList(
        new CodeCoverageDebugServer(ctx, CODE_COVERAGE_BASE_NAME),
        EvmRunner.getRemoteDebugger(ctx)));
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

  private static Case readCase(String fileName) {
    fileName = fileName.substring(PACKAGE_NAME.length() + 1);
    try (InputStream is = Ef2Test.class.getResourceAsStream(fileName)) {
      Case testCase = yaml.get().loadAs(is, Case.class);
      String[] fileNameSplit = fileName.split("/");
      if (fileNameSplit.length == 1) {
        throw new RuntimeException("expected a slash: " + fileName);
      }
      inheritCases(testCase, Arrays.asList(fileNameSplit).subList(0, fileNameSplit.length - 1));
      testCase.mainModule = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, fileNameSplit[0]);
      testCase.name = fileName.substring(0, fileName.length() - ".yaml".length());
      if (testCase.stdout == null) {
        throw new RuntimeException("no stdout specified for " + fileName);
      }
      return testCase;
    } catch (Exception e) {
      throw new RuntimeException("while reading " + fileName, e);
    }
  }

  private static final Map<List<String>,Optional<Case>> inheritedCases = new ConcurrentHashMap<>();
  private static void inheritCases(Case target, List<String> path) {
    if (path.isEmpty()) {
      return;
    }
    Optional<Case> inherited = inheritedCases.get(path);
    if (inherited == null) {
      String metaPath = path.stream().collect(Collectors.joining("/", "", "/_inherit.yaml"));
      InputStream is = Ef2Test.class.getResourceAsStream(metaPath);
      if (is == null) {
        inherited = Optional.empty();
      } else {
        try (InputStream closeMe = is) {
          inherited = Optional.of(yaml.get().loadAs(closeMe, Case.class));
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
      inheritedCases.put(path, inherited);
    }
    inherited.ifPresent(target::add);
    inheritCases(target, path.subList(0, path.size() - 1));
  }

  @Getter
  @Setter
  public static class Case {
    public String name;
    public String mainModule;
    public String[] args = new String[0];
    public String stdin = "";
    public String stdout;
    public String stderr = "";

    @Override
    public String toString() {
      return name;
    }

    public void add(Case other) {
      add(other, Case::getName, Case::setName, Objects::isNull);
      add(other, Case::getMainModule, Case::setMainModule, Objects::isNull);
      add(other, Case::getArgs, Case::setArgs, arr -> arr.length == 0);
      add(other, Case::getStdin, Case::setStdin, String::isEmpty);
      add(other, Case::getStdout, Case::setStdout, Objects::isNull);
      add(other, Case::getStderr, Case::setStderr, String::isEmpty);
    }

    private <T> void add(Case other, Function<Case,T> getter, BiConsumer<Case,T> setter, Predicate<T> isDefault) {
      T otherField = getter.apply(other);
      if (!isDefault.test(otherField)) {
        T myField = getter.apply(this);
        if (isDefault.test(myField)) {
          setter.accept(this, otherField);
        }
      }
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
