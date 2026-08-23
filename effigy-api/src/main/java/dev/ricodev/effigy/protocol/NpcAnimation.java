/*
 * This file is part of Effigy, licensed under the MIT License.
 * Copyright (c) 2026 ricodevvv and contributors.
 */
package dev.ricodev.effigy.protocol;

/**
 * The animations a player entity can be told to play.
 *
 * <p>Animations are one-shot: the client plays them once and returns to the idle state on its own,
 * so there is nothing to reset afterwards.
 *
 * @since 1.0.0
 */
public enum NpcAnimation {

  /** Swings the main hand, the animation of a normal left click. */
  SWING_MAIN_ARM,
  /** Swings the off hand. Ignored by clients older than 1.9. */
  SWING_OFF_HAND,
  /** Plays the red damage flash and the hurt sound. */
  TAKE_DAMAGE,
  /** Plays the animation of an entity getting out of a bed. */
  LEAVE_BED,
  /** Plays the critical hit particles around the entity. */
  CRITICAL_HIT,
  /** Plays the enchanted hit particles around the entity. */
  MAGIC_CRITICAL_HIT
}
