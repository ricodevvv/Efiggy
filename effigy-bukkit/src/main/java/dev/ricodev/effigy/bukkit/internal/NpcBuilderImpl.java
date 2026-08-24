/*
 * This file is part of Effigy, licensed under the MIT License.
 * Copyright (c) 2026 ricodevvv and contributors.
 */
package dev.ricodev.effigy.bukkit.internal;

import dev.ricodev.effigy.Npc;
import dev.ricodev.effigy.NpcBuilder;
import dev.ricodev.effigy.bukkit.internal.util.EntityIds;
import dev.ricodev.effigy.bukkit.internal.util.Preconditions;
import dev.ricodev.effigy.bukkit.internal.util.Rotations;
import dev.ricodev.effigy.profile.NpcProfile;
import dev.ricodev.effigy.settings.NpcSettings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The one and only {@link NpcBuilder} implementation.
 *
 * <p>Single use on purpose: reusing a builder is almost always a mistake, because two NPCs built
 * from it would share the settings object and, more importantly, the caller usually forgot to change
 * the location.
 *
 * <p>Not part of the public API.
 *
 * @since 1.0.0
 */
public final class NpcBuilderImpl implements NpcBuilder {

  private final EffigyImpl effigy;
  private final Map<String, Object> metadata = new HashMap<>();
  private final List<String> hologramLines = new ArrayList<>();

  private Location location;
  private NpcProfile profile;
  private NpcSettings settings = NpcSettings.defaults();
  private int entityId = -1;
  private boolean used;

  /**
   * Creates a builder bound to a library instance.
   *
   * @param effigy the instance the built NPC belongs to.
   * @since 1.0.0
   */
  NpcBuilderImpl(@NotNull EffigyImpl effigy) {
    this.effigy = effigy;
  }

  @NotNull
  @Override
  public NpcBuilder location(@NotNull Location location) {
    Objects.requireNonNull(location, "location");
    Preconditions.argument(location.getWorld() != null, "The location must have a world");
    this.location = location.clone();
    this.location.setYaw(Rotations.normalizeYaw(this.location.getYaw()));
    this.location.setPitch(Rotations.clampPitch(this.location.getPitch()));
    return this;
  }

  @NotNull
  @Override
  public NpcBuilder profile(@NotNull NpcProfile profile) {
    this.profile = Objects.requireNonNull(profile, "profile");
    return this;
  }

  @NotNull
  @Override
  public NpcBuilder entityId(int entityId) {
    Preconditions.argument(entityId >= 0, "The entity id must not be negative, got " + entityId);
    this.entityId = entityId;
    return this;
  }

  @NotNull
  @Override
  public NpcBuilder settings(@NotNull NpcSettings settings) {
    this.settings = Objects.requireNonNull(settings, "settings");
    return this;
  }

  @NotNull
  @Override
  public NpcBuilder settings(@NotNull Consumer<NpcSettings.Builder> decorator) {
    Objects.requireNonNull(decorator, "decorator");
    NpcSettings.Builder builder = this.settings.toBuilder();
    decorator.accept(builder);
    this.settings = builder.build();
    return this;
  }

  @NotNull
  @Override
  public NpcBuilder hologram(@NotNull String... lines) {
    Objects.requireNonNull(lines, "lines");
    for (String line : lines) {
      this.hologramLines.add(Objects.requireNonNull(line, "line"));
    }
    return this;
  }

  @NotNull
  @Override
  public NpcBuilder metadata(@NotNull String key, @NotNull Object value) {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(value, "value");
    this.metadata.put(key, value);
    return this;
  }

  @NotNull
  @Override
  public Npc build() {
    Preconditions.mainThread("NpcBuilder#build()");
    Preconditions.state(!this.used, "This builder has already been used");
    Preconditions.state(this.location != null, "A location is required");
    Preconditions.state(this.profile != null, "A profile is required");
    this.used = true;

    int id = this.entityId >= 0 ? this.entityId : EntityIds.next();
    NpcImpl npc = new NpcImpl(this.effigy, id, this.profile, this.location, this.settings);
    npc.metadata().putAll(this.metadata);
    if (!this.hologramLines.isEmpty()) {
      // Set before registering, so the first tracking cycle already spawns the lines.
      npc.hologram().lines(this.hologramLines.toArray(new String[0]));
    }
    this.effigy.onNpcCreated(npc);
    return npc;
  }

  /**
   * Returns the location set so far, for tests.
   *
   * @return the configured location, or {@code null} if none was set.
   * @since 1.0.0
   */
  @Nullable
  Location configuredLocation() {
    return this.location;
  }
}
