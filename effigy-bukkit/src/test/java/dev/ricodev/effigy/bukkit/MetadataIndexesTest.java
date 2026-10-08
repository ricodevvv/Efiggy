/*
 * This file is part of Effigy, licensed under the MIT License.
 * Copyright (c) 2026 ricodevvv and contributors.
 */
package dev.ricodev.effigy.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.ricodev.effigy.bukkit.internal.protocol.MetadataIndexes;
import dev.ricodev.effigy.bukkit.internal.util.MinecraftVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the metadata index table, checked against the protocol documentation of every release
 * at which an index moved.
 */
class MetadataIndexesTest {

  @Test
  @DisplayName("the skin layers follow every field inserted above the player")
  void skinLayers() {
    assertEquals(10, skinLayers(1, 8, 8));
    assertEquals(12, skinLayers(1, 9, 4));
    assertEquals(13, skinLayers(1, 10, 2));
    assertEquals(13, skinLayers(1, 13, 2));
    assertEquals(15, skinLayers(1, 14, 4));
    assertEquals(16, skinLayers(1, 16, 5));
    assertEquals(17, skinLayers(1, 17, 0));
    assertEquals(17, skinLayers(1, 21, 8));
    assertEquals(16, skinLayers(1, 21, 9));
    assertEquals(16, skinLayers(26, 3, 0));
  }

  @Test
  @DisplayName("the armour stand flags sit right after the living entity fields")
  void armorStandFlags() {
    assertEquals(10, armorStandFlags(1, 8, 8));
    assertEquals(10, armorStandFlags(1, 9, 4));
    assertEquals(11, armorStandFlags(1, 10, 2));
    assertEquals(11, armorStandFlags(1, 13, 2));
    assertEquals(13, armorStandFlags(1, 14, 4));
    assertEquals(14, armorStandFlags(1, 16, 5));
    assertEquals(15, armorStandFlags(1, 17, 0));
    assertEquals(15, armorStandFlags(1, 21, 9));
    assertEquals(15, armorStandFlags(26, 3, 0));
  }

  @Test
  @DisplayName("the item stack sits right after the base entity fields")
  void itemStack() {
    assertEquals(10, itemStack(1, 8, 8));
    assertEquals(5, itemStack(1, 9, 4));
    assertEquals(6, itemStack(1, 10, 2));
    assertEquals(6, itemStack(1, 13, 2));
    assertEquals(7, itemStack(1, 14, 4));
    assertEquals(8, itemStack(1, 17, 0));
    assertEquals(8, itemStack(26, 3, 0));
  }

  private static int skinLayers(int major, int minor, int patch) {
    return MetadataIndexes.of(MinecraftVersion.of(major, minor, patch)).skinLayers();
  }

  private static int armorStandFlags(int major, int minor, int patch) {
    return MetadataIndexes.of(MinecraftVersion.of(major, minor, patch)).armorStandFlags();
  }

  private static int itemStack(int major, int minor, int patch) {
    return MetadataIndexes.of(MinecraftVersion.of(major, minor, patch)).itemStack();
  }
}
