package com.yuvalshavit.effes2.util;

public class EvmStrings {
  private static final int WIGGLE_ROOM = 16; // for escape chars and all that. just a guess

  private EvmStrings() {}

  public static String escape(String input) {
    if (!input.isEmpty() && input.chars().allMatch(codePoint -> codePoint != '#' && Character.isLetterOrDigit(codePoint))) {
      return input;
    }
    StringBuilder sb = new StringBuilder(input.length() + 2 + WIGGLE_ROOM);
    sb.append('"');
    input.codePoints().forEach(c -> {
      final char esc;
      switch (c) {
        case '\b':
          esc = 'b';
          break;
        case '\t':
          esc = 't';
          break;
        case '\n':
          esc = 'n';
          break;
        case '\f':
          esc = 'f';
          break;
        case '\r':
          esc = 'r';
          break;
        case '"':
          esc = '"';
          break;
        case '\\':
          esc = '\\';
          break;
        default:
          esc = 0;
      }
      if (esc > 0) {
        sb.append('\\').append(esc);
      } else if ((c >= ' ' && c <= '~') || Character.isLetterOrDigit(c)) { // all "normal" ASCII chars, all unicode letters
        sb.append((char) c);
      } else if (c <= Character.MAX_VALUE) {
        sb.append("\\u").append(Integer.toHexString(c));
      } else {
        sb.append("\\u{").append(Integer.toHexString(c)).append('}');
      }
    });
    sb.append('"');
    return sb.toString();
  }
}
