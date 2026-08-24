/*
 * This file is part of Effigy, licensed under the MIT License.
 * Copyright (c) 2026 ricodevvv and contributors.
 */
package dev.ricodev.effigy.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.ricodev.effigy.hologram.TextAnimation;
import java.time.Duration;
import java.util.List;
import org.bukkit.ChatColor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the text animation generators, and in particular for the colour code handling that
 * separates a working typewriter from one that types out the letters of its own colour codes.
 */
class TextAnimationTest {

  private static final char SECTION = ChatColor.COLOR_CHAR;

  @Test
  @DisplayName("colour codes do not count as visible characters")
  void codesAreNotVisible() {
    assertEquals(4, TextAnimation.visibleLength("&eShop"));
    assertEquals(4, TextAnimation.visibleLength(SECTION + "eShop"));
    assertEquals(4, TextAnimation.visibleLength("&e&lShop"));
    assertEquals(0, TextAnimation.visibleLength("&e&l"));
    assertEquals(5, TextAnimation.visibleLength("plain"));
  }

  @Test
  @DisplayName("an ampersand that starts no valid code is a character like any other")
  void danglingMarkersAreVisible() {
    // "&b" is aqua, so it really is a code; "&z" and "& " are not.
    assertEquals(1, TextAnimation.visibleLength("a&b"));
    assertEquals(3, TextAnimation.visibleLength("a&z"));
    assertEquals(1, TextAnimation.visibleLength("&"));
    assertEquals(3, TextAnimation.visibleLength("&&&"));
    assertEquals("Tom & Jerry", TextAnimation.stripCodes("Tom & Jerry"));
  }

  @Test
  @DisplayName("stripping removes the codes and nothing else")
  void strippingKeepsTheText() {
    assertEquals("Shop", TextAnimation.stripCodes("&eShop"));
    assertEquals("Shop", TextAnimation.stripCodes("&e&lShop"));
    assertEquals("Tom & Jerry", TextAnimation.stripCodes("&eTom & Jerry"));
  }

  @Test
  @DisplayName("a prefix keeps every code it has passed, so the colour never changes mid animation")
  void prefixesKeepTheirCodes() {
    assertEquals("&eS", TextAnimation.visiblePrefix("&eShop", 1));
    assertEquals("&eSh", TextAnimation.visiblePrefix("&eShop", 2));
    assertEquals("&eShop", TextAnimation.visiblePrefix("&eShop", 4));
    assertEquals("&eShop", TextAnimation.visiblePrefix("&eShop", 99));
    assertEquals("", TextAnimation.visiblePrefix("&eShop", 0));
  }

  @Test
  @DisplayName("a code sitting between two characters is carried into the prefix with them")
  void prefixesPickUpCodesInTheMiddle() {
    assertEquals("&aSh&bo", TextAnimation.visiblePrefix("&aSh&bop", 3));
  }

  @Test
  @DisplayName("the typewriter reveals one visible character per frame")
  void typewriterRevealsOneCharacterPerFrame() {
    TextAnimation animation = TextAnimation.typewriter(
      "&eShop", Duration.ofMillis(100), Duration.ZERO);
    List<String> frames = animation.frames();

    assertEquals(4, frames.size());
    assertEquals("&eS", frames.get(0));
    assertEquals("&eSh", frames.get(1));
    assertEquals("&eSho", frames.get(2));
    assertEquals("&eShop", frames.get(3));
  }

  @Test
  @DisplayName("the hold at the end is expressed as repeated final frames")
  void typewriterHoldsTheFinishedText() {
    TextAnimation animation = TextAnimation.typewriter(
      "abc", Duration.ofMillis(100), Duration.ofMillis(500));

    // Three characters plus five frames of holding.
    assertEquals(8, animation.frameCount());
    assertEquals("abc", animation.frame(7));
  }

  @Test
  @DisplayName("the wave highlights exactly one character per frame")
  void waveHighlightsOneCharacter() {
    TextAnimation animation = TextAnimation.wave("abc", ChatColor.YELLOW, Duration.ofMillis(80));
    assertEquals(3, animation.frameCount());

    String highlight = ChatColor.YELLOW.toString();
    assertTrue(animation.frame(0).startsWith(highlight + "a"), animation.frame(0));
    assertTrue(animation.frame(1).contains(highlight + "b"), animation.frame(1));
    assertTrue(animation.frame(2).contains(highlight + "c"), animation.frame(2));

    // Whatever the frame, the text a player reads is unchanged.
    for (String frame : animation.frames()) {
      assertEquals("abc", TextAnimation.stripCodes(frame));
    }
  }

  @Test
  @DisplayName("the wave restores the original colour after the highlighted character")
  void waveRestoresTheSurroundingColour() {
    TextAnimation animation = TextAnimation.wave(
      SECTION + "7abc", ChatColor.YELLOW, Duration.ofMillis(80));
    String frame = animation.frame(1);

    // The highlight is applied to "b" and the grey is put back for "c".
    assertTrue(frame.contains(ChatColor.YELLOW + "b" + SECTION + "7"), frame);
    assertEquals("abc", TextAnimation.stripCodes(frame));
  }

  @Test
  @DisplayName("a colour cycle produces one frame per colour")
  void colorCycleProducesOneFramePerColour() {
    TextAnimation animation = TextAnimation.colorCycle(
      "&cShop", Duration.ofMillis(200), ChatColor.RED, ChatColor.GOLD, ChatColor.YELLOW);

    assertEquals(3, animation.frameCount());
    for (String frame : animation.frames()) {
      // The original code is stripped, otherwise it would override the cycling colour.
      assertEquals("Shop", TextAnimation.stripCodes(frame));
    }
    assertEquals(ChatColor.RED + "Shop", animation.frame(0));
  }

  @Test
  @DisplayName("frame indexes wrap around the loop")
  void framesWrapAround() {
    TextAnimation animation = TextAnimation.of(Duration.ofMillis(100), "a", "b", "c");

    assertEquals("a", animation.frame(0));
    assertEquals("a", animation.frame(3));
    assertEquals("b", animation.frame(7));
    assertThrows(IllegalArgumentException.class, () -> animation.frame(-1));
  }

  @Test
  @DisplayName("an interval shorter than a tick is raised to one tick")
  void intervalIsClampedToATick() {
    TextAnimation animation = TextAnimation.of(Duration.ofMillis(1), "a", "b");
    assertEquals(Duration.ofMillis(50), animation.interval());
  }

  @Test
  @DisplayName("degenerate input is rejected at construction time")
  void degenerateInputIsRejected() {
    assertThrows(IllegalArgumentException.class,
      () -> TextAnimation.of(Duration.ofMillis(100)));
    assertThrows(IllegalArgumentException.class,
      () -> TextAnimation.typewriter("&e&l", Duration.ofMillis(100)));
    assertThrows(IllegalArgumentException.class,
      () -> TextAnimation.wave("", ChatColor.RED, Duration.ofMillis(100)));
    assertThrows(IllegalArgumentException.class,
      () -> TextAnimation.colorCycle("Shop", Duration.ofMillis(100), ChatColor.RED));
  }
}
