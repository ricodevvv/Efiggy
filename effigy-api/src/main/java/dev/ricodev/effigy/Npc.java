/*
 * This file is part of Effigy, licensed under the MIT License.
 * Copyright (c) 2026 ricodevvv and contributors.
 */
package dev.ricodev.effigy;

import dev.ricodev.effigy.hologram.NpcHologram;
import dev.ricodev.effigy.profile.NpcProfile;
import dev.ricodev.effigy.protocol.NpcAnimation;
import dev.ricodev.effigy.protocol.NpcPose;
import dev.ricodev.effigy.protocol.SkinLayer;
import dev.ricodev.effigy.settings.NpcSettings;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

/**
 * A client-side player entity that exists purely as a stream of packets.
 *
 * <p>An NPC has no counterpart on the server: it never appears in {@link World#getEntities()}, it is
 * not ticked, it does not collide, and it is invisible to any plugin that is not aware of Effigy.
 * Everything a viewer sees is produced by this object sending packets to that specific viewer,
 * which is what makes NPCs cheap enough to place thousands of them on a lobby server.
 *
 * <p><strong>Viewers.</strong> Every NPC keeps its own set of viewers, the players that currently
 * have the entity spawned on their client. Viewers are normally managed automatically: a background
 * task shows the NPC to players that come within {@link NpcSettings#viewDistance()} and are accepted
 * by the {@link NpcSettings#visibilityRule() visibility rule}, and hides it again when they leave.
 * {@link #show(Player)} and {@link #hide(Player)} exist for the cases where the automatic behaviour
 * is not enough, but note that the tracking task may undo a manual change on its next run unless the
 * visibility rule agrees with it.
 *
 * <p><strong>Threading.</strong> Read-only methods are safe to call from any thread. Every method
 * that mutates the NPC or sends packets must be called from the server main thread and will throw
 * {@link IllegalStateException} otherwise. This mirrors how Bukkit entities behave and keeps the
 * events fired by this library synchronous, so listeners can touch the Bukkit API freely.
 *
 * <p><strong>Lifetime.</strong> An NPC stays usable until {@link #remove()} is called or the owning
 * {@link Effigy} instance is closed. Every method other than {@link #isRemoved()} throws
 * {@link IllegalStateException} once the NPC has been removed, so a stale reference fails loudly
 * instead of silently doing nothing.
 *
 * @see NpcBuilder
 * @see NpcRegistry
 * @since 1.0.0
 */
public interface Npc {

  /**
   * Returns the entity id this NPC uses in packets.
   *
   * <p>The id is unique among the entities of the server for as long as the NPC exists and is the
   * key used to correlate incoming interaction packets back to this NPC.
   *
   * @return the protocol level entity id.
   * @since 1.0.0
   */
  int entityId();

  /**
   * Returns the unique id sent to clients for this NPC.
   *
   * <p>This is the {@linkplain NpcProfile#uniqueId() unique id of the profile} the NPC was created
   * with. It never changes, not even when {@link #profile(NpcProfile)} replaces the skin, because
   * clients use it to correlate the tab list entry with the spawned entity.
   *
   * @return the unique id of this NPC.
   * @since 1.0.0
   */
  @NotNull
  UUID uniqueId();

  /**
   * Returns the profile that is currently sent to new viewers.
   *
   * @return the current profile, never {@code null}.
   * @since 1.0.0
   */
  @NotNull
  NpcProfile profile();

  /**
   * Replaces the profile of this NPC, changing its skin and name.
   *
   * <p>Clients cannot update the skin of an entity that is already spawned, so this respawns the NPC
   * for every current viewer. The unique id of the given profile is ignored in favour of
   * {@link #uniqueId()}; only the name and the texture properties are taken over.
   *
   * @param profile the profile to use from now on.
   * @throws NullPointerException  if {@code profile} is {@code null}.
   * @throws IllegalStateException if called off the main thread or after {@link #remove()}.
   * @since 1.0.0
   */
  void profile(@NotNull NpcProfile profile);

  /**
   * Returns a copy of the location this NPC stands at.
   *
   * <p>The returned location is a defensive copy; mutating it has no effect on the NPC. Use
   * {@link #teleport(Location)} to move it.
   *
   * @return a copy of the current location.
   * @since 1.0.0
   */
  @NotNull
  Location location();

  /**
   * Returns the world this NPC lives in.
   *
   * @return the world of {@link #location()}.
   * @since 1.0.0
   */
  @NotNull
  World world();

  /**
   * Returns the settings that control how this NPC is tracked and displayed.
   *
   * @return the immutable settings of this NPC.
   * @since 1.0.0
   */
  @NotNull
  NpcSettings settings();

  /**
   * Replaces the settings of this NPC.
   *
   * <p>The new settings take effect on the next run of the tracking task. Viewers that no longer
   * satisfy the new {@link NpcSettings#visibilityRule() visibility rule} or view distance are hidden
   * at that point rather than immediately.
   *
   * @param settings the settings to apply.
   * @throws NullPointerException  if {@code settings} is {@code null}.
   * @throws IllegalStateException if called off the main thread or after {@link #remove()}.
   * @since 1.0.0
   */
  void settings(@NotNull NpcSettings settings);

  /**
   * Returns the players that currently have this NPC spawned on their client.
   *
   * <p>The returned collection is an unmodifiable live view: it reflects later changes to the viewer
   * set and must not be iterated while showing or hiding the NPC. Copy it first if you need a
   * stable snapshot.
   *
   * @return an unmodifiable view of the current viewers.
   * @since 1.0.0
   */
  @NotNull
  @UnmodifiableView
  Collection<Player> viewers();

  /**
   * Checks whether the given player currently sees this NPC.
   *
   * @param player the player to check.
   * @return {@code true} if the NPC is spawned for the player.
   * @throws NullPointerException if {@code player} is {@code null}.
   * @since 1.0.0
   */
  boolean isViewer(@NotNull Player player);

  /**
   * Checks whether the given player is allowed to see this NPC.
   *
   * <p>This only asks the {@linkplain NpcSettings#visibilityRule() visibility rule}; it does not
   * take distance, world or the current viewer set into account.
   *
   * @param player the player to test.
   * @return {@code true} if the visibility rule accepts the player.
   * @throws NullPointerException if {@code player} is {@code null}.
   * @since 1.0.0
   */
  boolean canSee(@NotNull Player player);

  /**
   * Spawns this NPC for the given player, bypassing the distance check.
   *
   * <p>Fires {@link dev.ricodev.effigy.event.NpcShowEvent} before any packet is sent; if a listener
   * cancels the event, nothing happens and {@code false} is returned. The visibility rule is
   * <em>not</em> consulted, which makes this the right method for temporarily revealing an NPC to a
   * player the rule would normally reject.
   *
   * @param player the player to spawn the NPC for.
   * @return {@code true} if the NPC was spawned, {@code false} if the player already saw it or the
   *     event was cancelled.
   * @throws NullPointerException  if {@code player} is {@code null}.
   * @throws IllegalStateException if called off the main thread or after {@link #remove()}.
   * @since 1.0.0
   */
  boolean show(@NotNull Player player);

  /**
   * Despawns this NPC for the given player.
   *
   * <p>Fires {@link dev.ricodev.effigy.event.NpcHideEvent} before any packet is sent; if a listener
   * cancels the event, nothing happens and {@code false} is returned.
   *
   * @param player the player to despawn the NPC for.
   * @return {@code true} if the NPC was despawned, {@code false} if the player did not see it or the
   *     event was cancelled.
   * @throws NullPointerException  if {@code player} is {@code null}.
   * @throws IllegalStateException if called off the main thread or after {@link #remove()}.
   * @since 1.0.0
   */
  boolean hide(@NotNull Player player);

  /**
   * Despawns and immediately respawns this NPC for every current viewer.
   *
   * <p>Needed after changes that clients only read at spawn time, such as a new profile. The show
   * and hide events are not fired, because from the point of view of a plugin the viewer set does
   * not change.
   *
   * @throws IllegalStateException if called off the main thread or after {@link #remove()}.
   * @since 1.0.0
   */
  void respawn();

  /**
   * Moves this NPC to the given location.
   *
   * <p>Moves within the same world are sent as a teleport packet, which clients apply instantly
   * rather than interpolating. Moving the NPC into a different world drops every viewer, because no
   * packet moves an entity across worlds; the tracking task then rebuilds the viewer set from the
   * players of the new world on its next cycle.
   *
   * @param location the target location, including the yaw and pitch to apply.
   * @throws NullPointerException     if {@code location} is {@code null}.
   * @throws IllegalArgumentException if the location has no world.
   * @throws IllegalStateException    if called off the main thread or after {@link #remove()}.
   * @since 1.0.0
   */
  void teleport(@NotNull Location location);

  /**
   * Rotates the head and body of this NPC.
   *
   * <p>Values are normalised, so passing {@code 400} as the yaw is the same as passing {@code 40}.
   *
   * @param yaw   the horizontal rotation in degrees.
   * @param pitch the vertical rotation in degrees, clamped to {@code [-90, 90]}.
   * @throws IllegalStateException if called off the main thread or after {@link #remove()}.
   * @since 1.0.0
   */
  void rotate(float yaw, float pitch);

  /**
   * Rotates this NPC so that it faces the given location.
   *
   * @param target the location to look at.
   * @throws NullPointerException  if {@code target} is {@code null}.
   * @throws IllegalStateException if called off the main thread or after {@link #remove()}.
   * @since 1.0.0
   */
  void lookAt(@NotNull Location target);

  /**
   * Rotates this NPC so that it faces the eyes of the given player.
   *
   * <p>Unlike {@link #lookAt(Location)} the rotation is only sent to that one player, which is what
   * makes {@link NpcSettings#lookAtViewer()} able to have every viewer see the NPC looking straight
   * at them at the same time.
   *
   * @param player the player to look at.
   * @throws NullPointerException  if {@code player} is {@code null}.
   * @throws IllegalStateException if called off the main thread or after {@link #remove()}.
   * @since 1.0.0
   */
  void lookAt(@NotNull Player player);

  /**
   * Plays an animation on this NPC for every viewer.
   *
   * @param animation the animation to play.
   * @throws NullPointerException  if {@code animation} is {@code null}.
   * @throws IllegalStateException if called off the main thread or after {@link #remove()}.
   * @since 1.0.0
   */
  void playAnimation(@NotNull NpcAnimation animation);

  /**
   * Plays an animation on this NPC for a single viewer.
   *
   * <p>Does nothing if the player is not a viewer.
   *
   * @param animation the animation to play.
   * @param viewer    the player that should see the animation.
   * @throws NullPointerException  if any argument is {@code null}.
   * @throws IllegalStateException if called off the main thread or after {@link #remove()}.
   * @since 1.0.0
   */
  void playAnimation(@NotNull NpcAnimation animation, @NotNull Player viewer);

  /**
   * Returns the item this NPC currently holds or wears in the given slot.
   *
   * @param slot the slot to read.
   * @return a copy of the item in that slot, or {@code null} if the slot is empty.
   * @throws NullPointerException if {@code slot} is {@code null}.
   * @since 1.0.0
   */
  @Nullable
  ItemStack equipment(@NotNull EquipmentSlot slot);

  /**
   * Puts an item into the given equipment slot of this NPC.
   *
   * <p>The item is copied, so later changes to the passed stack are not picked up. Offhand slots are
   * silently ignored on servers older than 1.9.
   *
   * @param slot the slot to fill.
   * @param item the item to show, or {@code null} to clear the slot.
   * @throws NullPointerException  if {@code slot} is {@code null}.
   * @throws IllegalStateException if called off the main thread or after {@link #remove()}.
   * @since 1.0.0
   */
  void equipment(@NotNull EquipmentSlot slot, @Nullable ItemStack item);

  /**
   * Returns whether this NPC is currently sneaking.
   *
   * @return {@code true} if the NPC is sneaking.
   * @since 1.0.0
   */
  boolean sneaking();

  /**
   * Makes this NPC sneak or stand up.
   *
   * @param sneaking {@code true} to sneak.
   * @throws IllegalStateException if called off the main thread or after {@link #remove()}.
   * @since 1.0.0
   */
  void sneaking(boolean sneaking);

  /**
   * Returns the pose this NPC is currently in.
   *
   * @return the current pose, {@link NpcPose#STANDING} by default.
   * @since 1.0.0
   */
  @NotNull
  NpcPose pose();

  /**
   * Puts this NPC into the given pose.
   *
   * <p>Poses were introduced in Minecraft 1.14; on older servers the call is ignored except for
   * {@link NpcPose#CROUCHING}, which falls back to the legacy sneaking flag.
   *
   * @param pose the pose to apply.
   * @throws NullPointerException  if {@code pose} is {@code null}.
   * @throws IllegalStateException if called off the main thread or after {@link #remove()}.
   * @since 1.0.0
   */
  void pose(@NotNull NpcPose pose);

  /**
   * Returns the glow colour of this NPC.
   *
   * @return the current glow colour, or {@code null} if the NPC does not glow.
   * @since 1.0.0
   */
  @Nullable
  ChatColor glowing();

  /**
   * Makes this NPC glow in the given colour.
   *
   * <p>Clients read the outline colour from the scoreboard team an entity belongs to, not from its
   * metadata, so this method registers a team named after the entity id on the main scoreboard and
   * puts the profile name in it. Passing {@code null} removes the glow and unregisters the team.
   *
   * <p>Because teams address players by name, an NPC whose profile name equals the name of a real
   * player would colour that player as well. Give glowing NPCs a name no account uses.
   *
   * @param color the outline colour, or {@code null} to stop glowing.
   * @throws IllegalArgumentException if {@code color} is not a colour but a formatting code.
   * @throws IllegalStateException    if called off the main thread or after {@link #remove()}.
   * @since 1.0.0
   */
  void glowing(@Nullable ChatColor color);

  /**
   * Returns the skin layers that are currently visible on this NPC.
   *
   * @return an immutable set of visible layers.
   * @since 1.0.0
   */
  @NotNull
  Set<SkinLayer> skinLayers();

  /**
   * Sets which parts of the skin overlay are rendered on this NPC.
   *
   * <p>Clients hide every overlay by default, which is why a freshly spawned NPC without this call
   * has no hat, jacket or sleeves. {@link SkinLayer#all()} turns all of them on.
   *
   * @param layers the layers to show.
   * @throws NullPointerException  if {@code layers} is {@code null} or contains {@code null}.
   * @throws IllegalStateException if called off the main thread or after {@link #remove()}.
   * @since 1.0.0
   */
  void skinLayers(@NotNull Set<SkinLayer> layers);

  /**
   * Returns the floating text above this NPC.
   *
   * <p>Never {@code null}: every NPC owns a hologram, it simply starts out empty and sends no
   * packets until it has lines. The hologram is destroyed together with the NPC.
   *
   * @return the hologram of this NPC.
   * @see NpcHologram
   * @since 1.0.0
   */
  @NotNull
  NpcHologram hologram();

  /**
   * Returns the mutable, thread-safe map of arbitrary data attached to this NPC.
   *
   * <p>Effigy never reads or writes this map; it exists so that plugins can associate their own
   * state, for example a shop id or a dialogue tree, with an NPC without maintaining a side map
   * keyed by entity id. The map is cleared when the NPC is removed.
   *
   * @return the metadata map of this NPC.
   * @since 1.0.0
   */
  @NotNull
  Map<String, Object> metadata();

  /**
   * Returns the registry that owns this NPC.
   *
   * @return the owning registry.
   * @since 1.0.0
   */
  @NotNull
  NpcRegistry registry();

  /**
   * Returns whether this NPC has been removed.
   *
   * <p>This is the only method that stays callable on a removed NPC, and the only one that is safe
   * to call from any thread at any time.
   *
   * @return {@code true} if {@link #remove()} has already run.
   * @since 1.0.0
   */
  @Contract(pure = true)
  boolean isRemoved();

  /**
   * Despawns this NPC for every viewer and unregisters it.
   *
   * <p>Idempotent: calling it on an already removed NPC does nothing. After this returns the NPC is
   * no longer reachable through the registry and every other method throws
   * {@link IllegalStateException}.
   *
   * @throws IllegalStateException if called off the main thread.
   * @since 1.0.0
   */
  void remove();
}
