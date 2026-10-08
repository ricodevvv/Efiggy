/*
 * This file is part of Effigy, licensed under the MIT License.
 * Copyright (c) 2026 ricodevvv and contributors.
 */
package dev.ricodev.effigy.bukkit.internal.protocol;

import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemSwingAnimation;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.pose.EntityPose;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerAttachEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityAnimation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerHurtAnimation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnLivingEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnPlayer;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSwingAnimation;
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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
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
 * <p>Packets are written in the format of the server version, from 1.8.8 up to 26.x, and a protocol
 * translator such as ViaVersion takes it from there for clients of other versions. Where the
 * protocol moved over the years, the branches below say from which release on a format applies.
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

  /** Bit of the flag byte marking an entity as sneaking. */
  private static final byte FLAG_SNEAKING = 0x02;
  /** Bit of the flag byte marking an entity as invisible. */
  private static final byte FLAG_INVISIBLE = 0x20;
  /** Bit of the flag byte marking an entity as glowing, from 1.9 on. */
  private static final byte FLAG_GLOWING = 0x40;

  /** Bit of the armour stand flag byte selecting the small variant. */
  private static final byte ARMOR_STAND_SMALL = 0x01;
  /** Bit of the armour stand flag byte that switched gravity off before 1.10 had a field for it. */
  private static final byte ARMOR_STAND_NO_GRAVITY = 0x02;
  /** Bit of the armour stand flag byte removing the hitbox entirely. */
  private static final byte ARMOR_STAND_MARKER = 0x10;

  /** How many ticks an arm swing takes, the vanilla length the 26.3 swing packet asks for. */
  private static final int SWING_TICKS = 6;

  private static final Optional<Vector3d> NO_VELOCITY = Optional.of(Vector3d.zero());
  private static final Map<NpcPose, EntityPose> POSES;
  private static final EnumSet<WrapperPlayServerPlayerInfoUpdate.Action> ADD_ACTIONS = EnumSet.of(
    WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
    WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_HAT,
    WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED,
    WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LATENCY,
    WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_GAME_MODE,
    WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME);

  static {
    Map<NpcPose, EntityPose> poses = new EnumMap<>(NpcPose.class);
    poses.put(NpcPose.STANDING, EntityPose.STANDING);
    poses.put(NpcPose.FALL_FLYING, EntityPose.FALL_FLYING);
    poses.put(NpcPose.SLEEPING, EntityPose.SLEEPING);
    poses.put(NpcPose.SWIMMING, EntityPose.SWIMMING);
    poses.put(NpcPose.SPIN_ATTACK, EntityPose.SPIN_ATTACK);
    poses.put(NpcPose.CROUCHING, EntityPose.CROUCHING);
    poses.put(NpcPose.DYING, EntityPose.DYING);
    POSES = Collections.unmodifiableMap(poses);
  }

  private final PlayerManager players;
  private final MinecraftVersion version;
  private final MetadataIndexes indexes;
  private final byte armorStandFlags;

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
    this.indexes = MetadataIndexes.of(parsed);
    this.armorStandFlags = parsed.atLeast(1, 10)
      ? (byte) (ARMOR_STAND_SMALL | ARMOR_STAND_MARKER)
      : (byte) (ARMOR_STAND_SMALL | ARMOR_STAND_MARKER | ARMOR_STAND_NO_GRAVITY);
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
   * Returns whether an item line needs an armour stand to sit on.
   *
   * <p>Before 1.10 an entity cannot switch its gravity off, and clients simulate dropped items, so a
   * lone item would fall out of the hologram. Riding an invisible marker armour stand keeps it in
   * place: a passenger is positioned by its vehicle and never falls.
   *
   * @return {@code true} if every item line is a pair of entities, the item and its vehicle.
   * @since 1.0.0
   */
  public boolean itemLinesRide() {
    return !this.version.atLeast(1, 10);
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
   * <p>Players have their own spawn packet up to 1.20.1 and go through the generic one from 1.20.2
   * on.
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
    Vector3d position = positionOf(location);
    PacketWrapper<?> wrapper;
    if (this.version.atLeast(1, 20, 2)) {
      wrapper = new WrapperPlayServerSpawnEntity(
        entityId,
        Optional.of(uniqueId),
        EntityTypes.PLAYER,
        position,
        location.getPitch(),
        location.getYaw(),
        location.getYaw(),
        0,
        NO_VELOCITY);
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
   * packet carrying five ids is cheaper than five packets carrying one. The exception is 1.17.0,
   * whose destroy packet carries exactly one id.
   *
   * @param viewer    the player to send the packet to.
   * @param entityIds the entity ids to remove; nothing is sent for an empty array.
   * @since 1.0.0
   */
  public void sendDespawn(@NotNull Player viewer, int @NotNull ... entityIds) {
    if (entityIds.length == 0) {
      return;
    }
    if (this.version.equals(MinecraftVersion.of(1, 17, 0))) {
      for (int entityId : entityIds) {
        this.send(viewer, new WrapperPlayServerDestroyEntities(entityId));
      }
      return;
    }
    this.send(viewer, new WrapperPlayServerDestroyEntities(entityIds));
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
    this.spawnArmorStand(viewer, entityId, location);
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
      new com.github.retrooper.packetevents.protocol.world.Location(positionOf(location), 0.0f, 0.0f),
      false));
  }

  /**
   * Spawns a hovering dropped item, the entity an item hologram line is made of.
   *
   * <p>The client renders a dropped item bobbing and slowly rotating on its own, which is exactly
   * the effect an item line wants, so no animation has to be driven from the server. When
   * {@link #itemLinesRide()} says so, an invisible armour stand is spawned under it as well and the
   * item is put on it.
   *
   * @param viewer    the player to send the packets to.
   * @param entityId  the entity id of the item.
   * @param vehicleId the entity id of the armour stand the item rides, unused from 1.10 on.
   * @param location  where the item floats.
   * @since 1.0.0
   */
  public void sendItemLineSpawn(@NotNull Player viewer, int entityId, int vehicleId, @NotNull Location location) {
    if (this.itemLinesRide()) {
      this.spawnArmorStand(viewer, vehicleId, location);
      this.sendMetadata(viewer, vehicleId, this.buildVehicleMetadata());
    }
    this.spawnObject(viewer, entityId, EntityTypes.ITEM, location);
    if (this.itemLinesRide()) {
      this.mount(viewer, vehicleId, entityId);
    }
  }

  /**
   * Moves an item hologram line on the client of a viewer.
   *
   * @param viewer    the player to send the packet to.
   * @param entityId  the entity id of the item.
   * @param vehicleId the entity id of the armour stand the item rides, unused from 1.10 on.
   * @param location  the destination.
   * @since 1.0.0
   */
  public void sendItemLineMove(@NotNull Player viewer, int entityId, int vehicleId, @NotNull Location location) {
    this.sendHologramLineMove(viewer, this.itemLinesRide() ? vehicleId : entityId, location);
  }

  /**
   * Builds the metadata that puts a stack into a floating item line.
   *
   * <p>The stack is an optional value on 1.9 and 1.10 and a plain one before and after.
   *
   * @param item the item to display.
   * @return the metadata entries describing the line.
   * @since 1.0.0
   */
  @NotNull
  public List<EntityData<?>> buildItemLineMetadata(@NotNull ItemStack item) {
    com.github.retrooper.packetevents.protocol.item.ItemStack stack = SpigotReflectionUtil.decodeBukkitItemStack(item);
    List<EntityData<?>> data = new ArrayList<>(2);
    if (this.version.atLeast(1, 10)) {
      data.add(new EntityData<>(MetadataIndexes.NO_GRAVITY, EntityDataTypes.BOOLEAN, Boolean.TRUE));
    }
    if (this.version.atLeast(1, 9) && !this.version.atLeast(1, 11)) {
      data.add(new EntityData<>(this.indexes.itemStack(), EntityDataTypes.OPTIONAL_ITEMSTACK, Optional.of(stack)));
    } else {
      data.add(new EntityData<>(this.indexes.itemStack(), EntityDataTypes.ITEMSTACK, stack));
    }
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
    data.add(new EntityData<>(MetadataIndexes.FLAGS, EntityDataTypes.BYTE, FLAG_INVISIBLE));
    data.add(this.customName(text));
    if (this.version.atLeast(1, 9)) {
      data.add(new EntityData<>(MetadataIndexes.CUSTOM_NAME_VISIBLE, EntityDataTypes.BOOLEAN, Boolean.TRUE));
    } else {
      data.add(new EntityData<>(MetadataIndexes.CUSTOM_NAME_VISIBLE, EntityDataTypes.BYTE, (byte) 1));
    }
    if (this.version.atLeast(1, 10)) {
      data.add(new EntityData<>(MetadataIndexes.NO_GRAVITY, EntityDataTypes.BOOLEAN, Boolean.TRUE));
    }
    data.add(new EntityData<>(this.indexes.armorStandFlags(), EntityDataTypes.BYTE, this.armorStandFlags));
    return data;
  }

  /**
   * Builds the custom name entry of a hologram line.
   *
   * <p>Up to 1.12 the name is a plain string with section sign colour codes; from 1.13 on it is an
   * optional text component. Both the section sign and the ampersand are accepted, because a plugin
   * reading its lines from a configuration file will almost always have ampersands in hand.
   *
   * @param text the text to show.
   * @return the metadata entry carrying the name.
   * @since 1.0.0
   */
  @NotNull
  private EntityData<?> customName(@NotNull String text) {
    String legacy = ChatColor.translateAlternateColorCodes('&', text);
    if (this.version.atLeast(1, 13)) {
      Component component = LegacyComponentSerializer.legacySection().deserialize(legacy);
      return new EntityData<>(MetadataIndexes.CUSTOM_NAME, EntityDataTypes.OPTIONAL_ADV_COMPONENT, Optional.of(component));
    }
    return new EntityData<>(MetadataIndexes.CUSTOM_NAME, EntityDataTypes.STRING, legacy);
  }

  /**
   * Builds the metadata of the armour stand an item line rides before 1.10.
   *
   * @return the metadata entries describing the vehicle.
   * @since 1.0.0
   */
  @NotNull
  private List<EntityData<?>> buildVehicleMetadata() {
    List<EntityData<?>> data = new ArrayList<>(2);
    data.add(new EntityData<>(MetadataIndexes.FLAGS, EntityDataTypes.BYTE, FLAG_INVISIBLE));
    data.add(new EntityData<>(this.indexes.armorStandFlags(), EntityDataTypes.BYTE, this.armorStandFlags));
    return data;
  }

  /**
   * Rotates the body and the head of the NPC on the client of a viewer.
   *
   * <p>Two packets are needed: the rotation packet turns the body, and clients keep the head at its
   * previous angle unless a head look packet follows. 1.8 gets a teleport to the current position
   * instead of the rotation packet.
   *
   * @param viewer   the player to send the packets to.
   * @param entityId the entity id of the NPC.
   * @param location the current location, needed for the 1.8 fallback.
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
        new com.github.retrooper.packetevents.protocol.world.Location(positionOf(location), yaw, pitch),
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
        positionOf(location),
        location.getYaw(),
        location.getPitch()),
      true));
    this.send(viewer, new WrapperPlayServerEntityHeadLook(entityId, location.getYaw()));
  }

  /**
   * Plays an animation on the NPC for a single viewer.
   *
   * <p>The damage flash has its own packet from 1.19.4 on and arm swings have theirs from 26.3 on;
   * everything else still goes through the generic animation packet. The off hand swing is skipped
   * on 1.8, which has no off hand.
   *
   * @param viewer    the player to send the packet to.
   * @param entityId  the entity id of the NPC.
   * @param animation the animation to play.
   * @since 1.0.0
   */
  public void sendAnimation(@NotNull Player viewer, int entityId, @NotNull NpcAnimation animation) {
    switch (animation) {
      case SWING_MAIN_ARM:
        this.sendSwing(viewer, entityId, InteractionHand.MAIN_HAND);
        break;
      case SWING_OFF_HAND:
        if (this.version.atLeast(1, 9)) {
          this.sendSwing(viewer, entityId, InteractionHand.OFF_HAND);
        }
        break;
      case TAKE_DAMAGE:
        if (this.version.atLeast(1, 19, 4)) {
          this.send(viewer, new WrapperPlayServerHurtAnimation(entityId, 0.0f));
        } else {
          this.sendAnimation(viewer, entityId, WrapperPlayServerEntityAnimation.EntityAnimationType.HURT);
        }
        break;
      case LEAVE_BED:
        this.sendAnimation(viewer, entityId, WrapperPlayServerEntityAnimation.EntityAnimationType.WAKE_UP);
        break;
      case CRITICAL_HIT:
        this.sendAnimation(viewer, entityId, WrapperPlayServerEntityAnimation.EntityAnimationType.CRITICAL_HIT);
        break;
      case MAGIC_CRITICAL_HIT:
        this.sendAnimation(
          viewer, entityId, WrapperPlayServerEntityAnimation.EntityAnimationType.MAGIC_CRITICAL_HIT);
        break;
      default:
        break;
    }
  }

  /**
   * Swings one arm of the NPC for a single viewer.
   *
   * @param viewer   the player to send the packet to.
   * @param entityId the entity id of the NPC.
   * @param hand     the arm to swing.
   * @since 1.0.0
   */
  private void sendSwing(@NotNull Player viewer, int entityId, @NotNull InteractionHand hand) {
    if (this.version.atLeast(26, 3)) {
      this.send(viewer, new WrapperPlayServerSwingAnimation(
        entityId, hand, new ItemSwingAnimation(ItemSwingAnimation.Type.WHACK, SWING_TICKS)));
      return;
    }
    this.sendAnimation(viewer, entityId, hand == InteractionHand.MAIN_HAND
      ? WrapperPlayServerEntityAnimation.EntityAnimationType.SWING_MAIN_ARM
      : WrapperPlayServerEntityAnimation.EntityAnimationType.SWING_OFF_HAND);
  }

  /**
   * Sends one generic animation packet.
   *
   * @param viewer   the player to send the packet to.
   * @param entityId the entity id of the NPC.
   * @param type     the animation to play.
   * @since 1.0.0
   */
  private void sendAnimation(
    @NotNull Player viewer,
    int entityId,
    @NotNull WrapperPlayServerEntityAnimation.EntityAnimationType type
  ) {
    this.send(viewer, new WrapperPlayServerEntityAnimation(entityId, type));
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
   * Sends a metadata update for an entity to a single viewer.
   *
   * @param viewer   the player to send the packet to.
   * @param entityId the entity id of the NPC or the line.
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
   * <p>From 1.14 on sneaking and the crouching pose are two separate fields, and a client that
   * receives only the flag keeps the standing hitbox and draws the name tag at the wrong height, so
   * a sneaking NPC also gets the crouching pose. Before 1.14 there are no poses, and the crouching
   * one falls back to the sneaking flag.
   *
   * @param sneaking whether the NPC is sneaking.
   * @param glowing  whether the NPC has a glowing outline, ignored on 1.8.
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
    boolean poses = this.version.atLeast(1, 14);

    byte flags = 0;
    if (sneaking || (!poses && pose == NpcPose.CROUCHING)) {
      flags |= FLAG_SNEAKING;
    }
    if (glowing && this.version.atLeast(1, 9)) {
      flags |= FLAG_GLOWING;
    }
    data.add(new EntityData<>(MetadataIndexes.FLAGS, EntityDataTypes.BYTE, flags));
    data.add(new EntityData<>(this.indexes.skinLayers(), EntityDataTypes.BYTE, SkinLayer.pack(layers)));

    if (poses) {
      NpcPose effectivePose = sneaking && pose == NpcPose.STANDING ? NpcPose.CROUCHING : pose;
      data.add(new EntityData<>(MetadataIndexes.POSE, EntityDataTypes.ENTITY_POSE, POSES.get(effectivePose)));
    }
    return data;
  }

  /**
   * Spawns an armour stand, through the mob spawn packet up to 1.18 and the generic one after.
   *
   * @param viewer   the player to send the packet to.
   * @param entityId the entity id of the armour stand.
   * @param location where it stands.
   * @since 1.0.0
   */
  private void spawnArmorStand(@NotNull Player viewer, int entityId, @NotNull Location location) {
    if (this.version.atLeast(1, 19)) {
      this.spawnObject(viewer, entityId, EntityTypes.ARMOR_STAND, location);
      return;
    }
    this.send(viewer, new WrapperPlayServerSpawnLivingEntity(
      entityId,
      UUID.randomUUID(),
      EntityTypes.ARMOR_STAND,
      positionOf(location),
      0.0f,
      0.0f,
      0.0f,
      Vector3d.zero(),
      Collections.<EntityData<?>>emptyList()));
  }

  /**
   * Spawns a motionless entity through the generic spawn packet.
   *
   * <p>The velocity is always sent as zero, because the packet library otherwise fills in a default
   * that sends a client-simulated entity such as a dropped item flying off.
   *
   * @param viewer   the player to send the packet to.
   * @param entityId the entity id to spawn.
   * @param type     what kind of entity it is.
   * @param location where it appears.
   * @since 1.0.0
   */
  private void spawnObject(
    @NotNull Player viewer,
    int entityId,
    @NotNull EntityType type,
    @NotNull Location location
  ) {
    this.send(viewer, new WrapperPlayServerSpawnEntity(
      entityId,
      Optional.of(UUID.randomUUID()),
      type,
      positionOf(location),
      0.0f,
      0.0f,
      0.0f,
      0,
      NO_VELOCITY));
  }

  /**
   * Puts one entity on top of another, the 1.8 way or the 1.9 way.
   *
   * @param viewer    the player to send the packet to.
   * @param vehicleId the entity that is ridden.
   * @param riderId   the entity riding it.
   * @since 1.0.0
   */
  private void mount(@NotNull Player viewer, int vehicleId, int riderId) {
    if (this.version.atLeast(1, 9)) {
      this.send(viewer, new WrapperPlayServerSetPassengers(vehicleId, new int[] {riderId}));
    } else {
      this.send(viewer, new WrapperPlayServerAttachEntity(riderId, vehicleId, false));
    }
  }

  /**
   * Translates a Bukkit equipment slot into its protocol counterpart.
   *
   * <p>Matched by name, because the off hand constant does not exist in the 1.8 API and referencing
   * it would fail there as soon as this method runs.
   *
   * @param slot the Bukkit slot.
   * @return the protocol slot, or {@code null} if this server version cannot display it.
   * @since 1.0.0
   */
  @Nullable
  private com.github.retrooper.packetevents.protocol.player.EquipmentSlot translate(@NotNull EquipmentSlot slot) {
    switch (slot.name()) {
      case "HAND":
        return com.github.retrooper.packetevents.protocol.player.EquipmentSlot.MAIN_HAND;
      case "OFF_HAND":
        return this.version.atLeast(1, 9)
          ? com.github.retrooper.packetevents.protocol.player.EquipmentSlot.OFF_HAND
          : null;
      case "FEET":
        return com.github.retrooper.packetevents.protocol.player.EquipmentSlot.BOOTS;
      case "LEGS":
        return com.github.retrooper.packetevents.protocol.player.EquipmentSlot.LEGGINGS;
      case "CHEST":
        return com.github.retrooper.packetevents.protocol.player.EquipmentSlot.CHEST_PLATE;
      case "HEAD":
        return com.github.retrooper.packetevents.protocol.player.EquipmentSlot.HELMET;
      default:
        return null;
    }
  }

  /**
   * Converts a Bukkit location into a protocol position.
   *
   * @param location the location to convert.
   * @return its coordinates.
   * @since 1.0.0
   */
  @NotNull
  private static Vector3d positionOf(@NotNull Location location) {
    return new Vector3d(location.getX(), location.getY(), location.getZ());
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
