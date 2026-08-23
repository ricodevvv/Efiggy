/*
 * This file is part of Effigy, licensed under the MIT License.
 * Copyright (c) 2026 ricodevvv and contributors.
 */
package dev.ricodev.effigy.profile;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A single signed property of a game profile, in practice almost always the skin texture.
 *
 * <p>A property is a name, a base64 encoded value and an optional signature produced by Mojang. The
 * signature is what allows an unmodified client to render a skin that does not belong to the account
 * it is logged in with, so dropping it is the usual reason for an NPC to end up with the default
 * Steve or Alex skin.
 *
 * <p>Instances are immutable and safe to share between threads.
 *
 * @since 1.0.0
 */
public final class ProfileProperty {

  /** Name of the property that carries the skin and cape of a profile. */
  public static final String TEXTURES = "textures";

  private final String name;
  private final String value;
  private final String signature;

  private ProfileProperty(@NotNull String name, @NotNull String value, @Nullable String signature) {
    this.name = name;
    this.value = value;
    this.signature = signature;
  }

  /**
   * Creates a signed property.
   *
   * @param name      the property name, for example {@value #TEXTURES}.
   * @param value     the base64 encoded property value.
   * @param signature the Mojang signature of the value, or {@code null} if the property is unsigned.
   * @return the new property.
   * @throws NullPointerException if {@code name} or {@code value} is {@code null}.
   * @since 1.0.0
   */
  @NotNull
  public static ProfileProperty of(@NotNull String name, @NotNull String value, @Nullable String signature) {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(value, "value");
    return new ProfileProperty(name, value, signature);
  }

  /**
   * Creates an unsigned property.
   *
   * <p>Unsigned skin textures are only rendered by clients connected to a server in offline mode or
   * behind a proxy that re-signs them, so prefer {@link #of(String, String, String)} when a
   * signature is available.
   *
   * @param name  the property name.
   * @param value the base64 encoded property value.
   * @return the new property.
   * @throws NullPointerException if any argument is {@code null}.
   * @since 1.0.0
   */
  @NotNull
  public static ProfileProperty of(@NotNull String name, @NotNull String value) {
    return of(name, value, null);
  }

  /**
   * Returns the name of this property.
   *
   * @return the property name.
   * @since 1.0.0
   */
  @NotNull
  public String name() {
    return this.name;
  }

  /**
   * Returns the base64 encoded value of this property.
   *
   * @return the property value.
   * @since 1.0.0
   */
  @NotNull
  public String value() {
    return this.value;
  }

  /**
   * Returns the Mojang signature of this property.
   *
   * @return the signature, or {@code null} if this property is unsigned.
   * @since 1.0.0
   */
  @Nullable
  public String signature() {
    return this.signature;
  }

  /**
   * Returns whether this property carries a signature.
   *
   * @return {@code true} if {@link #signature()} is not {@code null}.
   * @since 1.0.0
   */
  public boolean signed() {
    return this.signature != null;
  }

  @Override
  public boolean equals(@Nullable Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof ProfileProperty)) {
      return false;
    }
    ProfileProperty that = (ProfileProperty) other;
    return this.name.equals(that.name)
      && this.value.equals(that.value)
      && Objects.equals(this.signature, that.signature);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.name, this.value, this.signature);
  }

  @Override
  public String toString() {
    return "ProfileProperty(name=" + this.name + ", signed=" + this.signed() + ')';
  }
}
