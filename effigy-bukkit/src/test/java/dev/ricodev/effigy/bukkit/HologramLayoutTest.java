/*
 * This file is part of Effigy, licensed under the MIT License.
 * Copyright (c) 2026 ricodevvv and contributors.
 */
package dev.ricodev.effigy.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.ricodev.effigy.bukkit.internal.hologram.HologramImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the hologram layout maths, which decides where each line of text ends up.
 */
class HologramLayoutTest {

  private static final double EPSILON = 0.0001;
  private static final double OFFSET = 2.15;
  private static final double SPACING = 0.28;

  @Test
  @DisplayName("a single line sits exactly at the configured offset")
  void singleLineSitsAtOffset() {
    assertEquals(64.0 + OFFSET, HologramImpl.lineY(64.0, OFFSET, SPACING, 0, 1), EPSILON);
  }

  @Test
  @DisplayName("the last line anchors at the offset and earlier lines stack upwards")
  void linesStackUpwardsFromTheOffset() {
    int lineCount = 3;
    double bottom = HologramImpl.lineY(64.0, OFFSET, SPACING, 2, lineCount);
    double middle = HologramImpl.lineY(64.0, OFFSET, SPACING, 1, lineCount);
    double top = HologramImpl.lineY(64.0, OFFSET, SPACING, 0, lineCount);

    assertEquals(64.0 + OFFSET, bottom, EPSILON);
    assertEquals(bottom + SPACING, middle, EPSILON);
    assertEquals(bottom + 2 * SPACING, top, EPSILON);
  }

  @Test
  @DisplayName("index zero is always the topmost line")
  void indexZeroIsTopmost() {
    for (int lineCount = 1; lineCount <= 8; lineCount++) {
      double previous = Double.MAX_VALUE;
      for (int index = 0; index < lineCount; index++) {
        double y = HologramImpl.lineY(64.0, OFFSET, SPACING, index, lineCount);
        assertTrue(y < previous, "line " + index + " of " + lineCount + " is not below line " + (index - 1));
        previous = y;
      }
    }
  }

  @Test
  @DisplayName("the bottom line stays put no matter how many lines are added above it")
  void addingLinesDoesNotMoveTheBottomOne() {
    for (int lineCount = 1; lineCount <= 8; lineCount++) {
      double bottom = HologramImpl.lineY(64.0, OFFSET, SPACING, lineCount - 1, lineCount);
      assertEquals(64.0 + OFFSET, bottom, EPSILON);
    }
  }
}
