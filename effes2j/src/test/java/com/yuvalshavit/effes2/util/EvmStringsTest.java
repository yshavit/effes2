package com.yuvalshavit.effes2.util;

import static org.testng.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class EvmStringsTest {
  @DataProvider(name = "test")
  public static Object[][] readParseFiles() {
    return new Cases()
      .test("hello", "hello")
      .test("hellö", "hellö")
      .test("let's go", "\"let's go\"") // single quotes may be quoted, but don't have to be
      .test("#hashtag", "\"#hashtag\"")
      .test("hellö world", "\"hellö world\"")
      .test("hello world", "\"hello world\"")
      .test("hello ☃ snowman", "\"hello \\u2603 snowman\"")
      .test("hello 🚀 rocketman", "\"hello \\u{1f680} rocketman\"")
      .test("", "\"\"")
      .get();
  }

  @Test(dataProvider = "test")
  public void escape(String raw, String expected) {
    assertEquals(EvmStrings.escape(raw), expected);
  }

  private static class Cases {
    private final List<Object[]> cases = new ArrayList<>();

    public Cases test(String input, String expected) {
      cases.add(new Object[] { input, expected });
      return this;
    }

    public Object[][] get() {
      return cases.toArray(new Object[0][]);
    }
  }
}
