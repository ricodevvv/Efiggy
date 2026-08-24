/*
 * This file is part of Effigy, licensed under the MIT License.
 * Copyright (c) 2026 ricodevvv and contributors.
 */
package dev.ricodev.effigy.bukkit.internal.hologram;

import org.jetbrains.annotations.NotNull;

/**
 * Where a hologram announces that it does, or no longer does, have something to animate.
 *
 * <p>The point of the callback is that the animation task should not exist at all on a server whose
 * holograms are static. The hub starts one repeating task when the first hologram reports itself as
 * animating and cancels it again when the last one stops, so the common case costs nothing.
 *
 * <p>Not part of the public API.
 *
 * @since 1.0.0
 */
public interface AnimationHub {

  /**
   * Reports whether a hologram currently has at least one animated line.
   *
   * <p>Called on every render, so it has to tolerate being handed the same value repeatedly.
   *
   * @param hologram  the hologram reporting in.
   * @param animating {@code true} if it has an animated line for at least one viewer.
   * @since 1.0.0
   */
  void setAnimating(@NotNull HologramImpl hologram, boolean animating);
}
