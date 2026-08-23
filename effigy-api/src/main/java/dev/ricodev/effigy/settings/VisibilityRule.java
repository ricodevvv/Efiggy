/*
 * This file is part of Effigy, licensed under the MIT License.
 * Copyright (c) 2026 ricodevvv and contributors.
 */
package dev.ricodev.effigy.settings;

import dev.ricodev.effigy.Npc;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Decides whether a given player is allowed to see a given NPC.
 *
 * <p>The rule is asked by the tracking task once per player per cycle, so it runs on the main thread
 * and must be fast and side effect free. Anything that hits a database or a web service belongs in a
 * cache that the rule merely reads.
 *
 * <pre>{@code
 * VisibilityRule staffOnly = (npc, player) -> player.hasPermission("myplugin.staff");
 * VisibilityRule nearbyStaff = staffOnly.and((npc, player) -> !player.isSleeping());
 * }</pre>
 *
 * @since 1.0.0
 */
@FunctionalInterface
public interface VisibilityRule {

  /**
   * Returns a rule that accepts every player.
   *
   * @return a rule that always returns {@code true}.
   * @since 1.0.0
   */
  @NotNull
  static VisibilityRule all() {
    return (npc, player) -> true;
  }

  /**
   * Returns a rule that accepts no player at all.
   *
   * <p>Useful for NPCs that are only ever revealed manually through {@link Npc#show(Player)}.
   *
   * @return a rule that always returns {@code false}.
   * @since 1.0.0
   */
  @NotNull
  static VisibilityRule none() {
    return (npc, player) -> false;
  }

  /**
   * Returns a rule that accepts players holding the given permission.
   *
   * @param permission the permission node to check.
   * @return a permission based rule.
   * @throws NullPointerException if {@code permission} is {@code null}.
   * @since 1.0.0
   */
  @NotNull
  static VisibilityRule permission(@NotNull String permission) {
    Objects.requireNonNull(permission, "permission");
    return (npc, player) -> player.hasPermission(permission);
  }

  /**
   * Tests whether the player may see the NPC.
   *
   * @param npc    the NPC being tracked.
   * @param player the player being considered.
   * @return {@code true} if the NPC should be visible to the player.
   * @since 1.0.0
   */
  boolean test(@NotNull Npc npc, @NotNull Player player);

  /**
   * Returns a rule that accepts a player only if this rule and the given one both accept them.
   *
   * @param other the rule to combine with.
   * @return the combined rule.
   * @throws NullPointerException if {@code other} is {@code null}.
   * @since 1.0.0
   */
  @NotNull
  default VisibilityRule and(@NotNull VisibilityRule other) {
    Objects.requireNonNull(other, "other");
    return (npc, player) -> this.test(npc, player) && other.test(npc, player);
  }

  /**
   * Returns a rule that accepts a player if this rule or the given one accepts them.
   *
   * @param other the rule to combine with.
   * @return the combined rule.
   * @throws NullPointerException if {@code other} is {@code null}.
   * @since 1.0.0
   */
  @NotNull
  default VisibilityRule or(@NotNull VisibilityRule other) {
    Objects.requireNonNull(other, "other");
    return (npc, player) -> this.test(npc, player) || other.test(npc, player);
  }

  /**
   * Returns a rule that accepts exactly the players this rule rejects.
   *
   * @return the negated rule.
   * @since 1.0.0
   */
  @NotNull
  default VisibilityRule negate() {
    return (npc, player) -> !this.test(npc, player);
  }
}
