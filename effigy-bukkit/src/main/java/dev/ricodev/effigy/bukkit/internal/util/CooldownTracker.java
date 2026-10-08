/*
 * This file is part of Effigy, licensed under the MIT License.
 * Copyright (c) 2026 ricodevvv and contributors.
 */
package dev.ricodev.effigy.bukkit.internal.util;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;

/**
 * Per player and per NPC rate limiting, used to collapse the packet burst of a single click into a
 * single event.
 *
 * <p>A vanilla client sends both an {@code INTERACT_AT} and an {@code INTERACT} packet for one right
 * click, and a modified client can send thousands per second. Filtering by packet type alone
 * therefore is not enough, and the filtering has to happen before the event is fired so that no
 * listener ever sees the duplicates.
 *
 * <p><strong>Threading.</strong> Fully thread-safe; the interaction path calls it from a netty
 * thread. Entries of a player are dropped on disconnect, so the map cannot outgrow the online player
 * count.
 *
 * <p>Not part of the public API.
 *
 * @since 1.0.0
 */
public final class CooldownTracker {

  private final Map<UUID, Map<Integer, Long>> lastUse = new ConcurrentHashMap<>();

  /**
   * Creates a tracker with no cooldowns stored.
   *
   * @since 1.0.0
   */
  public CooldownTracker() {
  }

  /**
   * Attempts to consume the cooldown of a player for one NPC.
   *
   * @param player   the unique id of the interacting player.
   * @param entityId the entity id of the NPC.
   * @param cooldown how long the player has to wait between two interactions.
   * @return {@code true} if the interaction may proceed, {@code false} if it happened too early.
   * @throws NullPointerException if any argument is {@code null}.
   * @since 1.0.0
   */
  public boolean tryAcquire(@NotNull UUID player, int entityId, @NotNull Duration cooldown) {
    if (cooldown.isZero() || cooldown.isNegative()) {
      return true;
    }

    long now = System.nanoTime();
    long window = cooldown.toNanos();
    Map<Integer, Long> perNpc = this.lastUse.computeIfAbsent(player, key -> new ConcurrentHashMap<>());

    // merge() keeps the check and the update atomic, so two packets arriving on different threads
    // can never both pass the window.
    Long stored = perNpc.merge(entityId, now, (previous, candidate) ->
      candidate - previous >= window ? candidate : previous);
    return stored == now;
  }

  /**
   * Drops every cooldown of a player.
   *
   * @param player the unique id of the player that disconnected.
   * @throws NullPointerException if {@code player} is {@code null}.
   * @since 1.0.0
   */
  public void forget(@NotNull UUID player) {
    this.lastUse.remove(player);
  }

  /**
   * Drops every cooldown referring to one NPC.
   *
   * @param entityId the entity id of the removed NPC.
   * @since 1.0.0
   */
  public void forgetNpc(int entityId) {
    for (Map<Integer, Long> perNpc : this.lastUse.values()) {
      perNpc.remove(entityId);
    }
  }

  /**
   * Drops every stored cooldown.
   *
   * @since 1.0.0
   */
  public void clear() {
    this.lastUse.clear();
  }
}
