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
import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a player left or right clicks an NPC.
 *
 * <p>This is the event most plugins care about. It is dispatched on the main thread after the
 * originating packet has already been swallowed, so the server never learns that a player attacked
 * something, and after the per-player
 * {@link dev.ricodev.effigy.settings.NpcSettings#interactionCooldown() cooldown} has been applied,
 * so a listener sees one event per real click even though the client sends several packets.
 *
 * <pre>{@code
 * @EventHandler
 * public void onInteract(NpcInteractEvent event) {
 *   if (event.action() == NpcInteractEvent.Action.RIGHT_CLICK
 *       && "shop".equals(event.npc().metadata().get("role"))) {
 *     this.openShop(event.player());
 *   }
 * }
 * }</pre>
 *
 * <p>Cancelling the event stops Effigy's own reactions to the click, such as
 * {@link dev.ricodev.effigy.settings.NpcSettings#imitateSwing() swing imitation}. It cannot undo
 * anything a listener has already done.
 *
 * @since 1.0.0
 */
public class NpcInteractEvent extends NpcEvent implements Cancellable {

  private static final HandlerList HANDLER_LIST = new HandlerList();

  private final Player player;
  private final Action action;
  private final EquipmentSlot hand;
  private boolean cancelled;

  /**
   * Creates a new interact event.
   *
   * @param npc    the NPC that was clicked.
   * @param player the player that clicked.
   * @param action what kind of click it was.
   * @param hand   the hand used, always {@link EquipmentSlot#HAND} for a left click.
   * @throws NullPointerException if any argument is {@code null}.
   * @since 1.0.0
   */
  public NpcInteractEvent(
    @NotNull Npc npc,
    @NotNull Player player,
    @NotNull Action action,
    @NotNull EquipmentSlot hand
  ) {
    super(npc);
    this.player = Objects.requireNonNull(player, "player");
    this.action = Objects.requireNonNull(action, "action");
    this.hand = Objects.requireNonNull(hand, "hand");
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
   * Returns the player that interacted with the NPC.
   *
   * @return the interacting player.
   * @since 1.0.0
   */
  @NotNull
  public Player player() {
    return this.player;
  }

  /**
   * Returns what kind of click this was.
   *
   * @return the interaction type.
   * @since 1.0.0
   */
  @NotNull
  public Action action() {
    return this.action;
  }

  /**
   * Returns the hand the player used.
   *
   * <p>Always {@link EquipmentSlot#HAND} for a left click and on servers older than 1.9, which have
   * no off hand.
   *
   * @return the hand used for the interaction.
   * @since 1.0.0
   */
  @NotNull
  public EquipmentSlot hand() {
    return this.hand;
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

  /**
   * The kind of click a player performed on an NPC.
   *
   * @since 1.0.0
   */
  public enum Action {

    /** The player attacked the NPC, that is, left clicked it. */
    LEFT_CLICK,
    /** The player used the NPC, that is, right clicked it. */
    RIGHT_CLICK
  }
}
