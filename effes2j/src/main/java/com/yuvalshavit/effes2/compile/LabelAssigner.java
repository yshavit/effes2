package com.yuvalshavit.effes2.compile;

import java.util.HashSet;
import java.util.Set;

import com.yuvalshavit.effesvm.runtime.EffesOps;

public class LabelAssigner {
  private static final char SEGMENT_DELIMITER = '.';
  private final Set<String> names = new HashSet<>();
  private final EffesOps<Void> out;

  public LabelAssigner(EffesOps<Void> out) {
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
    while (!names.add(allocatedName)) {
      allocatedName = description + '-' + (++disambiguation);
    }
    return allocatedName;
  }

  public void place(String name) {
    if (!names.remove(name)) {
      throw new IllegalArgumentException("unknown (or already-placed) label: " + name);
    }
    out.label(name);
  }

  /**
   * Produces a new label containing the "parent" description and a sub-segment. This is <em>not</em> disambiguated with other labels. It's assumed that a
   * sub-label happens close enough to its parent that no further disambiguation is needed.
   */
  public static String subLabel(String parent, String subSegment) {
    validate(subSegment);
    return parent + SEGMENT_DELIMITER + subSegment;
  }

  private static void validate(String description) {
    if (description.indexOf(SEGMENT_DELIMITER) >= 0) {
      throw new IllegalArgumentException("illegal segment: " + description);
    }
  }
}
