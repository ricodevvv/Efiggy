/*
 * This file is part of Effigy, licensed under the MIT License.
 * Copyright (c) 2026 ricodevvv and contributors.
 */
package dev.ricodev.effigy;

import dev.ricodev.effigy.hologram.NpcHologram;
import dev.ricodev.effigy.profile.NpcProfile;
import dev.ricodev.effigy.settings.NpcSettings;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Fluent builder for {@link Npc} instances.
 *
 * <p>A location and a profile are mandatory; everything else has a documented default. Because
 * resolving a skin hits the Mojang API, the profile has to be resolved before the NPC is built:
 *
 * <pre>{@code
 * effigy.profiles().resolveByName("Notch").thenAccept(profile ->
 *     Bukkit.getScheduler().runTask(plugin, () -> effigy.npc()
 *         .location(spawn)
 *         .profile(profile)
 *         .settings(settings -> settings
 *             .viewDistance(32)
 *             .lookAtViewer(true))
 *         .build()));
 * }</pre>
 *
 * <p>Builders are single use and not thread-safe. {@link #build()} may only be called once and only
 * from the server main thread.
 *
 * @since 1.0.0
 */
public interface NpcBuilder {

  /**
   * Sets where the NPC will stand.
   *
   * <p>The location is copied, including its yaw and pitch, which become the initial rotation of
   * the NPC.
   *
   * @param location the spawn location.
   * @return this builder.
   * @throws NullPointerException     if {@code location} is {@code null}.
   * @throws IllegalArgumentException if the location has no world.
   * @since 1.0.0
   */
  @NotNull
  @Contract("_ -> this")
  NpcBuilder location(@NotNull Location location);

  /**
   * Sets the profile that determines the name and skin of the NPC.
   *
   * @param profile the resolved profile to use.
   * @return this builder.
   * @throws NullPointerException if {@code profile} is {@code null}.
   * @see dev.ricodev.effigy.profile.ProfileResolver
   * @since 1.0.0
   */
  @NotNull
  @Contract("_ -> this")
  NpcBuilder profile(@NotNull NpcProfile profile);

  /**
   * Forces the NPC to use a specific entity id instead of an automatically allocated one.
   *
   * <p>Only useful when reproducing a fixed protocol state, for example in tests. Passing an id that
   * a real entity already uses corrupts the client state of every viewer, so leave this alone unless
   * you know the id is free.
   *
   * @param entityId the entity id to use.
   * @return this builder.
   * @throws IllegalArgumentException if {@code entityId} is negative.
   * @since 1.0.0
   */
  @NotNull
  @Contract("_ -> this")
  NpcBuilder entityId(int entityId);

  /**
   * Replaces the settings of the NPC wholesale.
   *
   * <p>Mutually exclusive with {@link #settings(Consumer)}; the last call wins.
   *
   * @param settings the settings to use.
   * @return this builder.
   * @throws NullPointerException if {@code settings} is {@code null}.
   * @since 1.0.0
   */
  @NotNull
  @Contract("_ -> this")
  NpcBuilder settings(@NotNull NpcSettings settings);

  /**
   * Adjusts the settings of the NPC, starting from {@link NpcSettings#defaults()}.
   *
   * @param decorator a callback that receives a settings builder to modify.
   * @return this builder.
   * @throws NullPointerException if {@code decorator} is {@code null}.
   * @since 1.0.0
   */
  @NotNull
  @Contract("_ -> this")
  NpcBuilder settings(@NotNull Consumer<NpcSettings.Builder> decorator);

  /**
   * Gives the NPC a hologram with the given static lines.
   *
   * <p>Shorthand for calling {@link NpcHologram#lines(String...)} on the built NPC. Use
   * {@link Npc#hologram()} afterwards for per viewer text, spacing or offset.
   *
   * @param lines the lines to show, the first one being the topmost.
   * @return this builder.
   * @throws NullPointerException if {@code lines} is {@code null} or contains {@code null}.
   * @since 1.0.0
   */
  @NotNull
  @Contract("_ -> this")
  NpcBuilder hologram(@NotNull String... lines);

  /**
   * Attaches a value to the {@link Npc#metadata() metadata map} of the NPC before it is registered.
   *
   * @param key   the metadata key.
   * @param value the value to store.
   * @return this builder.
   * @throws NullPointerException if any argument is {@code null}.
   * @since 1.0.0
   */
  @NotNull
  @Contract("_, _ -> this")
  NpcBuilder metadata(@NotNull String key, @NotNull Object value);

  /**
   * Builds the NPC and registers it.
   *
   * <p>The NPC is immediately picked up by the tracking task, so nearby players see it within a tick
   * without any further call.
   *
   * @return the newly created NPC.
   * @throws IllegalStateException if a location or profile is missing, if this builder was already
   *     used, if the owning {@link Effigy} instance is closed, or if called off the main thread.
   * @since 1.0.0
   */
  @NotNull
  Npc build();
}
