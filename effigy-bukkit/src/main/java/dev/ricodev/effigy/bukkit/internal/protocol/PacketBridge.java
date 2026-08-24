/*
 * This file is part of Effigy, licensed under the MIT License.
 * Copyright (c) 2026 ricodevvv and contributors.
 */
package dev.ricodev.effigy.bukkit.internal.protocol;

import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.util.Vector3d;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityAnimation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnPlayer;
import dev.ricodev.effigy.bukkit.internal.util.MinecraftVersion;
import dev.ricodev.effigy.profile.NpcProfile;
import dev.ricodev.effigy.profile.ProfileProperty;
import dev.ricodev.effigy.protocol.NpcAnimation;
import dev.ricodev.effigy.protocol.NpcPose;
import dev.ricodev.effigy.protocol.SkinLayer;
import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The single place in the library that knows what a packet looks like.
 *
 * <p>Everything above this class works with {@link Player}, {@link Location} and the enums of the
 * API; everything below it is PacketEvents. Keeping the boundary sharp means a protocol change of a
 * future Minecraft release, or even a switch to a different packet library, is a rewrite of this one
 * file rather than of the whole implementation.
 *
 * <p>Packets are sent <em>silently</em>, that is, without passing them through the listener pipeline
 * of PacketEvents. Other plugins therefore do not see the NPC traffic at all, which avoids feeding
 * anti-cheat and packet-limiter plugins entities that do not exist on the server.
 *
 * <p><strong>Threading.</strong> Sending is thread-safe, but the callers in this library all run on
 * the main thread so that packet order matches the order of the operations that produced them.
 *
 * <p>Not part of the public API.
 *
 * @since 1.0.0
 */
public final class PacketBridge {

  /**
   * Index of the shared entity flag byte, stable across every protocol version.
   *
   * @see <a href="https://minecraft.wiki/w/Java_Edition_protocol/Entity_metadata">Entity metadata</a>
   */
  private static final int FLAGS_INDEX = 0;
  /** Index of the pose, introduced together with poses in 1.14 and unchanged since. */
  private static final int POSE_INDEX = 6;
  /** Index of the optional custom name of any entity, unchanged since 1.9. */
  private static final int CUSTOM_NAME_INDEX = 2;
  /** Index of the flag deciding whether the custom name is rendered without looking at the entity. */
  private static final int CUSTOM_NAME_VISIBLE_INDEX = 3;
  /** Index of the flag disabling gravity, which for a client-side entity means it stays put. */
  private static final int NO_GRAVITY_INDEX = 5;
  /**
   * Index of the stack carried by a dropped item entity. Item entities extend the base entity
   * directly rather than the living entity, so this is the first index after the shared fields.
   */
  private static final int ITEM_STACK_INDEX = 8;

  /** Bit of the flag byte marking an entity as sneaking. */
  private static final byte FLAG_SNEAKING = 0x02;
  /** Bit of the flag byte marking an entity as glowing. */
  private static final byte FLAG_GLOWING = 0x40;
  /** Bit of the flag byte marking an entity as invisible. */
  private static final byte FLAG_INVISIBLE = 0x20;

  /** Bit of the armour stand flag byte selecting the small variant. */
  private static final byte ARMOR_STAND_SMALL = 0x01;
  /** Bit of the armour stand flag byte removing the hitbox entirely. */
  private static final byte ARMOR_STAND_MARKER = 0x10;

  private static final Map<NpcAnimation, WrapperPlayServerEntityAnimation.EntityAnimationType> ANIMATIONS;
  private static final Map<NpcPose, com.github.retrooper.packetevents.protocol.entity.pose.EntityPose> POSES;
  private static final EnumSet<WrapperPlayServerPlayerInfoUpdate.Action> ADD_ACTIONS = EnumSet.of(
    WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
    WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_HAT,
    WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED,
    WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LATENCY,
    WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_GAME_MODE,
    WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME);

  static {
    Map<NpcAnimation, WrapperPlayServerEntityAnimation.EntityAnimationType> animations =
      new EnumMap<>(NpcAnimation.class);
    animations.put(NpcAnimation.SWING_MAIN_ARM,
      WrapperPlayServerEntityAnimation.EntityAnimationType.SWING_MAIN_ARM);
    animations.put(NpcAnimation.SWING_OFF_HAND,
      WrapperPlayServerEntityAnimation.EntityAnimationType.SWING_OFF_HAND);
    animations.put(NpcAnimation.TAKE_DAMAGE,
      WrapperPlayServerEntityAnimation.EntityAnimationType.HURT);
    animations.put(NpcAnimation.LEAVE_BED,
      WrapperPlayServerEntityAnimation.EntityAnimationType.WAKE_UP);
    animations.put(NpcAnimation.CRITICAL_HIT,
      WrapperPlayServerEntityAnimation.EntityAnimationType.CRITICAL_HIT);
    animations.put(NpcAnimation.MAGIC_CRITICAL_HIT,
      WrapperPlayServerEntityAnimation.EntityAnimationType.MAGIC_CRITICAL_HIT);
    ANIMATIONS = Collections.unmodifiableMap(animations);

    Map<NpcPose, com.github.retrooper.packetevents.protocol.entity.pose.EntityPose> poses =
      new EnumMap<>(NpcPose.class);
    poses.put(NpcPose.STANDING,
      com.github.retrooper.packetevents.protocol.entity.pose.EntityPose.STANDING);
    poses.put(NpcPose.FALL_FLYING,
      com.github.retrooper.packetevents.protocol.entity.pose.EntityPose.FALL_FLYING);
    poses.put(NpcPose.SLEEPING,
      com.github.retrooper.packetevents.protocol.entity.pose.EntityPose.SLEEPING);
    poses.put(NpcPose.SWIMMING,
      com.github.retrooper.packetevents.protocol.entity.pose.EntityPose.SWIMMING);
    poses.put(NpcPose.SPIN_ATTACK,
      com.github.retrooper.packetevents.protocol.entity.pose.EntityPose.SPIN_ATTACK);
    poses.put(NpcPose.CROUCHING,
      com.github.retrooper.packetevents.protocol.entity.pose.EntityPose.CROUCHING);
    poses.put(NpcPose.DYING,
      com.github.retrooper.packetevents.protocol.entity.pose.EntityPose.DYING);
    POSES = Collections.unmodifiableMap(poses);
  }

  private final PlayerManager players;
  private final MinecraftVersion version;
  private final int skinLayerIndex;
  private final int armorStandFlagsIndex;

  /**
   * Creates a bridge on top of an initialised PacketEvents instance.
   *
   * @param api the packet library instance to use.
   * @throws NullPointerException if {@code api} is {@code null}.
   * @throws IllegalStateException if the server version cannot be determined.
   * @since 1.0.0
   */
  public PacketBridge(@NotNull PacketEventsAPI<?> api) {
    Objects.requireNonNull(api, "api");
    this.players = api.getPlayerManager();

    String releaseName = api.getServerManager().getVersion().getReleaseName();
    MinecraftVersion parsed = MinecraftVersion.parse(releaseName);
    if (parsed == null) {
      throw new IllegalStateException("Unable to parse the server version '" + releaseName + "'");
    }
    this.version = parsed;
    this.skinLayerIndex = resolveSkinLayerIndex(parsed);
    this.armorStandFlagsIndex = resolveArmorStandFlagsIndex(parsed);
  }

  /**
   * Works out where the displayed skin parts live in the metadata of a player entity.
   *
   * <p>The index moves whenever a field is inserted into a superclass of the player entity, which
   * has happened often enough that hard coding a single number breaks the skin overlay on half of
   * the supported versions.
   *
   * @param version the version of the server.
   * @return the metadata index of the displayed skin parts.
   * @since 1.0.0
   */
  private static int resolveSkinLayerIndex(@NotNull MinecraftVersion version) {
    if (version.atLeast(1, 21, 9)) {
      return 16;
    }
    if (version.atLeast(1, 17)) {
      return 17;
    }
    if (version.atLeast(1, 15)) {
      return 16;
    }
    if (version.atLeast(1, 14)) {
      return 15;
    }
    if (version.atLeast(1, 10)) {
      return 13;
    }
    if (version.atLeast(1, 9)) {
      return 12;
    }
    return 10;
  }

  /**
   * Works out where the armour stand flag byte lives in the metadata of an armour stand.
   *
   * <p>Armour stand fields sit directly on top of the living entity fields, so the index moves for
   * the same reason and at the same releases as the skin overlay index does.
   *
   * @param version the version of the server.
   * @return the metadata index of the armour stand flags.
   * @since 1.0.0
   */
  private static int resolveArmorStandFlagsIndex(@NotNull MinecraftVersion version) {
    return version.atLeast(1, 21, 9) ? 14 : 15;
  }

  /**
   * Returns the version of the server this bridge talks to.
   *
   * @return the parsed server version.
   * @since 1.0.0
   */
  @NotNull
  public MinecraftVersion version() {
    return this.version;
  }

  /**
   * Adds the profile of an NPC to the client of a viewer.
   *
   * <p>Must precede the spawn packet: a client that receives a player entity whose profile it does
   * not know renders it without a skin and, before 1.19.3, sometimes not at all.
   *
   * @param viewer   the player to send the packet to.
   * @param uniqueId the unique id the NPC is spawned with.
   * @param profile  the profile carrying the name and the skin.
   * @param listed   whether the client should display the entry in its player list.
   * @since 1.0.0
   */
  public void sendPlayerInfoAdd(
    @NotNull Player viewer,
    @NotNull UUID uniqueId,
    @NotNull NpcProfile profile,
    boolean listed
  ) {
    UserProfile userProfile = new UserProfile(uniqueId, profile.name());
    for (ProfileProperty property : profile.properties()) {
      userProfile.getTextureProperties()
        .add(new TextureProperty(property.name(), property.value(), property.signature()));
    }

    PacketWrapper<?> wrapper;
    if (this.version.atLeast(1, 19, 3)) {
      WrapperPlayServerPlayerInfoUpdate.PlayerInfo info = new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
        userProfile,
        listed,
        0,
        GameMode.SURVIVAL,
        null,
        null,
        0,
        true);
      wrapper = new WrapperPlayServerPlayerInfoUpdate(ADD_ACTIONS, Collections.singletonList(info));
    } else {
      WrapperPlayServerPlayerInfo.PlayerData data = new WrapperPlayServerPlayerInfo.PlayerData(
        null,
        userProfile,
        GameMode.SURVIVAL,
        0);
      wrapper = new WrapperPlayServerPlayerInfo(WrapperPlayServerPlayerInfo.Action.ADD_PLAYER, data);
    }
    this.send(viewer, wrapper);
  }

  /**
   * Removes the profile of an NPC from the client of a viewer.
   *
   * <p>On modern clients this also stops the NPC name from appearing in chat autocompletion. On
   * clients older than 1.19.3 it must be delayed past the spawn packet, otherwise the skin is
   * dropped along with the entry.
   *
   * @param viewer   the player to send the packet to.
   * @param uniqueId the unique id of the NPC.
   * @param profile  the profile of the NPC, required to build the legacy packet.
   * @since 1.0.0
   */
  public void sendPlayerInfoRemove(@NotNull Player viewer, @NotNull UUID uniqueId, @NotNull NpcProfile profile) {
    PacketWrapper<?> wrapper;
    if (this.version.atLeast(1, 19, 3)) {
      wrapper = new WrapperPlayServerPlayerInfoRemove(Collections.singletonList(uniqueId));
    } else {
      WrapperPlayServerPlayerInfo.PlayerData data = new WrapperPlayServerPlayerInfo.PlayerData(
        null,
        new UserProfile(uniqueId, profile.name()),
        null,
        0);
      wrapper = new WrapperPlayServerPlayerInfo(WrapperPlayServerPlayerInfo.Action.REMOVE_PLAYER, data);
    }
    this.send(viewer, wrapper);
  }

  /**
   * Spawns the NPC entity on the client of a viewer.
   *
   * @param viewer   the player to send the packet to.
   * @param entityId the entity id of the NPC.
   * @param uniqueId the unique id of the NPC.
   * @param location where the NPC stands, including its rotation.
   * @since 1.0.0
   */
  public void sendSpawn(
    @NotNull Player viewer,
    int entityId,
    @NotNull UUID uniqueId,
    @NotNull Location location
  ) {
    Vector3d position = new Vector3d(location.getX(), location.getY(), location.getZ());
    PacketWrapper<?> wrapper;
    if (this.version.atLeast(1, 20, 2)) {
      // Since 1.20.2 players are spawned through the generic entity spawn packet.
      wrapper = new WrapperPlayServerSpawnEntity(
        entityId,
        Optional.of(uniqueId),
        EntityTypes.PLAYER,
        position,
        location.getPitch(),
        location.getYaw(),
        location.getYaw(),
        0,
        Optional.empty());
    } else {
      wrapper = new WrapperPlayServerSpawnPlayer(
        entityId,
        uniqueId,
        new com.github.retrooper.packetevents.protocol.world.Location(
          position, location.getYaw(), location.getPitch()));
    }
    this.send(viewer, wrapper);
  }

  /**
   * Removes one or more entities from the client of a viewer.
   *
   * <p>Takes several ids at once because a hologram destroys all of its lines together, and one
   * packet carrying five ids is cheaper than five packets carrying one.
   *
   * @param viewer    the player to send the packet to.
   * @param entityIds the entity ids to remove; nothing is sent for an empty array.
   * @since 1.0.0
   */
  public void sendDespawn(@NotNull Player viewer, int @NotNull ... entityIds) {
    if (entityIds.length > 0) {
      this.send(viewer, new WrapperPlayServerDestroyEntities(entityIds));
    }
  }

  /**
   * Spawns an invisible marker armour stand, the entity a hologram line is made of.
   *
   * <p>Marker armour stands have no hitbox at all, so a hologram can never swallow a click meant for
   * the NPC underneath it, and no gravity, so the client leaves it exactly where it was put.
   *
   * @param viewer   the player to send the packet to.
   * @param entityId the entity id of the line.
   * @param location where the line floats.
   * @since 1.0.0
   */
  public void sendHologramLineSpawn(@NotNull Player viewer, int entityId, @NotNull Location location) {
    this.send(viewer, new WrapperPlayServerSpawnEntity(
      entityId,
      Optional.of(UUID.randomUUID()),
      EntityTypes.ARMOR_STAND,
      new Vector3d(location.getX(), location.getY(), location.getZ()),
      0.0f,
      0.0f,
      0.0f,
      0,
      Optional.empty()));
  }

  /**
   * Moves a hologram line on the client of a viewer.
   *
   * @param viewer   the player to send the packet to.
   * @param entityId the entity id of the line.
   * @param location the destination.
   * @since 1.0.0
   */
  public void sendHologramLineMove(@NotNull Player viewer, int entityId, @NotNull Location location) {
    this.send(viewer, new WrapperPlayServerEntityTeleport(
      entityId,
      new com.github.retrooper.packetevents.protocol.world.Location(
        new Vector3d(location.getX(), location.getY(), location.getZ()), 0.0f, 0.0f),
      false));
  }

  /**
   * Spawns a hovering dropped item, the entity an item hologram line is made of.
   *
   * <p>The client renders a dropped item bobbing and slowly rotating on its own, which is exactly
   * the effect an item line wants, so no animation has to be driven from the server.
   *
   * @param viewer   the player to send the packet to.
   * @param entityId the entity id of the line.
   * @param location where the item floats.
   * @since 1.0.0
   */
  public void sendItemLineSpawn(@NotNull Player viewer, int entityId, @NotNull Location location) {
    this.send(viewer, new WrapperPlayServerSpawnEntity(
      entityId,
      Optional.of(UUID.randomUUID()),
      EntityTypes.ITEM,
      new Vector3d(location.getX(), location.getY(), location.getZ()),
      0.0f,
      0.0f,
      0.0f,
      0,
      Optional.empty()));
  }

  /**
   * Builds the metadata that puts a stack into a floating item line.
   *
   * @param item the item to display.
   * @return the metadata entries describing the line.
   * @since 1.0.0
   */
  @NotNull
  public List<EntityData<?>> buildItemLineMetadata(@NotNull ItemStack item) {
    List<EntityData<?>> data = new ArrayList<>(2);
    // Without this the client applies gravity and the item sinks out of the hologram.
    data.add(new EntityData<>(NO_GRAVITY_INDEX, EntityDataTypes.BOOLEAN, Boolean.TRUE));
    data.add(new EntityData<>(
      ITEM_STACK_INDEX,
      EntityDataTypes.ITEMSTACK,
      SpigotReflectionUtil.decodeBukkitItemStack(item)));
    return data;
  }

  /**
   * Builds the metadata that turns an armour stand into a single line of floating text.
   *
   * @param text the text of the line, with section sign or ampersand colour codes.
   * @return the metadata entries describing the line.
   * @since 1.0.0
   */
  @NotNull
  public List<EntityData<?>> buildHologramLineMetadata(@NotNull String text) {
    List<EntityData<?>> data = new ArrayList<>(5);
    data.add(new EntityData<>(FLAGS_INDEX, EntityDataTypes.BYTE, FLAG_INVISIBLE));
    data.add(new EntityData<>(
      CUSTOM_NAME_INDEX,
      EntityDataTypes.OPTIONAL_ADV_COMPONENT,
      Optional.of(toComponent(text))));
    data.add(new EntityData<>(CUSTOM_NAME_VISIBLE_INDEX, EntityDataTypes.BOOLEAN, Boolean.TRUE));
    data.add(new EntityData<>(NO_GRAVITY_INDEX, EntityDataTypes.BOOLEAN, Boolean.TRUE));
    data.add(new EntityData<>(
      this.armorStandFlagsIndex,
      EntityDataTypes.BYTE,
      (byte) (ARMOR_STAND_SMALL | ARMOR_STAND_MARKER)));
    return data;
  }

  /**
   * Parses a legacy coloured string into the component the protocol expects.
   *
   * <p>Both the section sign and the ampersand are accepted, because a plugin reading its lines from
   * a configuration file will almost always have ampersands in hand.
   *
   * @param text the text to parse.
   * @return the parsed component.
   * @since 1.0.0
   */
  @NotNull
  private static Component toComponent(@NotNull String text) {
    return LegacyComponentSerializer.legacySection()
      .deserialize(org.bukkit.ChatColor.translateAlternateColorCodes('&', text));
  }

  /**
   * Rotates the body and the head of the NPC on the client of a viewer.
   *
   * <p>Two packets are needed: the rotation packet turns the body, and clients keep the head at its
   * previous angle unless a head look packet follows.
   *
   * @param viewer   the player to send the packets to.
   * @param entityId the entity id of the NPC.
   * @param location the current location, needed for the 1.8 fallback which has no rotation packet.
   * @param yaw      the horizontal rotation in degrees.
   * @param pitch    the vertical rotation in degrees.
   * @since 1.0.0
   */
  public void sendRotation(
    @NotNull Player viewer,
    int entityId,
    @NotNull Location location,
    float yaw,
    float pitch
  ) {
    PacketWrapper<?> rotation;
    if (this.version.atLeast(1, 9)) {
      rotation = new WrapperPlayServerEntityRotation(entityId, yaw, pitch, true);
    } else {
      rotation = new WrapperPlayServerEntityTeleport(
        entityId,
        new com.github.retrooper.packetevents.protocol.world.Location(
          new Vector3d(location.getX(), location.getY(), location.getZ()), yaw, pitch),
        true);
    }
    this.send(viewer, rotation);
    this.send(viewer, new WrapperPlayServerEntityHeadLook(entityId, yaw));
  }

  /**
   * Teleports the NPC on the client of a viewer.
   *
   * @param viewer   the player to send the packet to.
   * @param entityId the entity id of the NPC.
   * @param location the destination, including the rotation to apply.
   * @since 1.0.0
   */
  public void sendTeleport(@NotNull Player viewer, int entityId, @NotNull Location location) {
    this.send(viewer, new WrapperPlayServerEntityTeleport(
      entityId,
      new com.github.retrooper.packetevents.protocol.world.Location(
        new Vector3d(location.getX(), location.getY(), location.getZ()),
        location.getYaw(),
        location.getPitch()),
      true));
    this.send(viewer, new WrapperPlayServerEntityHeadLook(entityId, location.getYaw()));
  }

  /**
   * Plays an animation on the NPC for a single viewer.
   *
   * @param viewer    the player to send the packet to.
   * @param entityId  the entity id of the NPC.
   * @param animation the animation to play.
   * @since 1.0.0
   */
  public void sendAnimation(@NotNull Player viewer, int entityId, @NotNull NpcAnimation animation) {
    if (animation == NpcAnimation.SWING_OFF_HAND && !this.version.atLeast(1, 9)) {
      return;
    }
    this.send(viewer, new WrapperPlayServerEntityAnimation(entityId, ANIMATIONS.get(animation)));
  }

  /**
   * Updates one equipment slot of the NPC for a single viewer.
   *
   * @param viewer   the player to send the packet to.
   * @param entityId the entity id of the NPC.
   * @param slot     the slot to update.
   * @param item     the item to show, or {@code null} to clear the slot.
   * @since 1.0.0
   */
  public void sendEquipment(
    @NotNull Player viewer,
    int entityId,
    @NotNull EquipmentSlot slot,
    @Nullable ItemStack item
  ) {
    com.github.retrooper.packetevents.protocol.player.EquipmentSlot target = this.translate(slot);
    if (target == null) {
      return;
    }

    com.github.retrooper.packetevents.protocol.item.ItemStack stack = item == null
      ? com.github.retrooper.packetevents.protocol.item.ItemStack.EMPTY
      : SpigotReflectionUtil.decodeBukkitItemStack(item);
    this.send(viewer, new WrapperPlayServerEntityEquipment(
      entityId,
      Collections.singletonList(new Equipment(target, stack))));
  }

  /**
   * Sends a metadata update for the NPC to a single viewer.
   *
   * @param viewer   the player to send the packet to.
   * @param entityId the entity id of the NPC.
   * @param data     the metadata entries to send; nothing is sent for an empty list.
   * @since 1.0.0
   */
  public void sendMetadata(@NotNull Player viewer, int entityId, @NotNull List<EntityData<?>> data) {
    if (!data.isEmpty()) {
      this.send(viewer, new WrapperPlayServerEntityMetadata(entityId, data));
    }
  }

  /**
   * Builds the full metadata of an NPC, as sent right after it is spawned.
   *
   * @param sneaking whether the NPC is sneaking.
   * @param glowing  whether the NPC has a glowing outline.
   * @param pose     the pose of the NPC.
   * @param layers   the visible skin overlay parts.
   * @return the metadata entries describing that state.
   * @since 1.0.0
   */
  @NotNull
  public List<EntityData<?>> buildMetadata(
    boolean sneaking,
    boolean glowing,
    @NotNull NpcPose pose,
    @NotNull Set<SkinLayer> layers
  ) {
    List<EntityData<?>> data = new ArrayList<>(3);

    byte flags = 0;
    if (sneaking) {
      flags |= FLAG_SNEAKING;
    }
    if (glowing && this.version.atLeast(1, 9)) {
      flags |= FLAG_GLOWING;
    }
    data.add(new EntityData<>(FLAGS_INDEX, EntityDataTypes.BYTE, flags));
    data.add(new EntityData<>(this.skinLayerIndex, EntityDataTypes.BYTE, SkinLayer.pack(layers)));

    if (this.version.atLeast(1, 14)) {
      // Sneaking and the crouching pose are two separate fields; a client that receives only the
      // flag keeps the standing hitbox and renders the name tag at the wrong height.
      NpcPose effectivePose = sneaking && pose == NpcPose.STANDING ? NpcPose.CROUCHING : pose;
      data.add(new EntityData<>(POSE_INDEX, EntityDataTypes.ENTITY_POSE, POSES.get(effectivePose)));
    }
    return data;
  }

  /**
   * Translates a Bukkit equipment slot into its protocol counterpart.
   *
   * @param slot the Bukkit slot.
   * @return the protocol slot, or {@code null} if this server version cannot display it.
   * @since 1.0.0
   */
  @Nullable
  private com.github.retrooper.packetevents.protocol.player.EquipmentSlot translate(@NotNull EquipmentSlot slot) {
    switch (slot) {
      case HAND:
        return com.github.retrooper.packetevents.protocol.player.EquipmentSlot.MAIN_HAND;
      case OFF_HAND:
        return this.version.atLeast(1, 9)
          ? com.github.retrooper.packetevents.protocol.player.EquipmentSlot.OFF_HAND
          : null;
      case FEET:
        return com.github.retrooper.packetevents.protocol.player.EquipmentSlot.BOOTS;
      case LEGS:
        return com.github.retrooper.packetevents.protocol.player.EquipmentSlot.LEGGINGS;
      case CHEST:
        return com.github.retrooper.packetevents.protocol.player.EquipmentSlot.CHEST_PLATE;
      case HEAD:
        return com.github.retrooper.packetevents.protocol.player.EquipmentSlot.HELMET;
      default:
        // Slots such as BODY exist on the server but have no meaning for a player entity.
        return null;
    }
  }

  /**
   * Sends one packet to one player without notifying any listener.
   *
   * @param viewer  the receiving player.
   * @param wrapper the packet to send.
   * @since 1.0.0
   */
  private void send(@NotNull Player viewer, @NotNull PacketWrapper<?> wrapper) {
    this.players.sendPacketSilently(viewer, wrapper);
  }
}
