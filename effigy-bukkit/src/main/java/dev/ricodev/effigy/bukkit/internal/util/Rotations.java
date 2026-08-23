/*
 * This file is part of Effigy, licensed under the MIT License.
 * Copyright (c) 2026 ricodevvv and contributors.
 */
package dev.ricodev.effigy.bukkit.internal.util;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

/**
 * Rotation maths, kept free of Bukkit state so that it can be unit tested.
 *
 * <p>Not part of the public API.
 *
 * @since 1.0.0
 */
public final class Rotations {

  private Rotations() {
    throw new AssertionError("Rotations is a utility class");
  }

  /**
   * Computes the yaw needed to look from one point at another.
   *
   * <p>Minecraft measures yaw clockwise from south, which is the {@code +Z} axis, hence the offset
   * of ninety degrees against the mathematical convention.
   *
   * @param dx the difference on the x axis, target minus origin.
   * @param dz the difference on the z axis, target minus origin.
   * @return the yaw in degrees, normalised to {@code [-180, 180)}.
   * @since 1.0.0
   */
  public static float yaw(double dx, double dz) {
    return normalizeYaw((float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f);
  }

  /**
   * Computes the pitch needed to look from one point at another.
   *
   * @param dx the difference on the x axis, target minus origin.
   * @param dy the difference on the y axis, target minus origin.
   * @param dz the difference on the z axis, target minus origin.
   * @return the pitch in degrees, clamped to {@code [-90, 90]}. Zero when the two points coincide.
   * @since 1.0.0
   */
  public static float pitch(double dx, double dy, double dz) {
    double horizontal = Math.sqrt(dx * dx + dz * dz);
    if (horizontal == 0.0 && dy == 0.0) {
      return 0.0f;
    }
    return clampPitch((float) -Math.toDegrees(Math.atan2(dy, horizontal)));
  }

  /**
   * Brings a yaw into the range clients expect.
   *
   * @param yaw the yaw in degrees, of any magnitude.
   * @return the equivalent yaw in {@code [-180, 180)}.
   * @since 1.0.0
   */
  public static float normalizeYaw(float yaw) {
    float normalized = yaw % 360.0f;
    if (normalized >= 180.0f) {
      normalized -= 360.0f;
    } else if (normalized < -180.0f) {
      normalized += 360.0f;
    }
    // -0.0f compares equal to 0.0f but serialises differently; fold it away.
    return normalized == 0.0f ? 0.0f : normalized;
  }

  /**
   * Clamps a pitch to the range a client can render.
   *
   * @param pitch the pitch in degrees.
   * @return the pitch clamped to {@code [-90, 90]}.
   * @since 1.0.0
   */
  public static float clampPitch(float pitch) {
    return Math.max(-90.0f, Math.min(90.0f, pitch));
  }

  /**
   * Applies to {@code origin} the rotation that makes it face {@code target}.
   *
   * @param origin the location that is rotated; mutated in place.
   * @param target the location to face.
   * @throws NullPointerException if any argument is {@code null}.
   * @since 1.0.0
   */
  public static void face(@NotNull Location origin, @NotNull Location target) {
    double dx = target.getX() - origin.getX();
    double dy = target.getY() - origin.getY();
    double dz = target.getZ() - origin.getZ();
    origin.setYaw(yaw(dx, dz));
    origin.setPitch(pitch(dx, dy, dz));
  }
}
