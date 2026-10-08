/*
 * This file is part of Effigy, licensed under the MIT License.
 * Copyright (c) 2026 ricodevvv and contributors.
 */
package dev.ricodev.effigy.bukkit.internal;

import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import dev.ricodev.effigy.Npc;
import dev.ricodev.effigy.NpcRegistry;
import dev.ricodev.effigy.bukkit.internal.hologram.HologramImpl;
import dev.ricodev.effigy.bukkit.internal.protocol.PacketBridge;
import dev.ricodev.effigy.bukkit.internal.util.MinecraftVersion;
import dev.ricodev.effigy.bukkit.internal.util.Preconditions;
import dev.ricodev.effigy.bukkit.internal.util.Rotations;
import dev.ricodev.effigy.bukkit.internal.util.TeamColors;
import dev.ricodev.effigy.event.NpcHideEvent;
import dev.ricodev.effigy.event.NpcShowEvent;
import dev.ricodev.effigy.hologram.NpcHologram;
import dev.ricodev.effigy.profile.NpcProfile;
import dev.ricodev.effigy.protocol.NpcAnimation;
import dev.ricodev.effigy.protocol.NpcPose;
import dev.ricodev.effigy.protocol.SkinLayer;
import dev.ricodev.effigy.settings.NpcSettings;
import dev.ricodev.effigy.settings.TabListVisibility;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

/**
 * The one and only {@link Npc} implementation.
 *
 * <p>State that a client only reads at spawn time, such as the profile, forces a respawn when it
 * changes; everything else is pushed to the current viewers as an incremental packet. The class
 * keeps its own copy of that state so that a player who joins later receives exactly what the
 * earlier viewers have seen.
 *
 * <p>Not part of the public API.
 *
 * @since 1.0.0
 */
public final class NpcImpl implements Npc {

  private final EffigyImpl effigy;
  private final int entityId;
  private final UUID uniqueId;
  private final Location location;
  private final Set<Player> viewers = ConcurrentHashMap.newKeySet();
  private final Map<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);
  private final Map<String, Object> metadata = new ConcurrentHashMap<>();
  private final HologramImpl hologram;

  private volatile NpcProfile profile;
  private volatile NpcSettings settings;
  private volatile Set<SkinLayer> skinLayers;
  private volatile boolean sneaking;
  private volatile NpcPose pose = NpcPose.STANDING;
  private volatile ChatColor glowColor;
  private volatile boolean removed;

  /**
   * Creates an NPC. Called by {@link NpcBuilderImpl} only.
   *
   * @param effigy   the owning library instance.
   * @param entityId the allocated entity id.
   * @param profile  the profile to spawn with.
   * @param location the location to stand at; copied.
   * @param settings the settings to start with.
   * @since 1.0.0
   */
  NpcImpl(
    @NotNull EffigyImpl effigy,
    int entityId,
    @NotNull NpcProfile profile,
    @NotNull Location location,
    @NotNull NpcSettings settings
  ) {
    this.effigy = effigy;
    this.entityId = entityId;
    this.uniqueId = profile.uniqueId();
    this.profile = profile;
    this.location = location.clone();
    this.settings = settings;
    this.skinLayers = settings.skinLayers();
    this.hologram = new HologramImpl(
      this, effigy.packets(), () -> this.checkUsable("NpcHologram"), effigy);
  }

  @Override
  public int entityId() {
    return this.entityId;
  }

  @NotNull
  @Override
  public UUID uniqueId() {
    return this.uniqueId;
  }

  @NotNull
  @Override
  public NpcProfile profile() {
    return this.profile;
  }

  @Override
  public void profile(@NotNull NpcProfile profile) {
    Objects.requireNonNull(profile, "profile");
    this.checkUsable("Npc#profile(NpcProfile)");
    // The unique id has to survive, clients tie the tab list entry and the entity together with it.
    this.profile = profile.withUniqueId(this.uniqueId);
    this.respawn();
  }

  @NotNull
  @Override
  public Location location() {
    synchronized (this.location) {
      return this.location.clone();
    }
  }

  @NotNull
  @Override
  public World world() {
    synchronized (this.location) {
      World world = this.location.getWorld();
      if (world == null) {
        throw new IllegalStateException("The world of NPC " + this.profile.name() + " has been unloaded");
      }
      return world;
    }
  }

  @NotNull
  @Override
  public NpcSettings settings() {
    return this.settings;
  }

  @Override
  public void settings(@NotNull NpcSettings settings) {
    Objects.requireNonNull(settings, "settings");
    this.checkUsable("Npc#settings(NpcSettings)");
    this.settings = settings;
  }

  @NotNull
  @Override
  @UnmodifiableView
  public Collection<Player> viewers() {
    return Collections.unmodifiableSet(this.viewers);
  }

  @Override
  public boolean isViewer(@NotNull Player player) {
    Objects.requireNonNull(player, "player");
    return this.viewers.contains(player);
  }

  @Override
  public boolean canSee(@NotNull Player player) {
    Objects.requireNonNull(player, "player");
    return !this.removed && this.settings.visibilityRule().test(this, player);
  }

  @Override
  public boolean show(@NotNull Player player) {
    Objects.requireNonNull(player, "player");
    this.checkUsable("Npc#show(Player)");
    if (this.viewers.contains(player) || !player.isOnline()) {
      return false;
    }

    NpcShowEvent event = new NpcShowEvent(this, player);
    this.effigy.callEvent(event);
    if (event.isCancelled()) {
      return false;
    }

    // Claim the slot before sending anything: a listener of the event above may well have called
    // show() again, and two spawn packets for one entity id desynchronise the client for good.
    if (!this.viewers.add(player)) {
      return false;
    }
    this.spawnFor(player);
    return true;
  }

  @Override
  public boolean hide(@NotNull Player player) {
    Objects.requireNonNull(player, "player");
    this.checkUsable("Npc#hide(Player)");
    if (!this.viewers.contains(player)) {
      return false;
    }

    NpcHideEvent event = new NpcHideEvent(this, player);
    this.effigy.callEvent(event);
    if (event.isCancelled()) {
      return false;
    }
    if (!this.viewers.remove(player)) {
      return false;
    }
    this.despawnFor(player);
    return true;
  }

  @Override
  public void respawn() {
    this.checkUsable("Npc#respawn()");
    for (Player viewer : this.viewers) {
      if (viewer.isOnline()) {
        this.despawnFor(viewer);
        this.spawnFor(viewer);
      }
    }
  }

  @Override
  public void teleport(@NotNull Location target) {
    Objects.requireNonNull(target, "location");
    Preconditions.argument(target.getWorld() != null, "The target location must have a world");
    this.checkUsable("Npc#teleport(Location)");

    boolean worldChanged;
    Location snapshot;
    synchronized (this.location) {
      worldChanged = !Objects.equals(this.location.getWorld(), target.getWorld());
      this.location.setWorld(target.getWorld());
      this.location.setX(target.getX());
      this.location.setY(target.getY());
      this.location.setZ(target.getZ());
      this.location.setYaw(Rotations.normalizeYaw(target.getYaw()));
      this.location.setPitch(Rotations.clampPitch(target.getPitch()));
      snapshot = this.location.clone();
    }

    if (worldChanged) {
      // Nothing in the protocol moves an entity across worlds, so drop every viewer and let the
      // tracking task rebuild the viewer set from the players of the new world.
      this.hideFromAll();
      return;
    }
    for (Player viewer : this.viewers) {
      this.effigy.packets().sendTeleport(viewer, this.entityId, snapshot);
    }
    this.hologram.onNpcMoved();
  }

  @Override
  public void rotate(float yaw, float pitch) {
    this.checkUsable("Npc#rotate(float, float)");

    Location snapshot;
    synchronized (this.location) {
      this.location.setYaw(Rotations.normalizeYaw(yaw));
      this.location.setPitch(Rotations.clampPitch(pitch));
      snapshot = this.location.clone();
    }
    for (Player viewer : this.viewers) {
      this.effigy.packets().sendRotation(
        viewer, this.entityId, snapshot, snapshot.getYaw(), snapshot.getPitch());
    }
  }

  @Override
  public void lookAt(@NotNull Location target) {
    Objects.requireNonNull(target, "target");
    Location current = this.location();
    double dx = target.getX() - current.getX();
    double dy = target.getY() - current.getY();
    double dz = target.getZ() - current.getZ();
    this.rotate(Rotations.yaw(dx, dz), Rotations.pitch(dx, dy, dz));
  }

  @Override
  public void lookAt(@NotNull Player player) {
    Objects.requireNonNull(player, "player");
    this.checkUsable("Npc#lookAt(Player)");
    if (!this.viewers.contains(player)) {
      return;
    }

    Location current = this.location();
    Location eyes = player.getEyeLocation();
    if (!Objects.equals(current.getWorld(), eyes.getWorld())) {
      return;
    }

    double dx = eyes.getX() - current.getX();
    // Look at the eyes of the player from the eyes of the NPC, which sit about 1.62 blocks up.
    double dy = eyes.getY() - (current.getY() + 1.62);
    double dz = eyes.getZ() - current.getZ();
    float yaw = Rotations.yaw(dx, dz);
    float pitch = Rotations.pitch(dx, dy, dz);
    // Sent to this viewer only, so every viewer can be looked at simultaneously.
    this.effigy.packets().sendRotation(player, this.entityId, current, yaw, pitch);
  }

  @Override
  public void playAnimation(@NotNull NpcAnimation animation) {
    Objects.requireNonNull(animation, "animation");
    this.checkUsable("Npc#playAnimation(NpcAnimation)");
    for (Player viewer : this.viewers) {
      this.effigy.packets().sendAnimation(viewer, this.entityId, animation);
    }
  }

  @Override
  public void playAnimation(@NotNull NpcAnimation animation, @NotNull Player viewer) {
    Objects.requireNonNull(animation, "animation");
    Objects.requireNonNull(viewer, "viewer");
    this.checkUsable("Npc#playAnimation(NpcAnimation, Player)");
    if (this.viewers.contains(viewer)) {
      this.effigy.packets().sendAnimation(viewer, this.entityId, animation);
    }
  }

  @Nullable
  @Override
  public ItemStack equipment(@NotNull EquipmentSlot slot) {
    Objects.requireNonNull(slot, "slot");
    synchronized (this.equipment) {
      ItemStack item = this.equipment.get(slot);
      return item == null ? null : item.clone();
    }
  }

  @Override
  public void equipment(@NotNull EquipmentSlot slot, @Nullable ItemStack item) {
    Objects.requireNonNull(slot, "slot");
    this.checkUsable("Npc#equipment(EquipmentSlot, ItemStack)");

    ItemStack copy = item == null ? null : item.clone();
    synchronized (this.equipment) {
      if (copy == null) {
        this.equipment.remove(slot);
      } else {
        this.equipment.put(slot, copy);
      }
    }
    for (Player viewer : this.viewers) {
      this.effigy.packets().sendEquipment(viewer, this.entityId, slot, copy);
    }
  }

  @Override
  public boolean sneaking() {
    return this.sneaking;
  }

  @Override
  public void sneaking(boolean sneaking) {
    this.checkUsable("Npc#sneaking(boolean)");
    if (this.sneaking != sneaking) {
      this.sneaking = sneaking;
      this.pushMetadata();
    }
  }

  @NotNull
  @Override
  public NpcPose pose() {
    return this.pose;
  }

  @Override
  public void pose(@NotNull NpcPose pose) {
    Objects.requireNonNull(pose, "pose");
    this.checkUsable("Npc#pose(NpcPose)");
    if (this.pose != pose) {
      this.pose = pose;
      this.pushMetadata();
    }
  }

  @Nullable
  @Override
  public ChatColor glowing() {
    return this.glowColor;
  }

  @Override
  public void glowing(@Nullable ChatColor color) {
    Preconditions.argument(color == null || color.isColor(), "The glow colour must be a colour, not a format");
    this.checkUsable("Npc#glowing(ChatColor)");
    if (this.glowColor == color) {
      return;
    }

    this.glowColor = color;
    this.updateGlowTeam(color);
    this.pushMetadata();
  }

  @NotNull
  @Override
  public Set<SkinLayer> skinLayers() {
    return this.skinLayers;
  }

  @Override
  public void skinLayers(@NotNull Set<SkinLayer> layers) {
    Objects.requireNonNull(layers, "layers");
    this.checkUsable("Npc#skinLayers(Set)");

    EnumSet<SkinLayer> copy = EnumSet.noneOf(SkinLayer.class);
    for (SkinLayer layer : layers) {
      copy.add(Objects.requireNonNull(layer, "layer"));
    }
    this.skinLayers = Collections.unmodifiableSet(copy);
    this.pushMetadata();
  }

  @NotNull
  @Override
  public NpcHologram hologram() {
    return this.hologram;
  }

  @NotNull
  @Override
  public Map<String, Object> metadata() {
    return this.metadata;
  }

  @NotNull
  @Override
  public NpcRegistry registry() {
    return this.effigy.registry();
  }

  @Override
  public boolean isRemoved() {
    return this.removed;
  }

  @Override
  public void remove() {
    Preconditions.mainThread("Npc#remove()");
    if (this.removed) {
      return;
    }

    this.removed = true;
    this.hideFromAll();
    this.hologram.onNpcRemoved();
    this.updateGlowTeam(null);
    this.metadata.clear();
    this.effigy.onNpcRemoved(this);
  }

  /**
   * Despawns this NPC for every viewer without firing the hide event.
   *
   * <p>Used where the outcome is not negotiable: a world change, a removal or a shutdown.
   *
   * @since 1.0.0
   */
  void hideFromAll() {
    for (Player viewer : this.viewers) {
      if (viewer.isOnline()) {
        this.despawnFor(viewer);
      }
    }
    this.viewers.clear();
  }

  /**
   * Forgets a player that is no longer reachable, without sending a despawn packet.
   *
   * <p>Called when a player disconnects: the connection is already gone, so a packet would only
   * produce a warning in the log.
   *
   * @param player the player that left.
   * @since 1.0.0
   */
  void forgetViewer(@NotNull Player player) {
    this.viewers.remove(player);
    this.hologram.forgetViewer(player);
  }

  /**
   * Sends the whole spawn sequence to one player.
   *
   * <p>The order matters. The profile has to arrive before the spawn packet, the metadata after it,
   * and the tab list entry may only be withdrawn once the client has had a chance to associate the
   * skin with the entity.
   *
   * @param viewer the player to spawn this NPC for.
   * @since 1.0.0
   */
  private void spawnFor(@NotNull Player viewer) {
    PacketBridge packets = this.effigy.packets();
    NpcProfile currentProfile = this.profile;
    NpcSettings currentSettings = this.settings;
    Location snapshot = this.location();

    boolean listed = currentSettings.tabListVisibility() == TabListVisibility.VISIBLE;
    packets.sendPlayerInfoAdd(viewer, this.uniqueId, currentProfile, listed);
    packets.sendSpawn(viewer, this.entityId, this.uniqueId, snapshot);
    packets.sendMetadata(viewer, this.entityId, this.buildMetadata());
    packets.sendRotation(viewer, this.entityId, snapshot, snapshot.getYaw(), snapshot.getPitch());

    synchronized (this.equipment) {
      for (Map.Entry<EquipmentSlot, ItemStack> entry : this.equipment.entrySet()) {
        packets.sendEquipment(viewer, this.entityId, entry.getKey(), entry.getValue());
      }
    }

    this.hologram.onViewerAdded(viewer);

    if (!listed && !packets.version().atLeast(1, 19, 3)) {
      // Before 1.19.3 there is no "present but not listed" state, so the entry has to be removed
      // again, and only after a delay: a client that loses it too early drops the skin with it.
      this.effigy.scheduleDelayed(currentSettings.tabListRemovalDelay(), () -> {
        if (!this.removed && viewer.isOnline() && this.viewers.contains(viewer)) {
          packets.sendPlayerInfoRemove(viewer, this.uniqueId, this.profile);
        }
      });
    }
  }

  /**
   * Sends the despawn sequence to one player.
   *
   * @param viewer the player to despawn this NPC for.
   * @since 1.0.0
   */
  private void despawnFor(@NotNull Player viewer) {
    this.hologram.onViewerRemoved(viewer);
    this.effigy.packets().sendDespawn(viewer, this.entityId);
    this.effigy.packets().sendPlayerInfoRemove(viewer, this.uniqueId, this.profile);
  }

  /**
   * Builds the metadata describing the current visual state.
   *
   * @return the metadata entries to send.
   * @since 1.0.0
   */
  @NotNull
  private List<EntityData<?>> buildMetadata() {
    return this.effigy.packets()
      .buildMetadata(this.sneaking, this.glowColor != null, this.pose, this.skinLayers);
  }

  /**
   * Sends the current metadata to every viewer.
   *
   * @since 1.0.0
   */
  private void pushMetadata() {
    List<EntityData<?>> data = this.buildMetadata();
    for (Player viewer : this.viewers) {
      this.effigy.packets().sendMetadata(viewer, this.entityId, data);
    }
  }

  /**
   * Creates, updates or deletes the scoreboard team that colours the glowing outline.
   *
   * <p>The outline colour of an entity is not part of its metadata; clients read it from the team
   * the entity belongs to. The team is registered on the main scoreboard and named after the entity
   * id, so two NPCs never share one. The id is written in base 36 to stay within the sixteen
   * characters a team name may have before 1.18. Nothing happens on 1.8, which has no glowing.
   *
   * @param color the colour to apply, or {@code null} to delete the team again.
   * @since 1.0.0
   */
  private void updateGlowTeam(@Nullable ChatColor color) {
    MinecraftVersion version = this.effigy.packets().version();
    if (!version.atLeast(1, 9)) {
      return;
    }

    Scoreboard scoreboard = this.effigy.plugin().getServer().getScoreboardManager().getMainScoreboard();
    String teamName = "effigy-" + Integer.toString(this.entityId, 36);
    Team team = scoreboard.getTeam(teamName);

    if (color == null) {
      if (team != null) {
        team.unregister();
      }
      return;
    }
    if (team == null) {
      team = scoreboard.registerNewTeam(teamName);
    }
    TeamColors.apply(team, color, version);
    team.addEntry(this.profile.name());
  }

  /**
   * Verifies that a mutating call is legal right now.
   *
   * @param operation the operation being attempted, used in the error messages.
   * @throws IllegalStateException if the NPC was removed or the caller is not on the main thread.
   * @since 1.0.0
   */
  private void checkUsable(@NotNull String operation) {
    Preconditions.mainThread(operation);
    Preconditions.state(!this.removed, "NPC " + this.profile.name() + " has already been removed");
  }

  @Override
  public String toString() {
    return "Npc(name=" + this.profile.name()
      + ", entityId=" + this.entityId
      + ", viewers=" + this.viewers.size() + ')';
  }
}
