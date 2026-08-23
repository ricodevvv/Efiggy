/*
 * This file is part of Effigy, licensed under the MIT License.
 * Copyright (c) 2026 ricodevvv and contributors.
 */
package dev.ricodev.effigy;

import dev.ricodev.effigy.profile.ProfileResolver;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * Entry point of the library: one instance owns the NPCs, listeners and tasks of one plugin.
 *
 * <p>Create an instance in {@code onEnable} and close it in {@code onDisable}:
 *
 * <pre>{@code
 * public final class MyPlugin extends JavaPlugin {
 *
 *   private Effigy effigy;
 *
 *   @Override
 *   public void onLoad() {
 *     // PacketEvents must be built and loaded before any world exists.
 *     EffigyBukkit.bootstrapPacketEvents(this);
 *   }
 *
 *   @Override
 *   public void onEnable() {
 *     this.effigy = EffigyBukkit.create(this);
 *   }
 *
 *   @Override
 *   public void onDisable() {
 *     this.effigy.close();
 *   }
 * }
 * }</pre>
 *
 * <p>Instances are independent. Two plugins that each create their own instance cannot see or
 * accidentally remove each other's NPCs, and unloading one of them leaves the other untouched.
 *
 * <p><strong>Threading.</strong> All methods are safe to call from any thread; the objects they
 * return document their own requirements.
 *
 * @since 1.0.0
 */
public interface Effigy extends AutoCloseable {

  /**
   * Returns the plugin that owns this instance.
   *
   * @return the owning plugin.
   * @since 1.0.0
   */
  @NotNull
  Plugin plugin();

  /**
   * Returns the registry holding every NPC created by this instance.
   *
   * @return the NPC registry.
   * @since 1.0.0
   */
  @NotNull
  NpcRegistry registry();

  /**
   * Returns the resolver used to turn names and unique ids into skinned profiles.
   *
   * @return the profile resolver.
   * @since 1.0.0
   */
  @NotNull
  ProfileResolver profiles();

  /**
   * Starts building a new NPC.
   *
   * <p>The returned builder is not thread-safe and is meant to be used once and thrown away.
   *
   * @return a fresh NPC builder bound to this instance.
   * @since 1.0.0
   */
  @NotNull
  NpcBuilder npc();

  /**
   * Returns whether this instance has been closed.
   *
   * @return {@code true} if {@link #close()} has already run.
   * @since 1.0.0
   */
  boolean isClosed();

  /**
   * Removes every NPC, unregisters all listeners and stops the tracking task.
   *
   * <p>Idempotent. Effigy also closes itself when the owning plugin is disabled, so forgetting this
   * call leaks nothing, but calling it explicitly makes the shutdown order deterministic.
   *
   * @throws IllegalStateException if called off the main thread.
   * @since 1.0.0
   */
  @Override
  void close();
}
