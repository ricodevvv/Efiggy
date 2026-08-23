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
 * Fired just before an NPC is spawned for a player.
 *
 * <p>Cancelling the event prevents the spawn packets from being sent. The tracking task will try
 * again on its next cycle while the player stays in range, so cancelling here is a per-tick veto
 * rather than a permanent one; use a
 * {@link dev.ricodev.effigy.settings.VisibilityRule} for a permanent decision, which is also much
 * cheaper.
 *
 * @since 1.0.0
 */
public class NpcShowEvent extends NpcEvent implements Cancellable {

  private static final HandlerList HANDLER_LIST = new HandlerList();

  private final Player player;
  private boolean cancelled;

  /**
   * Creates a new show event.
   *
   * @param npc    the NPC that is about to be shown.
   * @param player the player the NPC is about to be shown to.
   * @throws NullPointerException if any argument is {@code null}.
   * @since 1.0.0
   */
  public NpcShowEvent(@NotNull Npc npc, @NotNull Player player) {
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
   * Returns the player the NPC is about to be shown to.
   *
   * @return the future viewer.
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
