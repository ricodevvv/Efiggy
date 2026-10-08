/*
 * This file is part of Effigy, licensed under the MIT License.
 * Copyright (c) 2026 ricodevvv and contributors.
 */
package dev.ricodev.effigy.bukkit.internal.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import org.bukkit.ChatColor;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Colours a scoreboard team the way the client of each version reads it.
 *
 * <p>Clients take the outline colour of a glowing entity from its team. Up to 1.12 they look at the
 * last colour code of the team prefix; from 1.13 on they look at the team colour instead. Bukkit
 * only has a setter for the latter from 1.12 on, and the library compiles against the 1.8 API, so
 * that setter is looked up once by reflection.
 *
 * <p>Not part of the public API.
 *
 * @since 1.0.0
 */
public final class TeamColors {

  @Nullable
  private static final Method SET_COLOR = findSetColor();

  private TeamColors() {
    throw new AssertionError("TeamColors is a utility class");
  }

  /**
   * Gives a team the colour its glowing members should have.
   *
   * @param team    the team to colour.
   * @param color   the colour to apply.
   * @param version the version of the server.
   * @throws NullPointerException  if any argument is {@code null}.
   * @throws IllegalStateException if the server refuses the colour.
   * @since 1.0.0
   */
  @SuppressWarnings("deprecation")
  public static void apply(@NotNull Team team, @NotNull ChatColor color, @NotNull MinecraftVersion version) {
    Objects.requireNonNull(team, "team");
    Objects.requireNonNull(color, "color");
    if (SET_COLOR == null || !version.atLeast(1, 13)) {
      team.setPrefix(color.toString());
      return;
    }

    try {
      SET_COLOR.invoke(team, color);
    } catch (InvocationTargetException exception) {
      throw new IllegalStateException("Unable to colour team " + team.getName(), exception.getCause());
    } catch (IllegalAccessException exception) {
      throw new IllegalStateException("Unable to colour team " + team.getName(), exception);
    }
  }

  /**
   * Finds {@code Team#setColor(ChatColor)}.
   *
   * @return the setter, or {@code null} on servers older than 1.12.
   * @since 1.0.0
   */
  @Nullable
  private static Method findSetColor() {
    try {
      return Team.class.getMethod("setColor", ChatColor.class);
    } catch (NoSuchMethodException exception) {
      return null;
    }
  }
}
