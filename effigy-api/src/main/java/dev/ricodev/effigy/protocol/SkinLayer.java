/*
 * This file is part of Effigy, licensed under the MIT License.
 * Copyright (c) 2026 ricodevvv and contributors.
 */
package dev.ricodev.effigy.protocol;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

/**
 * The optional overlay parts of a player skin, sent to clients as a bit mask.
 *
 * <p>Clients render none of them by default, which is why an NPC spawned without an explicit call to
 * {@link dev.ricodev.effigy.Npc#skinLayers(Set)} looks like it is missing its hat and jacket even
 * though the skin itself is correct.
 *
 * @since 1.0.0
 */
public enum SkinLayer {

  /** The cape, only visible on profiles whose texture property includes one. */
  CAPE(0x01),
  /** The overlay of the torso. */
  JACKET(0x02),
  /** The overlay of the left arm. */
  LEFT_SLEEVE(0x04),
  /** The overlay of the right arm. */
  RIGHT_SLEEVE(0x08),
  /** The overlay of the left leg. */
  LEFT_PANTS(0x10),
  /** The overlay of the right leg. */
  RIGHT_PANTS(0x20),
  /** The second layer of the head, commonly hair or a hat. */
  HAT(0x40);

  private static final Set<SkinLayer> ALL = Collections.unmodifiableSet(EnumSet.allOf(SkinLayer.class));
  private static final Set<SkinLayer> NONE = Collections.unmodifiableSet(EnumSet.noneOf(SkinLayer.class));

  private final int mask;

  SkinLayer(int mask) {
    this.mask = mask;
  }

  /**
   * Returns an immutable set containing every layer.
   *
   * @return all skin layers.
   * @since 1.0.0
   */
  @NotNull
  @Unmodifiable
  public static Set<SkinLayer> all() {
    return ALL;
  }

  /**
   * Returns an immutable empty set of layers.
   *
   * @return no skin layers.
   * @since 1.0.0
   */
  @NotNull
  @Unmodifiable
  public static Set<SkinLayer> none() {
    return NONE;
  }

  /**
   * Packs the given layers into the bit mask the protocol expects.
   *
   * @param layers the layers to encode.
   * @return the encoded mask, {@code 0} if the set is empty.
   * @throws NullPointerException if {@code layers} is {@code null} or contains {@code null}.
   * @since 1.0.0
   */
  public static byte pack(@NotNull Set<SkinLayer> layers) {
    int mask = 0;
    for (SkinLayer layer : layers) {
      mask |= layer.mask;
    }
    return (byte) mask;
  }

  /**
   * Unpacks a protocol bit mask back into a set of layers.
   *
   * @param mask the mask to decode.
   * @return the decoded layers, in enum order.
   * @since 1.0.0
   */
  @NotNull
  public static Set<SkinLayer> unpack(byte mask) {
    EnumSet<SkinLayer> layers = EnumSet.noneOf(SkinLayer.class);
    for (SkinLayer layer : values()) {
      if ((mask & layer.mask) != 0) {
        layers.add(layer);
      }
    }
    return layers;
  }

  /**
   * Returns the single bit this layer occupies in the mask.
   *
   * @return the bit mask of this layer.
   * @since 1.0.0
   */
  public int mask() {
    return this.mask;
  }
}
