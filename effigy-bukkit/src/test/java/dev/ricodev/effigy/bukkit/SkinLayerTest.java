/*
 * This file is part of Effigy, licensed under the MIT License.
 * Copyright (c) 2026 ricodevvv and contributors.
 */
package dev.ricodev.effigy.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.ricodev.effigy.profile.NpcProfile;
import dev.ricodev.effigy.protocol.SkinLayer;
import java.util.EnumSet;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the API value types that carry protocol level invariants.
 */
class SkinLayerTest {

  @Test
  @DisplayName("packing and unpacking a layer set round trips")
  void packRoundTrips() {
    EnumSet<SkinLayer> layers = EnumSet.of(SkinLayer.HAT, SkinLayer.JACKET);
    assertEquals(layers, SkinLayer.unpack(SkinLayer.pack(layers)));
    assertEquals(SkinLayer.none(), SkinLayer.unpack(SkinLayer.pack(SkinLayer.none())));
    assertEquals(EnumSet.allOf(SkinLayer.class), SkinLayer.unpack(SkinLayer.pack(SkinLayer.all())));
  }

  @Test
  @DisplayName("all layers pack into the byte the vanilla client sends")
  void allLayersMatchVanillaMask() {
    assertEquals((byte) 0x7F, SkinLayer.pack(SkinLayer.all()));
    assertEquals((byte) 0x00, SkinLayer.pack(SkinLayer.none()));
  }

  @Test
  @DisplayName("a name a client would refuse is rejected at construction time")
  void profileRejectsImpossibleNames() {
    assertThrows(IllegalArgumentException.class, () -> NpcProfile.of(""));
    assertThrows(IllegalArgumentException.class, () -> NpcProfile.of("this_name_is_far_too_long"));
    assertThrows(IllegalArgumentException.class, () -> NpcProfile.of("has spaces"));
  }

  @Test
  @DisplayName("the unique id survives a skin swap")
  void identitySurvivesSkinSwap() {
    UUID id = UUID.randomUUID();
    NpcProfile original = NpcProfile.of(id, "Guide");
    NpcProfile skinned = original.withSkinOf(NpcProfile.of(UUID.randomUUID(), "Notch"));

    assertEquals(id, skinned.uniqueId());
    assertEquals("Guide", skinned.name());
  }
}
