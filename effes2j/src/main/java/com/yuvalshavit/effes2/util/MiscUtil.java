package com.yuvalshavit.effes2.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.function.Consumer;

public class MiscUtil {
  private MiscUtil() {}

  public static String toStringWithStackTrace(Throwable e) {
    StringWriter writer = new StringWriter();
    PrintWriter printer = new PrintWriter(writer);
    e.printStackTrace(printer);
    printer.flush();
    return writer.toString();
  }

  public static <T> void ifNotNull(T element, Consumer<? super T> handler) {
    if (element != null) {
      handler.accept(element);
    }
  }
}
