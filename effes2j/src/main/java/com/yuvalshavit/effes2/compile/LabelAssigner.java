package com.yuvalshavit.effes2.compile;

import java.util.HashMap;
import java.util.Map;

import org.antlr.v4.runtime.Token;

import com.yuvalshavit.effesvm.runtime.EffesOps;

public class LabelAssigner {
  private static final char SEGMENT_DELIMITER = '.';
  private static final char DISAMBIGUATION_DELIMITER = ':';
  private final Map<String,Boolean> names = new HashMap<>();
  private final EffesOps<Token> out;

  public LabelAssigner(EffesOps<Token> out) {
    this.out = out;
  }

  /**
   * Allocates a new, unique label containing (in some fashion) the description provided.
   *
   * How this label is disambiguated from others of the same provided description is implementation-specific.
   * @throws IllegalArgumentException if the description contains a period
   */
  public String allocate(String description) {
    validate(description);
    int disambiguation = 0;
    String allocatedName = description;
    while (names.putIfAbsent(allocatedName, true) != null) { // try to put new keys until we succeed
      allocatedName = description + DISAMBIGUATION_DELIMITER + (++disambiguation);
    }
    return allocatedName;
  }

  public void place(Token tok, String name) {
    if (!names.replace(name, true, false)) {
      throw new IllegalArgumentException("unknown (or already-placed) label: " + name);
    }
    out.label(tok, name);
  }

  private static void validate(String description) {
    if (description.indexOf(SEGMENT_DELIMITER) >= 0) {
      throw new IllegalArgumentException("illegal segment: " + description);
    }
  }
}
