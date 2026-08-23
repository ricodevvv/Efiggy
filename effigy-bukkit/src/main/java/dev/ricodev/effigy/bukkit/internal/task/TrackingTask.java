/*
 * This file is part of Effigy, licensed under the MIT License.
 * Copyright (c) 2026 ricodevvv and contributors.
 */
package dev.ricodev.effigy.bukkit.internal.task;

import dev.ricodev.effigy.Npc;
import dev.ricodev.effigy.NpcRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * The single repeating task that decides who sees which NPC.
 *
 * <p>One task for every NPC would be the obvious design and the wrong one: a lobby with a thousand
 * NPCs would then schedule a thousand tasks per tick. Instead this task walks the registry once per
 * cycle and, for each NPC, only the players of its world.
 *
 * <p>A cycle does three things per NPC and player pair:
 *
 * <ol>
 *   <li>show the NPC to players that are in range and accepted by the visibility rule;
 *   <li>hide it from viewers that walked out of range, changed world or stopped being accepted;
 *   <li>rotate it towards its viewers if the NPC is configured to follow them.
 * </ol>
 *
 * <p>The default interval is four ticks. Anything faster mostly generates packets for players who
 * have not moved, and anything slower makes an NPC appear noticeably late when a player sprints past
 * the edge of its view distance.
 *
 * <p>Not part of the public API.
 *
 * @since 1.0.0
 */
public final class TrackingTask implements Runnable {

  /** How many ticks pass between two cycles. */
  public static final long INTERVAL_TICKS = 4L;

  private final Plugin plugin;
  private final NpcRegistry registry;

  /**
   * Creates the task.
   *
   * @param plugin   the plugin used for logging.
   * @param registry the registry to walk.
   * @throws NullPointerException if any argument is {@code null}.
   * @since 1.0.0
   */
  public TrackingTask(@NotNull Plugin plugin, @NotNull NpcRegistry registry) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.registry = Objects.requireNonNull(registry, "registry");
  }

  @Override
  public void run() {
    for (Npc npc : this.registry.all()) {
      try {
        this.tick(npc);
      } catch (Throwable throwable) {
        // One broken NPC, usually through a visibility rule that throws, must not stop the others.
        this.plugin.getLogger().log(Level.SEVERE, "Failed to track NPC " + npc, throwable);
      }
    }
  }

  /**
   * Runs one cycle for a single NPC.
   *
   * @param npc the NPC to update.
   * @since 1.0.0
   */
  private void tick(@NotNull Npc npc) {
    if (npc.isRemoved()) {
      return;
    }

    Location location = npc.location();
    World world = location.getWorld();
    if (world == null) {
      // The world was unloaded underneath the NPC; drop every viewer and wait.
      this.hideAll(npc);
      return;
    }

    double range = npc.settings().viewDistanceSquared();
    boolean lookAtViewer = npc.settings().lookAtViewer();

    // Copy the viewers before mutating them: hide() writes to the same set.
    List<Player> stale = null;
    for (Player viewer : npc.viewers()) {
      if (!viewer.isOnline() || !this.shouldSee(npc, viewer, world, location, range)) {
        if (stale == null) {
          stale = new ArrayList<>();
        }
        stale.add(viewer);
      } else if (lookAtViewer) {
        npc.lookAt(viewer);
      }
    }
    if (stale != null) {
      for (Player viewer : stale) {
        npc.hide(viewer);
      }
    }

    for (Player player : world.getPlayers()) {
      if (!npc.isViewer(player) && this.shouldSee(npc, player, world, location, range)) {
        npc.show(player);
      }
    }
  }

  /**
   * Decides whether a player belongs into the viewer set of an NPC right now.
   *
   * @param npc      the NPC in question.
   * @param player   the player in question.
   * @param world    the world of the NPC.
   * @param location the location of the NPC.
   * @param range    the squared view distance of the NPC.
   * @return {@code true} if the player should see the NPC.
   * @since 1.0.0
   */
  private boolean shouldSee(
    @NotNull Npc npc,
    @NotNull Player player,
    @NotNull World world,
    @NotNull Location location,
    double range
  ) {
    Location playerLocation = player.getLocation();
    if (!world.equals(playerLocation.getWorld())) {
      return false;
    }
    // distanceSquared would throw for a different world, which the check above already ruled out.
    return playerLocation.distanceSquared(location) <= range && npc.canSee(player);
  }

  /**
   * Hides an NPC from all of its viewers, ignoring cancellation.
   *
   * @param npc the NPC to clear.
   * @since 1.0.0
   */
  private void hideAll(@NotNull Npc npc) {
    for (Player viewer : new ArrayList<>(npc.viewers())) {
      npc.hide(viewer);
    }
  }
}
