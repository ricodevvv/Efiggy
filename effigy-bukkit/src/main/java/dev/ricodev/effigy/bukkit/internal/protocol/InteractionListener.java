/*
 * This file is part of Effigy, licensed under the MIT License.
 * Copyright (c) 2026 ricodevvv and contributors.
 */
package dev.ricodev.effigy.bukkit.internal.protocol;

import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import dev.ricodev.effigy.Npc;
import dev.ricodev.effigy.NpcRegistry;
import dev.ricodev.effigy.bukkit.internal.util.CooldownTracker;
import dev.ricodev.effigy.event.NpcInteractEvent;
import dev.ricodev.effigy.protocol.NpcAnimation;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * Turns the interaction packets a client sends for an NPC into {@link NpcInteractEvent}.
 *
 * <p>Runs on a netty thread, which forces three things:
 *
 * <ul>
 *   <li>the lookup of the NPC has to be cheap, which it is because the registry is a hash map;
 *   <li>the packet has to be cancelled here rather than later, otherwise the server would try to
 *       resolve an entity id it knows nothing about and log a warning per click;
 *   <li>the event must be handed to the main thread, because Bukkit listeners are allowed to assume
 *       they run there.
 * </ul>
 *
 * <p>Only {@code ATTACK} and {@code INTERACT} are acted upon. {@code INTERACT_AT} is deliberately
 * ignored: clients send it alongside {@code INTERACT} for the same click, and treating both would
 * double every right click.
 *
 * <p>Not part of the public API.
 *
 * @since 1.0.0
 */
public final class InteractionListener extends SimplePacketListenerAbstract {

  private final Plugin plugin;
  private final NpcRegistry registry;
  private final CooldownTracker cooldowns;

  /**
   * Creates the listener.
   *
   * @param plugin    the plugin used to schedule the event dispatch on the main thread.
   * @param registry  the registry to resolve entity ids against.
   * @param cooldowns the tracker collapsing duplicate packets.
   * @throws NullPointerException if any argument is {@code null}.
   * @since 1.0.0
   */
  public InteractionListener(
    @NotNull Plugin plugin,
    @NotNull NpcRegistry registry,
    @NotNull CooldownTracker cooldowns
  ) {
    super(PacketListenerPriority.MONITOR);
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.registry = Objects.requireNonNull(registry, "registry");
    this.cooldowns = Objects.requireNonNull(cooldowns, "cooldowns");
  }

  @Override
  public void onPacketPlayReceive(@NotNull PacketPlayReceiveEvent event) {
    if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) {
      return;
    }

    // The player is null while the connection is still in the configuration phase.
    if (!(event.getPlayer() instanceof Player)) {
      return;
    }
    Player player = (Player) event.getPlayer();

    WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);
    Optional<Npc> found = this.registry.byEntityId(packet.getEntityId());
    if (!found.isPresent()) {
      return;
    }
    Npc npc = found.get();

    // Whatever happens next, the server must never see this packet: it refers to an entity that
    // does not exist outside of the client.
    event.setCancelled(true);

    NpcInteractEvent.Action action;
    switch (packet.getAction()) {
      case ATTACK:
        action = NpcInteractEvent.Action.LEFT_CLICK;
        break;
      case INTERACT:
        action = NpcInteractEvent.Action.RIGHT_CLICK;
        break;
      default:
        // INTERACT_AT arrives together with INTERACT and would double the event.
        return;
    }

    EquipmentSlot hand = packet.getHand() == InteractionHand.OFF_HAND
      ? EquipmentSlot.OFF_HAND
      : EquipmentSlot.HAND;
    if (!this.cooldowns.tryAcquire(player.getUniqueId(), npc.entityId(), npc.settings().interactionCooldown())) {
      return;
    }

    this.dispatch(npc, player, action, hand);
  }

  /**
   * Hands the interaction to the main thread and fires the event there.
   *
   * @param npc    the NPC that was clicked.
   * @param player the player that clicked.
   * @param action the kind of click.
   * @param hand   the hand used.
   * @since 1.0.0
   */
  private void dispatch(
    @NotNull Npc npc,
    @NotNull Player player,
    @NotNull NpcInteractEvent.Action action,
    @NotNull EquipmentSlot hand
  ) {
    this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
      // Between the packet and this tick the NPC may have been removed or the player may have left.
      if (npc.isRemoved() || !player.isOnline()) {
        return;
      }

      NpcInteractEvent interactEvent = new NpcInteractEvent(npc, player, action, hand);
      try {
        this.plugin.getServer().getPluginManager().callEvent(interactEvent);
      } catch (Throwable throwable) {
        // A broken listener must not take down the packet pipeline or skip the swing imitation.
        this.plugin.getLogger().log(Level.SEVERE, "Listener threw while handling an NPC interaction", throwable);
      }

      if (!interactEvent.isCancelled() && npc.settings().imitateSwing() && !npc.isRemoved()) {
        npc.playAnimation(
          hand == EquipmentSlot.OFF_HAND ? NpcAnimation.SWING_OFF_HAND : NpcAnimation.SWING_MAIN_ARM,
          player);
      }
    });
  }
}
