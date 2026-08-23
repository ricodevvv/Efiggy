/*
 * This file is part of Effigy, licensed under the MIT License.
 * Copyright (c) 2026 ricodevvv and contributors.
 */
package dev.ricodev.effigy.bukkit.internal.util;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A parsed {@code major.minor.patch} Minecraft version, ordered and comparable.
 *
 * <p>The protocol differences this library cares about are expressed as version ranges rather than
 * as constants of the packet library. That keeps the code readable, lets a single condition state
 * exactly which releases it covers, and means an update of the packet library cannot silently break
 * a comparison because an enum constant was renamed.
 *
 * <p>Not part of the public API.
 *
 * @since 1.0.0
 */
public final class MinecraftVersion implements Comparable<MinecraftVersion> {

  private final int major;
  private final int minor;
  private final int patch;

  private MinecraftVersion(int major, int minor, int patch) {
    this.major = major;
    this.minor = minor;
    this.patch = patch;
  }

  /**
   * Creates a version from its three components.
   *
   * @param major the major version, {@code 1} for every Java Edition release so far.
   * @param minor the minor version.
   * @param patch the patch version.
   * @return the version.
   * @since 1.0.0
   */
  @NotNull
  public static MinecraftVersion of(int major, int minor, int patch) {
    return new MinecraftVersion(major, minor, patch);
  }

  /**
   * Parses a release name such as {@code 1.21}, {@code 1.20.4} or {@code 1.19.3-pre1}.
   *
   * <p>Anything after the third component is ignored, and missing components default to zero, so
   * both {@code 1.21} and {@code 1.21.0} parse to the same version.
   *
   * @param releaseName the name to parse.
   * @return the parsed version, or {@code null} if the name does not start with a number.
   * @throws NullPointerException if {@code releaseName} is {@code null}.
   * @since 1.0.0
   */
  @Nullable
  public static MinecraftVersion parse(@NotNull String releaseName) {
    Objects.requireNonNull(releaseName, "releaseName");
    String[] parts = releaseName.split("[.\\-+_]");
    if (parts.length == 0) {
      return null;
    }

    int[] numbers = new int[3];
    for (int i = 0; i < 3; i++) {
      if (i >= parts.length) {
        break;
      }
      try {
        numbers[i] = Integer.parseInt(parts[i].trim());
      } catch (NumberFormatException exception) {
        // The first component has to be numeric; later ones may be a qualifier such as "pre1".
        if (i == 0) {
          return null;
        }
        break;
      }
    }
    return new MinecraftVersion(numbers[0], numbers[1], numbers[2]);
  }

  /**
   * Returns whether this version is the given one or newer.
   *
   * @param major the major version to compare against.
   * @param minor the minor version to compare against.
   * @param patch the patch version to compare against.
   * @return {@code true} if this version is at least the given one.
   * @since 1.0.0
   */
  public boolean atLeast(int major, int minor, int patch) {
    if (this.major != major) {
      return this.major > major;
    }
    if (this.minor != minor) {
      return this.minor > minor;
    }
    return this.patch >= patch;
  }

  /**
   * Returns whether this version is the given one or newer, ignoring the patch component.
   *
   * @param major the major version to compare against.
   * @param minor the minor version to compare against.
   * @return {@code true} if this version is at least the given one.
   * @since 1.0.0
   */
  public boolean atLeast(int major, int minor) {
    return this.atLeast(major, minor, 0);
  }

  /**
   * Returns the major component.
   *
   * @return the major version.
   * @since 1.0.0
   */
  public int major() {
    return this.major;
  }

  /**
   * Returns the minor component.
   *
   * @return the minor version.
   * @since 1.0.0
   */
  public int minor() {
    return this.minor;
  }

  /**
   * Returns the patch component.
   *
   * @return the patch version.
   * @since 1.0.0
   */
  public int patch() {
    return this.patch;
  }

  @Override
  public int compareTo(@NotNull MinecraftVersion other) {
    int result = Integer.compare(this.major, other.major);
    if (result != 0) {
      return result;
    }
    result = Integer.compare(this.minor, other.minor);
    return result != 0 ? result : Integer.compare(this.patch, other.patch);
  }

  @Override
  public boolean equals(@Nullable Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof MinecraftVersion)) {
      return false;
    }
    MinecraftVersion that = (MinecraftVersion) other;
    return this.major == that.major && this.minor == that.minor && this.patch == that.patch;
  }

  @Override
  public int hashCode() {
    return (this.major * 31 + this.minor) * 31 + this.patch;
  }

  @Override
  public String toString() {
    return this.major + "." + this.minor + "." + this.patch;
  }
}
