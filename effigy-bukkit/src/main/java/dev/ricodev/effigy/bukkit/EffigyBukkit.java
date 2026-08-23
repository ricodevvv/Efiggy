/*
 * This file is part of Effigy, licensed under the MIT License.
 * Copyright (c) 2026 ricodevvv and contributors.
 */
package dev.ricodev.effigy.bukkit;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.settings.PacketEventsSettings;
import com.github.retrooper.packetevents.util.TimeStampMode;
import dev.ricodev.effigy.Effigy;
import dev.ricodev.effigy.bukkit.internal.EffigyImpl;
import dev.ricodev.effigy.bukkit.internal.profile.MojangProfileResolver;
import dev.ricodev.effigy.bukkit.internal.util.Preconditions;
import dev.ricodev.effigy.profile.ProfileResolver;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import java.util.Objects;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Creates {@link Effigy} instances on Paper and Spigot.
 *
 * <p>The whole library is reachable through this class and the interfaces of the API module;
 * everything under {@code internal} is free to change between releases.
 *
 * <h2>Bootstrapping</h2>
 *
 * <p>Effigy is built on PacketEvents, which has to be constructed in {@code onLoad} rather than in
 * {@code onEnable}: it installs a channel handler into the server pipeline and can only do so before
 * the first player is able to connect. Two setups are supported.
 *
 * <p><strong>PacketEvents as a server plugin.</strong> Declare {@code depend: [packetevents]} in
 * {@code plugin.yml} and do nothing else; {@link #create(Plugin)} picks up the running instance.
 *
 * <p><strong>PacketEvents shaded into your plugin.</strong> Call
 * {@link #bootstrapPacketEvents(Plugin)} from {@code onLoad}:
 *
 * <pre>{@code
 * @Override
 * public void onLoad() {
 *   EffigyBukkit.bootstrapPacketEvents(this);
 * }
 *
 * @Override
 * public void onEnable() {
 *   this.effigy = EffigyBukkit.create(this);
 * }
 *
 * @Override
 * public void onDisable() {
 *   if (this.effigy != null) {
 *     this.effigy.close();
 *   }
 * }
 * }</pre>
 *
 * @since 1.0.0
 */
public final class EffigyBukkit {

  private EffigyBukkit() {
    throw new AssertionError("EffigyBukkit is a utility class");
  }

  /**
   * Builds and loads a PacketEvents instance configured for this library.
   *
   * <p>Call from {@code onLoad} and only when PacketEvents is shaded into your plugin. Does nothing
   * if an instance already exists, so it is safe to call even when another plugin got there first.
   *
   * <p>Update checks and packet timestamps are turned off, because neither is useful here and both
   * cost either a request on startup or a few nanoseconds per packet.
   *
   * @param plugin the plugin PacketEvents is bound to.
   * @return the instance that is now current, whether it was created here or already existed.
   * @throws NullPointerException if {@code plugin} is {@code null}.
   * @since 1.0.0
   */
  @NotNull
  public static PacketEventsAPI<?> bootstrapPacketEvents(@NotNull Plugin plugin) {
    Objects.requireNonNull(plugin, "plugin");

    PacketEventsAPI<?> existing = PacketEvents.getAPI();
    if (existing != null) {
      return existing;
    }

    PacketEventsSettings settings = new PacketEventsSettings()
      .checkForUpdates(false)
      .reEncodeByDefault(false)
      .timeStampMode(TimeStampMode.NONE);
    PacketEventsAPI<?> api = SpigotPacketEventsBuilder.build(plugin, settings);
    PacketEvents.setAPI(api);
    api.load();
    return api;
  }

  /**
   * Creates an instance backed by the default, caching Mojang profile resolver.
   *
   * @param plugin the plugin that owns the instance.
   * @return a started instance; close it in {@code onDisable}.
   * @throws NullPointerException  if {@code plugin} is {@code null}.
   * @throws IllegalStateException if PacketEvents is missing or was never initialised, or if this is
   *     called off the main thread.
   * @since 1.0.0
   */
  @NotNull
  @Contract("_ -> new")
  public static Effigy create(@NotNull Plugin plugin) {
    return create(plugin, null);
  }

  /**
   * Creates an instance backed by a resolver of your own.
   *
   * <p>Pass a custom resolver when skins come from a local database, a proxy or a texture service
   * rather than from Mojang. The resolver is not closed on shutdown; it belongs to the caller.
   *
   * @param plugin   the plugin that owns the instance.
   * @param profiles the resolver to use, or {@code null} for the default Mojang resolver.
   * @return a started instance; close it in {@code onDisable}.
   * @throws NullPointerException  if {@code plugin} is {@code null}.
   * @throws IllegalStateException if PacketEvents is missing or was never initialised, or if this is
   *     called off the main thread.
   * @since 1.0.0
   */
  @NotNull
  @Contract("_, _ -> new")
  public static Effigy create(@NotNull Plugin plugin, @Nullable ProfileResolver profiles) {
    Objects.requireNonNull(plugin, "plugin");
    Preconditions.mainThread("EffigyBukkit#create(Plugin)");

    PacketEventsAPI<?> api = PacketEvents.getAPI();
    Preconditions.state(api != null, "PacketEvents is not available. Either depend on the PacketEvents "
      + "plugin or call EffigyBukkit#bootstrapPacketEvents(Plugin) from your onLoad method.");
    if (!api.isLoaded()) {
      // Loading here would already be too late for the channel injector, so fail loudly instead of
      // handing back an instance whose interaction handling silently does nothing.
      throw new IllegalStateException("PacketEvents was built but never loaded. Call load() on it, or "
        + "EffigyBukkit#bootstrapPacketEvents(Plugin), from onLoad rather than onEnable.");
    }
    if (!api.isInitialized()) {
      api.init();
    }

    if (profiles != null) {
      return new EffigyImpl(plugin, api, profiles, null);
    }
    MojangProfileResolver resolver = new MojangProfileResolver(api);
    return new EffigyImpl(plugin, api, resolver, resolver);
  }
}
