package com.yuvalshavit.effes2.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class StdinHelper {
  private static final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

  public static String readLine(String prompt) {
    try {
      System.out.print(prompt);
      return reader.readLine();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static Iterator<String> readUntil(String prompt, String stop) {
    return new Iterator<String>() {
      boolean foundEnd;
      String next;
      @Override
      public boolean hasNext() {
        if (foundEnd) {
          return false;
        }
        if (next == null) {
          next = readLine(prompt);
        }
        if (next == null || next.matches(stop)) {
          foundEnd = true;
        }
        return !foundEnd;
      }

      @Override
      public String next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        String result = next;
        next = null;
        return result;
      }
    };
  }

}
