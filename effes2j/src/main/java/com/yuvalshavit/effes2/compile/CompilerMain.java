package com.yuvalshavit.effes2.compile;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.io.Files;
import com.yuvalshavit.effes2.util.MiscUtil;

public class CompilerMain {
  public static void main(String[] args) {
    ErrorHandler errorHandler = new ErrorHandler();

    // Create the compilation units
    File sourceDir = getDir("source", "", false);
    File outputDir = getDir("output", sourceDir.getAbsolutePath(), true);
    if (args.length == 0) {
      File[] sourceFiles = sourceDir.listFiles(f -> f.isFile() && f.getName().endsWith(".ef"));
      if (sourceFiles == null) {
        System.err.printf("couldn't read files in %s%n", sourceDir);
        System.exit(1);
      }
      args = Stream.of(sourceFiles)
        .map(File::getName)
        .toArray(String[]::new);
    }

    Pattern fileRegex = Pattern.compile("(.+)\\.ef$");
    Collection<Compiler.CompileUnit> compileUnits = Stream.of(args)
      .map(fileName -> {
        File file = new File(sourceDir, fileName);
        if (!file.exists()) {
          errorHandler.accept("no such file: " + file);
          return null;
        }
        Matcher matcher = fileRegex.matcher(fileName);
        if (!matcher.matches()) {
          errorHandler.accept("not a .ef file: " + file);
        }
        return new Compiler.CompileUnit(matcher.group(1), file.getPath(), () -> {
          try {
            return Files.toString(file, StandardCharsets.UTF_8);
          } catch (IOException e) {
            errorHandler.accept(MiscUtil.toStringWithStackTrace(e));
            return null;
          }
        });
      })
      .filter(Objects::nonNull)
      .collect(Collectors.toList());

    // Create the writers
    Function<String,Writer> writers = moduleName -> {
      File efct = new File(outputDir, moduleName + ".efct");
      try {
        return new FileWriter(efct);
      } catch (IOException e) {
        errorHandler.accept(MiscUtil.toStringWithStackTrace(e));
        return null;
      }
    };

    // Compile and check no errors
    try {
      Compiler.compile(compileUnits, writers, errorHandler);
    } catch (CompilationException e) {
      final Throwable t;
      if (e.getCause() == null) {
        t = e;
      } else {
        System.err.print(e.getLocationMessage());
        System.err.print(": ");
        t = e.getCause();
      }
      t.printStackTrace();
    }
    if (errorHandler.sawAny) {
      System.exit(1);
    }
  }

  private static File getDir(String propertyName, String defaultValue, boolean create) {
    File file = new File(System.getProperty(propertyName, defaultValue));
    if (file.exists()) {
      if (!file.isDirectory()) {
        System.err.printf("not a directory: \"%s\"%n", file);
        System.exit(1);
      }
    } else if (create) {
      if (!file.mkdirs()) {
        System.err.printf("couldn't create %s directory \"%s\"%n", propertyName, file);
        System.exit(1);
      }
    } else {
      System.err.printf("%s directory doesn't exist: \"%s\"%n", propertyName, file);
      System.exit(1);
    }
    return file;
  }

  private static class ErrorHandler implements Consumer<String> {
    private boolean sawAny = false;

    @Override
    public void accept(String s) {
      sawAny = true;
      System.err.println(s);
    }
  }

}
