/*
 * This file is part of Effigy, licensed under the MIT License.
 * Copyright (c) 2026 ricodevvv and contributors.
 */
package dev.ricodev.effigy.bukkit.internal;

import dev.ricodev.effigy.Npc;
import dev.ricodev.effigy.NpcRegistry;
import dev.ricodev.effigy.bukkit.internal.util.Preconditions;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.UnmodifiableView;

/**
 * Hash map backed {@link NpcRegistry}.
 *
 * <p>Two indexes are kept because both lookups sit on a hot path: the entity id is what an incoming
 * interaction packet carries, and the unique id is what plugins persist. Both maps are concurrent,
 * so the netty thread can read them while the main thread registers a new NPC.
 *
 * <p>Not part of the public API.
 *
 * @since 1.0.0
 */
public final class NpcRegistryImpl implements NpcRegistry {

  private final Map<Integer, Npc> byEntityId = new ConcurrentHashMap<>();
  private final Map<UUID, Npc> byUniqueId = new ConcurrentHashMap<>();
  private final Collection<Npc> view = Collections.unmodifiableCollection(this.byEntityId.values());

  @NotNull
  @Override
  public Optional<Npc> byEntityId(int entityId) {
    return Optional.ofNullable(this.byEntityId.get(entityId));
  }

  @NotNull
  @Override
  public Optional<Npc> byUniqueId(@NotNull UUID uniqueId) {
    Objects.requireNonNull(uniqueId, "uniqueId");
    return Optional.ofNullable(this.byUniqueId.get(uniqueId));
  }

  @NotNull
  @Override
  @UnmodifiableView
  public Collection<Npc> all() {
    return this.view;
  }

  @NotNull
  @Override
  public Collection<Npc> inWorld(@NotNull World world) {
    Objects.requireNonNull(world, "world");
    List<Npc> result = new ArrayList<>();
    for (Npc npc : this.byEntityId.values()) {
      if (!npc.isRemoved() && world.equals(npc.location().getWorld())) {
        result.add(npc);
      }
    }
    return result;
  }

  @NotNull
  @Override
  public Collection<Npc> viewedBy(@NotNull Player player) {
    Objects.requireNonNull(player, "player");
    List<Npc> result = new ArrayList<>();
    for (Npc npc : this.byEntityId.values()) {
      if (npc.isViewer(player)) {
        result.add(npc);
      }
    }
    return result;
  }

  @Override
  public int size() {
    return this.byEntityId.size();
  }

  @Override
  public void removeAll() {
    Preconditions.mainThread("NpcRegistry#removeAll()");
    // Copy first: remove() calls back into this registry to unregister itself.
    for (Npc npc : new ArrayList<>(this.byEntityId.values())) {
      npc.remove();
    }
  }

  /**
   * Adds an NPC to both indexes.
   *
   * @param npc the NPC to register.
   * @throws IllegalStateException if the entity id or the unique id is already taken.
   * @since 1.0.0
   */
  void register(@NotNull NpcImpl npc) {
    Npc previous = this.byEntityId.putIfAbsent(npc.entityId(), npc);
    Preconditions.state(previous == null, "Entity id " + npc.entityId() + " is already in use");

    previous = this.byUniqueId.putIfAbsent(npc.uniqueId(), npc);
    if (previous != null) {
      this.byEntityId.remove(npc.entityId(), npc);
      throw new IllegalStateException("Unique id " + npc.uniqueId() + " is already in use");
    }
  }

  /**
   * Removes an NPC from both indexes.
   *
   * @param npc the NPC to unregister.
   * @since 1.0.0
   */
  void unregister(@NotNull NpcImpl npc) {
    this.byEntityId.remove(npc.entityId(), npc);
    this.byUniqueId.remove(npc.uniqueId(), npc);
  }
}
