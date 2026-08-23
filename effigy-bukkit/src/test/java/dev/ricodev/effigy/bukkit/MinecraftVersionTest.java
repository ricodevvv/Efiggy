/*
 * This file is part of Effigy, licensed under the MIT License.
 * Copyright (c) 2026 ricodevvv and contributors.
 */
package dev.ricodev.effigy.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.ricodev.effigy.bukkit.internal.util.MinecraftVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the version parsing that every protocol branch of the library depends on.
 */
class MinecraftVersionTest {

  @Test
  @DisplayName("release names parse with and without a patch component")
  void parsesReleaseNames() {
    assertEquals(MinecraftVersion.of(1, 21, 0), MinecraftVersion.parse("1.21"));
    assertEquals(MinecraftVersion.of(1, 20, 4), MinecraftVersion.parse("1.20.4"));
    assertEquals(MinecraftVersion.of(1, 19, 3), MinecraftVersion.parse("1.19.3-pre1"));
    assertEquals(MinecraftVersion.of(1, 8, 8), MinecraftVersion.parse("1.8.8"));
  }

  @Test
  @DisplayName("a name that does not start with a number is rejected")
  void rejectsGarbage() {
    assertNull(MinecraftVersion.parse("unknown"));
    assertNotNull(MinecraftVersion.parse("1.21.4-R0.1-SNAPSHOT"));
  }

  @Test
  @DisplayName("comparisons respect every component")
  void comparesComponentWise() {
    MinecraftVersion version = MinecraftVersion.of(1, 20, 2);
    assertTrue(version.atLeast(1, 20, 2));
    assertTrue(version.atLeast(1, 20));
    assertTrue(version.atLeast(1, 19, 3));
    assertFalse(version.atLeast(1, 20, 3));
    assertFalse(version.atLeast(1, 21));
  }

  @Test
  @DisplayName("the boundaries the protocol code branches on behave as expected")
  void protocolBoundaries() {
    // 1.19.3 introduced the not-listed tab list flag.
    assertFalse(MinecraftVersion.of(1, 19, 2).atLeast(1, 19, 3));
    assertTrue(MinecraftVersion.of(1, 19, 3).atLeast(1, 19, 3));
    // 1.20.2 moved players onto the generic spawn packet.
    assertFalse(MinecraftVersion.of(1, 20, 1).atLeast(1, 20, 2));
    assertTrue(MinecraftVersion.of(1, 21, 4).atLeast(1, 20, 2));
  }
}
