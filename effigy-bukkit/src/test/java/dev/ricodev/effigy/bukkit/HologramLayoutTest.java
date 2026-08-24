/*
 * This file is part of Effigy, licensed under the MIT License.
 * Copyright (c) 2026 ricodevvv and contributors.
 */
package dev.ricodev.effigy.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.ricodev.effigy.bukkit.internal.hologram.HologramImpl;
import dev.ricodev.effigy.hologram.HologramLine;
import dev.ricodev.effigy.hologram.TextAnimation;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the hologram layout maths, which decides where each line ends up when text, animated
 * text and item lines are stacked together.
 */
class HologramLayoutTest {

  private static final double EPSILON = 0.0001;
  private static final double BASE = 64.0;
  private static final double OFFSET = 2.15;
  private static final double SPACING = 0.28;
  private static final double ITEM_HEIGHT = 0.6;

  private static double[] layout(List<HologramLine> content) {
    return HologramImpl.layout(BASE, OFFSET, SPACING, ITEM_HEIGHT, content);
  }

  private static List<HologramLine> text(int count) {
    List<HologramLine> lines = new ArrayList<>(count);
    for (int index = 0; index < count; index++) {
      lines.add(HologramLine.text("line " + index));
    }
    return lines;
  }

  @Test
  @DisplayName("a single line sits exactly at the configured offset")
  void singleLineSitsAtOffset() {
    assertEquals(BASE + OFFSET, layout(text(1))[0], EPSILON);
  }

  @Test
  @DisplayName("the last line anchors at the offset and earlier lines stack upwards")
  void linesStackUpwardsFromTheOffset() {
    double[] positions = layout(text(3));
    assertEquals(BASE + OFFSET, positions[2], EPSILON);
    assertEquals(positions[2] + SPACING, positions[1], EPSILON);
    assertEquals(positions[2] + 2 * SPACING, positions[0], EPSILON);
  }

  @Test
  @DisplayName("index zero is always the topmost line")
  void indexZeroIsTopmost() {
    for (int lineCount = 1; lineCount <= 8; lineCount++) {
      double[] positions = layout(text(lineCount));
      double previous = Double.MAX_VALUE;
      for (int index = 0; index < lineCount; index++) {
        assertTrue(positions[index] < previous, "line " + index + " is not below line " + (index - 1));
        previous = positions[index];
      }
    }
  }

  @Test
  @DisplayName("the bottom line stays put no matter how many lines are added above it")
  void addingLinesDoesNotMoveTheBottomOne() {
    for (int lineCount = 1; lineCount <= 8; lineCount++) {
      double[] positions = layout(text(lineCount));
      assertEquals(BASE + OFFSET, positions[lineCount - 1], EPSILON);
    }
  }

  @Test
  @DisplayName("an item line reserves its own, larger height for the lines above it")
  void itemLinesReserveTheirOwnHeight() {
    List<HologramLine> content = Arrays.asList(
      HologramLine.text("above"),
      HologramLine.item(new org.bukkit.inventory.ItemStack()),
      HologramLine.text("below"));
    double[] positions = layout(content);

    assertEquals(BASE + OFFSET, positions[2], EPSILON);
    // The item clears the text line under it, then the top text clears the item.
    assertEquals(positions[2] + SPACING, positions[1], EPSILON);
    assertEquals(positions[1] + ITEM_HEIGHT, positions[0], EPSILON);
  }

  @Test
  @DisplayName("an animated line occupies the same space as a plain text line")
  void animatedLinesMeasureLikeText() {
    TextAnimation animation = TextAnimation.of(Duration.ofMillis(100), "a", "b");
    double[] mixed = layout(Arrays.asList(
      HologramLine.animated(animation), HologramLine.text("bottom")));
    double[] plain = layout(text(2));

    assertEquals(plain[0], mixed[0], EPSILON);
    assertEquals(plain[1], mixed[1], EPSILON);
  }

  @Test
  @DisplayName("no two lines ever overlap, whatever the mix")
  void linesNeverOverlap() {
    List<HologramLine> content = Arrays.asList(
      HologramLine.item(new org.bukkit.inventory.ItemStack()),
      HologramLine.text("one"),
      HologramLine.item(new org.bukkit.inventory.ItemStack()),
      HologramLine.text("two"),
      HologramLine.text("three"));
    double[] positions = layout(content);

    for (int index = 0; index < positions.length - 1; index++) {
      double gap = positions[index] - positions[index + 1];
      assertTrue(gap >= SPACING - EPSILON, "lines " + index + " and " + (index + 1) + " overlap");
    }
  }

  @Test
  @DisplayName("an empty hologram produces no positions")
  void emptyContentProducesNoPositions() {
    assertEquals(0, layout(Collections.<HologramLine>emptyList()).length);
  }
}
