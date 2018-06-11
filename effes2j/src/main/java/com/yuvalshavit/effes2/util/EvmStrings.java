package com.yuvalshavit.effes2.util;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;

import com.yuvalshavit.effes2.parse.EffesLexer;

public class EvmStrings {
  private static final int WIGGLE_ROOM = 16; // for escape chars and all that. just a guess

  private EvmStrings() {}

  public static String unescapeRegex(String input) {
    return input.replaceAll("\\\\/", "/");
  }

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

  public static String quotedEfToString(TerminalNode terminalNode) {
    Token symbol = terminalNode.getSymbol();
    if (symbol.getType() != EffesLexer.QUOTED_STRING) {
      throw new RuntimeException("expected QUOTED_STRING, found" + EffesLexer.VOCABULARY.getDisplayName(symbol.getType()));
    }
    String text = symbol.getText();
    return text.substring(1, text.length() - 1);
  }

  /**
   * Translates escape characters to their equivalents. For instance, the two characters '\' and '\t' will become a tab.
   */
  public static String unEscape(String string) {
    if (string.chars().noneMatch(c -> c == '\\')) {
      return string;
    }
    StringBuilder sb = new StringBuilder(string.length());
    boolean prevWasEsc = false;
    for (int i = 0; i < string.length(); ++i) {
      char c = string.charAt(i);
      if (prevWasEsc) {
        final char esc;
        switch (c) {
          case 'b':
            esc = '\b';
            break;
          case 't':
            esc = '\t';
            break;
          case 'n':
            esc = '\n';
            break;
          case 'f':
            esc = '\f';
            break;
          case 'r':
            esc = '\r';
            break;
          case '"':
            esc = '"';
            break;
          case '\\':
            esc = '\\';
            break;
          default:
            throw new IllegalArgumentException("unrecognized escape: \\" + c + " in " + string);
        }
        sb.append(esc);
        prevWasEsc = false;
      } else if (c == '\\') {
        prevWasEsc = true;
      } else {
        sb.append(c);
      }
    }
    if (prevWasEsc) {
      throw new IllegalArgumentException("can't end with unescaped backslash: " + string);
    }
    return sb.toString();
  }
}
