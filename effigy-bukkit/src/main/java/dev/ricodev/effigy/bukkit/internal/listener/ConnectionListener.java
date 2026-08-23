/*
 * This file is part of Effigy, licensed under the MIT License.
 * Copyright (c) 2026 ricodevvv and contributors.
 */
package dev.ricodev.effigy.bukkit.internal.listener;

import dev.ricodev.effigy.Npc;
import dev.ricodev.effigy.bukkit.internal.EffigyImpl;
import dev.ricodev.effigy.protocol.NpcAnimation;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Keeps the viewer sets honest and drives the imitation behaviour.
 *
 * <p>Two of these handlers exist to prevent leaks rather than to add features. A disconnecting
 * player must be dropped from every viewer set, otherwise the NPC keeps a strong reference to a
 * {@code Player} whose connection is gone, which both leaks memory and makes the tracking task send
 * packets into the void. A player who changes world must be dropped too, because the tracking task
 * only looks at the players of the world of the NPC and would never notice them again.
 *
 * <p>Not part of the public API.
 *
 * @since 1.0.0
 */
public final class ConnectionListener implements Listener {

  private final EffigyImpl effigy;

  /**
   * Creates the listener.
   *
   * @param effigy the instance whose NPCs are kept in sync.
   * @throws NullPointerException if {@code effigy} is {@code null}.
   * @since 1.0.0
   */
  public ConnectionListener(@NotNull EffigyImpl effigy) {
    this.effigy = Objects.requireNonNull(effigy, "effigy");
  }

  /**
   * Drops a disconnecting player from every viewer set and from the cooldown tracker.
   *
   * @param event the quit event.
   * @since 1.0.0
   */
  @EventHandler(priority = EventPriority.MONITOR)
  public void onQuit(@NotNull PlayerQuitEvent event) {
    Player player = event.getPlayer();
    this.effigy.forgetPlayer(player);
  }

  /**
   * Drops a player that left the world of an NPC from its viewer set.
   *
   * <p>No despawn packet is needed: a client discards every entity of the old world on its own when
   * the server sends it a respawn packet.
   *
   * @param event the world change event.
   * @since 1.0.0
   */
  @EventHandler(priority = EventPriority.MONITOR)
  public void onWorldChange(@NotNull PlayerChangedWorldEvent event) {
    Player player = event.getPlayer();
    for (Npc npc : this.effigy.registry().viewedBy(player)) {
      if (!player.getWorld().equals(npc.location().getWorld())) {
        this.effigy.forgetViewer(npc, player);
      }
    }
  }

  /**
   * Makes NPCs sneak along with a viewer that toggles sneaking.
   *
   * <p>Uses the Bukkit event rather than the sneak packet, so this handler already runs on the main
   * thread and sees the same state the rest of the server does.
   *
   * @param event the sneak event.
   * @since 1.0.0
   */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onSneak(@NotNull PlayerToggleSneakEvent event) {
    Player player = event.getPlayer();
    for (Npc npc : this.effigy.registry().viewedBy(player)) {
      if (npc.settings().imitateSneak()) {
        npc.sneaking(event.isSneaking());
      }
    }
  }

  /**
   * Makes NPCs swing their arm along with a viewer that swings.
   *
   * <p>The animation is sent only to the player that caused it, so the NPC appears to react to each
   * viewer individually instead of flailing whenever anybody nearby swings.
   *
   * @param event the animation event.
   * @since 1.0.0
   */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onAnimation(@NotNull PlayerAnimationEvent event) {
    if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
      return;
    }

    Player player = event.getPlayer();
    for (Npc npc : this.effigy.registry().viewedBy(player)) {
      if (npc.settings().imitateSwing()) {
        npc.playAnimation(NpcAnimation.SWING_MAIN_ARM, player);
      }
    }
  }
}
