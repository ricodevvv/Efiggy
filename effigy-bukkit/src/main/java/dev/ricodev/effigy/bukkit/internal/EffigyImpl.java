/*
 * This file is part of Effigy, licensed under the MIT License.
 * Copyright (c) 2026 ricodevvv and contributors.
 */
package dev.ricodev.effigy.bukkit.internal;

import com.github.retrooper.packetevents.PacketEventsAPI;
import dev.ricodev.effigy.Effigy;
import dev.ricodev.effigy.Npc;
import dev.ricodev.effigy.NpcBuilder;
import dev.ricodev.effigy.NpcRegistry;
import dev.ricodev.effigy.bukkit.internal.hologram.AnimationHub;
import dev.ricodev.effigy.bukkit.internal.hologram.HologramImpl;
import dev.ricodev.effigy.bukkit.internal.listener.ConnectionListener;
import dev.ricodev.effigy.bukkit.internal.protocol.InteractionListener;
import dev.ricodev.effigy.bukkit.internal.protocol.PacketBridge;
import dev.ricodev.effigy.bukkit.internal.task.TrackingTask;
import dev.ricodev.effigy.bukkit.internal.util.CooldownTracker;
import dev.ricodev.effigy.bukkit.internal.util.Preconditions;
import dev.ricodev.effigy.profile.ProfileResolver;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The one and only {@link Effigy} implementation, and the owner of everything else.
 *
 * <p>Constructing it wires the whole library together: the packet bridge, the registry, the netty
 * side interaction listener, the Bukkit side connection listener and the tracking task.
 * {@link #close()} takes all of that apart again in the reverse order, so no packet is sent after
 * the plugin has said goodbye.
 *
 * <p>Not part of the public API.
 *
 * @since 1.0.0
 */
public final class EffigyImpl implements Effigy, AnimationHub {

  private final Plugin plugin;
  private final PacketBridge packets;
  private final NpcRegistryImpl registry = new NpcRegistryImpl();
  private final CooldownTracker cooldowns = new CooldownTracker();
  private final ProfileResolver profiles;
  private final AutoCloseable ownedResolver;

  private final InteractionListener interactionListener;
  private final ConnectionListener connectionListener;
  private final BukkitTask trackingTask;
  private final PacketEventsAPI<?> packetEvents;

  private final Set<HologramImpl> animatedHolograms = ConcurrentHashMap.newKeySet();
  private volatile BukkitTask animationTask;
  private volatile boolean closed;

  /**
   * Wires up an instance and starts it.
   *
   * @param plugin        the owning plugin.
   * @param packetEvents  the initialised packet library instance.
   * @param profiles      the resolver to hand out through {@link #profiles()}.
   * @param ownedResolver the resolver to close on shutdown, or {@code null} if the caller owns it.
   * @throws NullPointerException if {@code plugin}, {@code packetEvents} or {@code profiles} is
   *     {@code null}.
   * @since 1.0.0
   */
  public EffigyImpl(
    @NotNull Plugin plugin,
    @NotNull PacketEventsAPI<?> packetEvents,
    @NotNull ProfileResolver profiles,
    @Nullable AutoCloseable ownedResolver
  ) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.packetEvents = Objects.requireNonNull(packetEvents, "packetEvents");
    this.profiles = Objects.requireNonNull(profiles, "profiles");
    this.ownedResolver = ownedResolver;
    this.packets = new PacketBridge(packetEvents);

    this.interactionListener = new InteractionListener(plugin, this.registry, this.cooldowns);
    packetEvents.getEventManager().registerListener(this.interactionListener);

    this.connectionListener = new ConnectionListener(this);
    plugin.getServer().getPluginManager().registerEvents(this.connectionListener, plugin);

    this.trackingTask = plugin.getServer().getScheduler().runTaskTimer(
      plugin,
      new TrackingTask(plugin, this.registry),
      TrackingTask.INTERVAL_TICKS,
      TrackingTask.INTERVAL_TICKS);
  }

  @NotNull
  @Override
  public Plugin plugin() {
    return this.plugin;
  }

  @NotNull
  @Override
  public NpcRegistry registry() {
    return this.registry;
  }

  @NotNull
  @Override
  public ProfileResolver profiles() {
    return this.profiles;
  }

  @NotNull
  @Override
  public NpcBuilder npc() {
    Preconditions.state(!this.closed, "This Effigy instance has been closed");
    return new NpcBuilderImpl(this);
  }

  @Override
  public boolean isClosed() {
    return this.closed;
  }

  @Override
  public void close() {
    Preconditions.mainThread("Effigy#close()");
    if (this.closed) {
      return;
    }
    this.closed = true;

    // Stop producing work first, then drain what is left, then release the resources.
    this.trackingTask.cancel();
    this.stopAnimationTask();
    HandlerList.unregisterAll(this.connectionListener);
    this.packetEvents.getEventManager().unregisterListener(this.interactionListener);

    this.registry.removeAll();
    this.cooldowns.clear();

    if (this.ownedResolver != null) {
      try {
        this.ownedResolver.close();
      } catch (Exception exception) {
        this.plugin.getLogger().log(Level.WARNING, "Failed to close the profile resolver", exception);
      }
    }
  }

  /**
   * Returns the packet bridge shared by every NPC of this instance.
   *
   * @return the packet bridge.
   * @since 1.0.0
   */
  @NotNull
  public PacketBridge packets() {
    return this.packets;
  }

  /**
   * Fires a Bukkit event, isolating the caller from a listener that throws.
   *
   * @param event the event to fire.
   * @since 1.0.0
   */
  public void callEvent(@NotNull Event event) {
    try {
      this.plugin.getServer().getPluginManager().callEvent(event);
    } catch (Throwable throwable) {
      this.plugin.getLogger().log(
        Level.SEVERE, "Listener threw while handling " + event.getEventName(), throwable);
    }
  }

  /**
   * Runs a task on the main thread after a delay.
   *
   * @param delayTicks how many ticks to wait; {@code 0} runs on the next tick.
   * @param task       the task to run.
   * @since 1.0.0
   */
  public void scheduleDelayed(int delayTicks, @NotNull Runnable task) {
    if (this.closed || !this.plugin.isEnabled()) {
      return;
    }
    this.plugin.getServer().getScheduler().runTaskLater(this.plugin, task, Math.max(1L, delayTicks));
  }

  /**
   * Registers a freshly built NPC.
   *
   * @param npc the NPC to register.
   * @since 1.0.0
   */
  void onNpcCreated(@NotNull NpcImpl npc) {
    Preconditions.state(!this.closed, "This Effigy instance has been closed");
    this.registry.register(npc);
  }

  /**
   * Unregisters an NPC that removed itself.
   *
   * @param npc the removed NPC.
   * @since 1.0.0
   */
  void onNpcRemoved(@NotNull NpcImpl npc) {
    this.registry.unregister(npc);
    this.cooldowns.forgetNpc(npc.entityId());
  }

  @Override
  public void setAnimating(@NotNull HologramImpl hologram, boolean animating) {
    if (this.closed) {
      return;
    }

    if (animating) {
      // Start the task on the first animated hologram rather than on enable, so a server whose
      // holograms are all static never schedules anything.
      if (this.animatedHolograms.add(hologram) && this.animationTask == null) {
        this.startAnimationTask();
      }
    } else if (this.animatedHolograms.remove(hologram) && this.animatedHolograms.isEmpty()) {
      this.stopAnimationTask();
    }
  }

  /**
   * Schedules the per tick task that advances hologram animations.
   *
   * @since 1.0.0
   */
  private synchronized void startAnimationTask() {
    if (this.animationTask != null || this.closed) {
      return;
    }
    this.animationTask = this.plugin.getServer().getScheduler().runTaskTimer(
      this.plugin,
      () -> {
        for (HologramImpl hologram : this.animatedHolograms) {
          try {
            hologram.tickAnimations();
          } catch (Throwable throwable) {
            this.plugin.getLogger().log(
              Level.SEVERE, "Failed to advance a hologram animation", throwable);
          }
        }
      },
      1L,
      1L);
  }

  /**
   * Cancels the animation task if it is running.
   *
   * @since 1.0.0
   */
  private synchronized void stopAnimationTask() {
    BukkitTask task = this.animationTask;
    if (task != null) {
      task.cancel();
      this.animationTask = null;
    }
  }

  /**
   * Drops every trace of a player that disconnected.
   *
   * @param player the player that left.
   * @since 1.0.0
   */
  public void forgetPlayer(@NotNull Player player) {
    for (Npc npc : this.registry.all()) {
      if (npc instanceof NpcImpl) {
        ((NpcImpl) npc).forgetViewer(player);
      }
    }
    this.cooldowns.forget(player.getUniqueId());
  }

  /**
   * Drops a single viewer of a single NPC without sending a despawn packet.
   *
   * @param npc    the NPC to update.
   * @param player the player to forget.
   * @since 1.0.0
   */
  public void forgetViewer(@NotNull Npc npc, @NotNull Player player) {
    if (npc instanceof NpcImpl) {
      ((NpcImpl) npc).forgetViewer(player);
    }
  }
}
