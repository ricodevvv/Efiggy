/*
 * This file is part of Effigy, licensed under the MIT License.
 * Copyright (c) 2026 ricodevvv and contributors.
 */
package dev.ricodev.effigy.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.ricodev.effigy.bukkit.internal.util.Rotations;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the rotation maths, which is the only part of the packet path that can be verified
 * without a running server.
 */
class RotationsTest {

  private static final float EPSILON = 0.001f;

  @Test
  @DisplayName("yaw follows the Minecraft convention of zero pointing south")
  void yawMatchesMinecraftConvention() {
    // +Z is south and is yaw 0, +X is west and is yaw -90.
    assertEquals(0.0f, Rotations.yaw(0.0, 1.0), EPSILON);
    assertEquals(-90.0f, Rotations.yaw(1.0, 0.0), EPSILON);
    assertEquals(90.0f, Rotations.yaw(-1.0, 0.0), EPSILON);
    assertEquals(-180.0f, Rotations.yaw(0.0, -1.0), EPSILON);
  }

  @Test
  @DisplayName("pitch is positive when looking down")
  void pitchIsPositiveLookingDown() {
    assertEquals(0.0f, Rotations.pitch(1.0, 0.0, 0.0), EPSILON);
    assertEquals(45.0f, Rotations.pitch(1.0, -1.0, 0.0), EPSILON);
    assertEquals(-45.0f, Rotations.pitch(1.0, 1.0, 0.0), EPSILON);
    assertEquals(90.0f, Rotations.pitch(0.0, -1.0, 0.0), EPSILON);
    assertEquals(-90.0f, Rotations.pitch(0.0, 1.0, 0.0), EPSILON);
  }

  @Test
  @DisplayName("two points at the same spot produce no rotation instead of NaN")
  void coincidentPointsDoNotProduceNaN() {
    assertEquals(0.0f, Rotations.pitch(0.0, 0.0, 0.0), EPSILON);
    assertTrue(Float.isFinite(Rotations.yaw(0.0, 0.0)));
  }

  @Test
  @DisplayName("yaw is normalised into the range clients accept")
  void yawIsNormalised() {
    assertEquals(40.0f, Rotations.normalizeYaw(400.0f), EPSILON);
    assertEquals(-40.0f, Rotations.normalizeYaw(-400.0f), EPSILON);
    assertEquals(-170.0f, Rotations.normalizeYaw(190.0f), EPSILON);
    assertEquals(0.0f, Rotations.normalizeYaw(360.0f), EPSILON);

    for (float yaw = -1000.0f; yaw <= 1000.0f; yaw += 7.3f) {
      float normalized = Rotations.normalizeYaw(yaw);
      assertTrue(normalized >= -180.0f && normalized < 180.0f, "out of range: " + normalized);
    }
  }

  @Test
  @DisplayName("pitch is clamped to what a client can render")
  void pitchIsClamped() {
    assertEquals(90.0f, Rotations.clampPitch(180.0f), EPSILON);
    assertEquals(-90.0f, Rotations.clampPitch(-180.0f), EPSILON);
    assertEquals(12.5f, Rotations.clampPitch(12.5f), EPSILON);
  }
}
