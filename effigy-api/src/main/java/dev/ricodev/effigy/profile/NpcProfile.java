/*
 * This file is part of Effigy, licensed under the MIT License.
 * Copyright (c) 2026 ricodevvv and contributors.
 */
package dev.ricodev.effigy.profile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

/**
 * The identity of an NPC: a unique id, a name and the properties that carry its skin.
 *
 * <p>This is the library's counterpart to a Mojang game profile, minus the server internals. It is
 * immutable, so a resolved profile can be cached, shared between NPCs and handed across threads
 * without copying.
 *
 * <p>Two kinds of profile matter in practice:
 *
 * <ul>
 *   <li>an <em>unskinned</em> profile, created by {@link #of(String)}, which renders as the default
 *       Steve or Alex skin derived from its unique id;
 *   <li>a <em>skinned</em> profile, which additionally holds a {@value ProfileProperty#TEXTURES}
 *       property and is normally produced by a {@link ProfileResolver}.
 * </ul>
 *
 * @see ProfileResolver
 * @since 1.0.0
 */
public final class NpcProfile {

  /**
   * Pattern every profile name must match: 1 to 16 word characters, as enforced by the vanilla
   * client. Longer or exotic names are silently dropped by clients and would make the NPC invisible.
   */
  private static final Pattern NAME_PATTERN = Pattern.compile("^\\w{1,16}$");

  private final UUID uniqueId;
  private final String name;
  private final List<ProfileProperty> properties;

  private NpcProfile(@NotNull UUID uniqueId, @NotNull String name, @NotNull List<ProfileProperty> properties) {
    this.uniqueId = uniqueId;
    this.name = name;
    this.properties = properties;
  }

  /**
   * Creates an unskinned profile with a random unique id.
   *
   * @param name the name of the NPC, at most 16 word characters.
   * @return the new profile.
   * @throws NullPointerException     if {@code name} is {@code null}.
   * @throws IllegalArgumentException if {@code name} is empty, longer than 16 characters or contains
   *     characters the client rejects.
   * @since 1.0.0
   */
  @NotNull
  public static NpcProfile of(@NotNull String name) {
    return of(UUID.randomUUID(), name, Collections.emptyList());
  }

  /**
   * Creates an unskinned profile with a fixed unique id.
   *
   * <p>Use this when the NPC has to keep the same identity across restarts, for instance because the
   * skin is cached under that id.
   *
   * @param uniqueId the unique id to use.
   * @param name     the name of the NPC.
   * @return the new profile.
   * @throws NullPointerException     if any argument is {@code null}.
   * @throws IllegalArgumentException if the name is not accepted by the client.
   * @since 1.0.0
   */
  @NotNull
  public static NpcProfile of(@NotNull UUID uniqueId, @NotNull String name) {
    return of(uniqueId, name, Collections.emptyList());
  }

  /**
   * Creates a profile with the given properties.
   *
   * @param uniqueId   the unique id to use.
   * @param name       the name of the NPC.
   * @param properties the properties of the profile; copied defensively.
   * @return the new profile.
   * @throws NullPointerException     if any argument or element is {@code null}.
   * @throws IllegalArgumentException if the name is not accepted by the client.
   * @since 1.0.0
   */
  @NotNull
  public static NpcProfile of(
    @NotNull UUID uniqueId,
    @NotNull String name,
    @NotNull Collection<ProfileProperty> properties
  ) {
    Objects.requireNonNull(uniqueId, "uniqueId");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(properties, "properties");
    if (!NAME_PATTERN.matcher(name).matches()) {
      throw new IllegalArgumentException(
        "Profile name '" + name + "' is not a valid Minecraft name (1-16 word characters)");
    }

    List<ProfileProperty> copy = new ArrayList<>(properties.size());
    for (ProfileProperty property : properties) {
      copy.add(Objects.requireNonNull(property, "property"));
    }
    return new NpcProfile(uniqueId, name, Collections.unmodifiableList(copy));
  }

  /**
   * Returns the unique id of this profile.
   *
   * @return the profile unique id.
   * @since 1.0.0
   */
  @NotNull
  public UUID uniqueId() {
    return this.uniqueId;
  }

  /**
   * Returns the name of this profile.
   *
   * @return the profile name.
   * @since 1.0.0
   */
  @NotNull
  public String name() {
    return this.name;
  }

  /**
   * Returns the properties of this profile.
   *
   * @return an unmodifiable list of properties, possibly empty.
   * @since 1.0.0
   */
  @NotNull
  @Unmodifiable
  public List<ProfileProperty> properties() {
    return this.properties;
  }

  /**
   * Returns the property with the given name.
   *
   * @param name the property name to look for.
   * @return the matching property, or an empty optional if this profile has none.
   * @throws NullPointerException if {@code name} is {@code null}.
   * @since 1.0.0
   */
  @NotNull
  public Optional<ProfileProperty> property(@NotNull String name) {
    Objects.requireNonNull(name, "name");
    for (ProfileProperty property : this.properties) {
      if (property.name().equals(name)) {
        return Optional.of(property);
      }
    }
    return Optional.empty();
  }

  /**
   * Returns whether this profile carries a skin texture.
   *
   * @return {@code true} if a {@value ProfileProperty#TEXTURES} property is present.
   * @since 1.0.0
   */
  public boolean skinned() {
    return this.property(ProfileProperty.TEXTURES).isPresent();
  }

  /**
   * Returns a copy of this profile with a different unique id.
   *
   * @param uniqueId the unique id of the new profile.
   * @return a new profile, or this one if the id already matches.
   * @throws NullPointerException if {@code uniqueId} is {@code null}.
   * @since 1.0.0
   */
  @NotNull
  public NpcProfile withUniqueId(@NotNull UUID uniqueId) {
    Objects.requireNonNull(uniqueId, "uniqueId");
    return this.uniqueId.equals(uniqueId) ? this : new NpcProfile(uniqueId, this.name, this.properties);
  }

  /**
   * Returns a copy of this profile with a different name.
   *
   * @param name the name of the new profile.
   * @return a new profile, or this one if the name already matches.
   * @throws NullPointerException     if {@code name} is {@code null}.
   * @throws IllegalArgumentException if the name is not accepted by the client.
   * @since 1.0.0
   */
  @NotNull
  public NpcProfile withName(@NotNull String name) {
    Objects.requireNonNull(name, "name");
    if (this.name.equals(name)) {
      return this;
    }
    if (!NAME_PATTERN.matcher(name).matches()) {
      throw new IllegalArgumentException(
        "Profile name '" + name + "' is not a valid Minecraft name (1-16 word characters)");
    }
    return new NpcProfile(this.uniqueId, name, this.properties);
  }

  /**
   * Returns a copy of this profile with the skin of another profile.
   *
   * <p>The typical use is to keep a stable identity while swapping skins: the receiver supplies the
   * unique id and the name, the argument supplies the texture properties.
   *
   * @param other the profile to take the properties from.
   * @return a new profile with the identity of this one and the skin of the other.
   * @throws NullPointerException if {@code other} is {@code null}.
   * @since 1.0.0
   */
  @NotNull
  public NpcProfile withSkinOf(@NotNull NpcProfile other) {
    Objects.requireNonNull(other, "other");
    return new NpcProfile(this.uniqueId, this.name, other.properties);
  }

  /**
   * Returns the offline mode unique id that a player with this profile name would have.
   *
   * <p>Servers running in offline mode derive ids from the name instead of asking Mojang. Building
   * an NPC with the derived id makes it indistinguishable from a real offline mode player, which
   * matters for plugins that key data by unique id.
   *
   * @return the offline unique id of {@link #name()}.
   * @since 1.0.0
   */
  @NotNull
  public UUID offlineUniqueId() {
    return UUID.nameUUIDFromBytes(("OfflinePlayer:" + this.name).getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public boolean equals(@Nullable Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof NpcProfile)) {
      return false;
    }
    NpcProfile that = (NpcProfile) other;
    return this.uniqueId.equals(that.uniqueId)
      && this.name.equals(that.name)
      && this.properties.equals(that.properties);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.uniqueId, this.name, this.properties);
  }

  @Override
  public String toString() {
    return "NpcProfile(name=" + this.name + ", uniqueId=" + this.uniqueId + ", skinned=" + this.skinned() + ')';
  }
}
