/*
 * This file is part of Effigy, licensed under the MIT License.
 * Copyright (c) 2026 ricodevvv and contributors.
 */
package dev.ricodev.effigy.profile;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Turns a name, a unique id or an online player into a profile complete with skin textures.
 *
 * <p>The default implementation talks to the Mojang session API and caches its answers, because
 * those endpoints are rate limited to roughly one request per profile per minute and will start
 * returning HTTP 429 long before a busy lobby has finished spawning its NPCs. Plugins that keep
 * their own skin database should implement this interface and pass it to the Effigy builder instead.
 *
 * <p><strong>Threading.</strong> Implementations must never block the calling thread; all methods
 * return immediately and complete their future on some other thread. The returned futures therefore
 * complete <em>off</em> the main thread, so hop back before touching the Bukkit API:
 *
 * <pre>{@code
 * resolver.resolveByName("Notch")
 *     .thenAcceptAsync(profile -> effigy.npc().location(loc).profile(profile).build(),
 *                      runnable -> Bukkit.getScheduler().runTask(plugin, runnable));
 * }</pre>
 *
 * @since 1.0.0
 */
@FunctionalInterface
public interface ProfileResolver {

  /**
   * Resolves the profile belonging to the given unique id.
   *
   * @param uniqueId the unique id to look up.
   * @return a future completed with the resolved profile, or completed exceptionally with a
   *     {@link CompletionException} if the profile does not exist or the lookup failed.
   * @throws NullPointerException if {@code uniqueId} is {@code null}.
   * @since 1.0.0
   */
  @NotNull
  CompletableFuture<NpcProfile> resolve(@NotNull UUID uniqueId);

  /**
   * Resolves the profile belonging to the given name.
   *
   * <p>Names are resolved to a unique id first, so this costs one extra request compared to
   * {@link #resolve(UUID)}. The default implementation performs both steps and caches both results.
   *
   * @param name the name to look up.
   * @return a future completed with the resolved profile, or completed exceptionally if no account
   *     with that name exists or the lookup failed.
   * @throws NullPointerException     if {@code name} is {@code null}.
   * @throws IllegalArgumentException if {@code name} cannot be a Minecraft name.
   * @since 1.0.0
   */
  @NotNull
  default CompletableFuture<NpcProfile> resolveByName(@NotNull String name) {
    CompletableFuture<NpcProfile> failed = new CompletableFuture<>();
    failed.completeExceptionally(new UnsupportedOperationException(
      this.getClass().getName() + " cannot resolve profiles by name"));
    return failed;
  }

  /**
   * Reads the profile of a player that is currently online.
   *
   * <p>No network request is involved: the server already holds the authenticated profile of every
   * connected player. The returned future is therefore already completed, and its result carries a
   * fresh random unique id so that the NPC does not collide with the real player on the client.
   *
   * @param player the online player to copy.
   * @return a completed future holding a profile with the skin of the player.
   * @throws NullPointerException if {@code player} is {@code null}.
   * @since 1.0.0
   */
  @NotNull
  default CompletableFuture<NpcProfile> resolve(@NotNull Player player) {
    CompletableFuture<NpcProfile> failed = new CompletableFuture<>();
    failed.completeExceptionally(new UnsupportedOperationException(
      this.getClass().getName() + " cannot resolve profiles of online players"));
    return failed;
  }
}
