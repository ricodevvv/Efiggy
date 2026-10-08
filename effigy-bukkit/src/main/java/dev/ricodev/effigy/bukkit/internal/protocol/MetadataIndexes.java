/*
 * This file is part of Effigy, licensed under the MIT License.
 * Copyright (c) 2026 ricodevvv and contributors.
 */
package dev.ricodev.effigy.bukkit.internal.protocol;

import dev.ricodev.effigy.bukkit.internal.util.MinecraftVersion;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Where the metadata fields Effigy writes live on one server version.
 *
 * <p>A field index is its position in the class hierarchy of the entity, so it moves every time
 * Mojang adds a field to a superclass. The base entity fields at the top never moved; the ones
 * further down did, several times. All of that is kept here, with no packet library types, so the
 * whole table can be unit tested.
 *
 * <p>Not part of the public API.
 *
 * @see <a href="https://minecraft.wiki/w/Java_Edition_protocol/Entity_metadata">Entity metadata</a>
 * @since 1.0.0
 */
public final class MetadataIndexes {

  /** The shared flag byte of every entity. */
  public static final int FLAGS = 0;
  /** The custom name of any entity. */
  public static final int CUSTOM_NAME = 2;
  /** Whether the custom name is drawn without looking at the entity. */
  public static final int CUSTOM_NAME_VISIBLE = 3;
  /** The flag disabling gravity, which exists from 1.10 on. */
  public static final int NO_GRAVITY = 5;
  /** The pose, which exists from 1.14 on. */
  public static final int POSE = 6;

  private final int skinLayers;
  private final int armorStandFlags;
  private final int itemStack;

  private MetadataIndexes(int skinLayers, int armorStandFlags, int itemStack) {
    this.skinLayers = skinLayers;
    this.armorStandFlags = armorStandFlags;
    this.itemStack = itemStack;
  }

  /**
   * Builds the table for a server version.
   *
   * @param version the version of the server.
   * @return the indexes that version uses.
   * @since 1.0.0
   */
  @NotNull
  public static MetadataIndexes of(@NotNull MinecraftVersion version) {
    Objects.requireNonNull(version, "version");
    return new MetadataIndexes(skinLayersOf(version), armorStandFlagsOf(version), itemStackOf(version));
  }

  /**
   * Returns the index of the displayed skin parts of a player.
   *
   * @return the skin layer index.
   * @since 1.0.0
   */
  public int skinLayers() {
    return this.skinLayers;
  }

  /**
   * Returns the index of the armour stand flag byte.
   *
   * @return the armour stand flags index.
   * @since 1.0.0
   */
  public int armorStandFlags() {
    return this.armorStandFlags;
  }

  /**
   * Returns the index of the stack a dropped item entity carries.
   *
   * @return the item stack index.
   * @since 1.0.0
   */
  public int itemStack() {
    return this.itemStack;
  }

  /**
   * Works out where the skin parts sit. 1.21.9 moved them into the new avatar class, which sits
   * between the living entity and the player, so they went one up instead of one down.
   *
   * @param version the version of the server.
   * @return the skin layer index.
   * @since 1.0.0
   */
  private static int skinLayersOf(@NotNull MinecraftVersion version) {
    if (version.atLeast(1, 21, 9)) {
      return 16;
    }
    if (version.atLeast(1, 17)) {
      return 17;
    }
    if (version.atLeast(1, 15)) {
      return 16;
    }
    if (version.atLeast(1, 14)) {
      return 15;
    }
    if (version.atLeast(1, 10)) {
      return 13;
    }
    if (version.atLeast(1, 9)) {
      return 12;
    }
    return 10;
  }

  /**
   * Works out where the armour stand flags sit, right after the last living entity field.
   *
   * @param version the version of the server.
   * @return the armour stand flags index.
   * @since 1.0.0
   */
  private static int armorStandFlagsOf(@NotNull MinecraftVersion version) {
    if (version.atLeast(1, 17)) {
      return 15;
    }
    if (version.atLeast(1, 15)) {
      return 14;
    }
    if (version.atLeast(1, 14)) {
      return 13;
    }
    if (version.atLeast(1, 10)) {
      return 11;
    }
    return 10;
  }

  /**
   * Works out where the stack of an item entity sits, right after the last base entity field.
   *
   * @param version the version of the server.
   * @return the item stack index.
   * @since 1.0.0
   */
  private static int itemStackOf(@NotNull MinecraftVersion version) {
    if (version.atLeast(1, 17)) {
      return 8;
    }
    if (version.atLeast(1, 14)) {
      return 7;
    }
    if (version.atLeast(1, 10)) {
      return 6;
    }
    if (version.atLeast(1, 9)) {
      return 5;
    }
    return 10;
  }
}
