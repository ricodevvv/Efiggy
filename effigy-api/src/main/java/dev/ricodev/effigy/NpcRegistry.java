/*
 * This file is part of Effigy, licensed under the MIT License.
 * Copyright (c) 2026 ricodevvv and contributors.
 */
package dev.ricodev.effigy;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.UnmodifiableView;

/**
 * Holds every NPC created by one {@link Effigy} instance and answers lookups by id.
 *
 * <p>The registry is the bridge between the protocol layer and the API: when an interaction packet
 * arrives it carries nothing but an entity id, and {@link #byEntityId(int)} is what turns that
 * number back into an {@link Npc}. Lookups are backed by hash maps and are cheap enough to run on
 * the netty thread.
 *
 * <p><strong>Threading.</strong> Every method is thread-safe. Iterating a returned collection while
 * another thread registers or removes NPCs is safe and will not throw, but may or may not observe
 * the concurrent change.
 *
 * @since 1.0.0
 */
public interface NpcRegistry {

  /**
   * Looks up an NPC by its protocol entity id.
   *
   * @param entityId the entity id to look for.
   * @return the matching NPC, or an empty optional if no NPC uses that id.
   * @since 1.0.0
   */
  @NotNull
  Optional<Npc> byEntityId(int entityId);

  /**
   * Looks up an NPC by its unique id.
   *
   * @param uniqueId the unique id to look for.
   * @return the matching NPC, or an empty optional if no NPC uses that id.
   * @throws NullPointerException if {@code uniqueId} is {@code null}.
   * @since 1.0.0
   */
  @NotNull
  Optional<Npc> byUniqueId(@NotNull UUID uniqueId);

  /**
   * Returns every registered NPC.
   *
   * @return an unmodifiable view of all NPCs of this registry.
   * @since 1.0.0
   */
  @NotNull
  @UnmodifiableView
  Collection<Npc> all();

  /**
   * Returns every registered NPC that stands in the given world.
   *
   * @param world the world to filter by.
   * @return a newly allocated list of the matching NPCs.
   * @throws NullPointerException if {@code world} is {@code null}.
   * @since 1.0.0
   */
  @NotNull
  Collection<Npc> inWorld(@NotNull World world);

  /**
   * Returns every NPC the given player currently sees.
   *
   * @param player the player to collect the NPCs of.
   * @return a newly allocated list of the NPCs spawned for the player.
   * @throws NullPointerException if {@code player} is {@code null}.
   * @since 1.0.0
   */
  @NotNull
  Collection<Npc> viewedBy(@NotNull Player player);

  /**
   * Returns the number of registered NPCs.
   *
   * @return the registry size.
   * @since 1.0.0
   */
  int size();

  /**
   * Removes every NPC of this registry, despawning them for all viewers.
   *
   * @throws IllegalStateException if called off the main thread.
   * @since 1.0.0
   */
  void removeAll();
}
