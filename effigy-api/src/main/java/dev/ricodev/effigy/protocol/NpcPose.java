/*
 * This file is part of Effigy, licensed under the MIT License.
 * Copyright (c) 2026 ricodevvv and contributors.
 */
package dev.ricodev.effigy.protocol;

/**
 * The body poses a player entity can hold.
 *
 * <p>Poses arrived with Minecraft 1.14. On older servers only {@link #CROUCHING} has an effect,
 * through the legacy sneaking flag; every other value is ignored.
 *
 * <p>A pose changes the hitbox the client draws and, for {@link #SLEEPING}, where the entity is
 * rendered, but it never changes what the server thinks: an NPC has no hitbox on the server at all.
 *
 * @since 1.0.0
 */
public enum NpcPose {

  /** The default upright pose. */
  STANDING,
  /** Arms out, as while gliding with an elytra. */
  FALL_FLYING,
  /** Lying in a bed. */
  SLEEPING,
  /** Arms forward, as while swimming. */
  SWIMMING,
  /** Arms up, as while charging a riptide trident. */
  SPIN_ATTACK,
  /** Sneaking. The only pose that also works before 1.14. */
  CROUCHING,
  /** Lying face down, as a dead entity. */
  DYING
}
