package com.yuvalshavit.effes2.parse;

public interface SimpleParseErrorListener {
  void error(int line, int charPositionInLine, String msg);
}
