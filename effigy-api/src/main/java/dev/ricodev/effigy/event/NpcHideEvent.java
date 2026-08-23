/*
 * This file is part of Effigy, licensed under the MIT License.
 * Copyright (c) 2026 ricodevvv and contributors.
 */
package dev.ricodev.effigy.event;

import dev.ricodev.effigy.Npc;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired just before an NPC is despawned for a player.
 *
 * <p>Cancelling keeps the NPC on the client for now; the tracking task will attempt the hide again
 * on its next cycle. The event is <em>not</em> fired when the player disconnects or when the NPC is
 * removed, because at that point the outcome is not negotiable.
 *
 * @since 1.0.0
 */
public class NpcHideEvent extends NpcEvent implements Cancellable {

  private static final HandlerList HANDLER_LIST = new HandlerList();

  private final Player player;
  private boolean cancelled;

  /**
   * Creates a new hide event.
   *
   * @param npc    the NPC that is about to be hidden.
   * @param player the player the NPC is about to be hidden from.
   * @throws NullPointerException if any argument is {@code null}.
   * @since 1.0.0
   */
  public NpcHideEvent(@NotNull Npc npc, @NotNull Player player) {
    super(npc);
    this.player = Objects.requireNonNull(player, "player");
  }

  /**
   * Returns the handler list of this event type.
   *
   * @return the shared handler list.
   * @since 1.0.0
   */
  @NotNull
  public static HandlerList getHandlerList() {
    return HANDLER_LIST;
  }

  /**
   * Returns the player the NPC is about to be hidden from.
   *
   * @return the current viewer.
   * @since 1.0.0
   */
  @NotNull
  public Player player() {
    return this.player;
  }

  @Override
  public boolean isCancelled() {
    return this.cancelled;
  }

  @Override
  public void setCancelled(boolean cancel) {
    this.cancelled = cancel;
  }

  @NotNull
  @Override
  public HandlerList getHandlers() {
    return HANDLER_LIST;
  }
}
