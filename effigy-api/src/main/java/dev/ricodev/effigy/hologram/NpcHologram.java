/*
 * This file is part of Effigy, licensed under the MIT License.
 * Copyright (c) 2026 ricodevvv and contributors.
 */
package dev.ricodev.effigy.hologram;

import dev.ricodev.effigy.Npc;
import java.util.List;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

/**
 * The floating text above an NPC.
 *
 * <p>Every NPC owns exactly one hologram, reachable through {@link Npc#hologram()}. It starts empty
 * and costs nothing until it has lines. Each line is a separate client-side entity, an invisible
 * marker armour stand for text and a hovering dropped item for an item line, sent only to the
 * viewers of the NPC. A hologram therefore inherits the visibility rule, the view distance and the
 * lifetime of the NPC it belongs to, and follows it when it is teleported.
 *
 * <pre>{@code
 * npc.hologram().lines(List.of(
 *     "&e&lVillage Shop",
 *     "&7Right click to browse"));
 * }</pre>
 *
 * <p><strong>Text, items and animations.</strong> {@link #lines(String...)} is the shorthand for a
 * stack of plain text. {@link #content(List)} takes {@link HologramLine} values and so can mix in
 * floating items and {@link TextAnimation animated} lines.
 *
 * <p><strong>Static or per viewer.</strong> The content above is shown to everybody.
 * {@link #renderer(HologramRenderer)} computes the content per viewer instead and takes precedence
 * while it is set. Colour codes may use either the section sign or an ampersand.
 *
 * <p><strong>Layout.</strong> The first line is rendered at the top and the last one anchors at
 * {@link #offsetY()} above the feet of the NPC. Lines stack upwards from there, each taking the
 * height of its own kind: {@link #lineSpacing()} for text and {@link #itemLineHeight()} for items.
 *
 * <p><strong>Threading.</strong> Reads are safe from any thread; everything that changes the
 * hologram must run on the main thread, exactly like the rest of {@link Npc}.
 *
 * @see HologramRenderer
 * @since 1.0.0
 */
public interface NpcHologram {

  /**
   * Returns the NPC this hologram belongs to.
   *
   * @return the owning NPC.
   * @since 1.0.0
   */
  @NotNull
  Npc npc();

  /**
   * Returns the static content of this hologram.
   *
   * <p>Empty when the hologram is unused or driven by a {@link HologramRenderer}.
   *
   * @return an immutable list of lines, the first one being the topmost.
   * @since 1.0.0
   */
  @NotNull
  @Unmodifiable
  List<HologramLine> content();

  /**
   * Replaces the static content and pushes the change to every viewer.
   *
   * <p>This is the full form of {@link #lines(String...)}: it accepts animated lines and item lines
   * as well as plain text. An empty list hides the hologram.
   *
   * @param content the lines to show, the first one being the topmost.
   * @throws NullPointerException  if {@code content} is {@code null} or contains {@code null}.
   * @throws IllegalStateException if called off the main thread or after the NPC was removed.
   * @since 1.0.0
   */
  void content(@NotNull List<HologramLine> content);

  /**
   * Replaces the static content and pushes the change to every viewer.
   *
   * @param content the lines to show, the first one being the topmost.
   * @throws NullPointerException  if {@code content} is {@code null} or contains {@code null}.
   * @throws IllegalStateException if called off the main thread or after the NPC was removed.
   * @since 1.0.0
   */
  void content(@NotNull HologramLine... content);

  /**
   * Replaces the static content with plain text lines.
   *
   * <p>Shorthand for wrapping each string in {@link HologramLine#text(String)}. Passing nothing
   * hides the hologram.
   *
   * @param lines the lines to show, the first one being the topmost.
   * @throws NullPointerException  if {@code lines} is {@code null} or contains {@code null}.
   * @throws IllegalStateException if called off the main thread or after the NPC was removed.
   * @since 1.0.0
   */
  void lines(@NotNull String... lines);

  /**
   * Returns the renderer producing per viewer text, if one is set.
   *
   * @return the current renderer, or {@code null} if the hologram uses its static lines.
   * @since 1.0.0
   */
  @Nullable
  HologramRenderer renderer();

  /**
   * Sets the renderer producing per viewer text.
   *
   * <p>While a renderer is set it takes precedence over {@link #content()}. Passing {@code null}
   * removes it and falls back to the static lines. Either way the change is pushed to every viewer
   * immediately.
   *
   * @param renderer the renderer to use, or {@code null} to use the static lines.
   * @throws IllegalStateException if called off the main thread or after the NPC was removed.
   * @since 1.0.0
   */
  void renderer(@Nullable HologramRenderer renderer);

  /**
   * Returns how far above the feet of the NPC the bottom line sits.
   *
   * @return the vertical offset in blocks, {@code 2.15} by default.
   * @since 1.0.0
   */
  double offsetY();

  /**
   * Sets how far above the feet of the NPC the bottom line sits.
   *
   * <p>The default puts the bottom line just above the head of a standing player. Nudge it when the
   * NPC sits in a vehicle, sleeps, or wears a tall custom head.
   *
   * @param offsetY the vertical offset in blocks.
   * @throws IllegalArgumentException if {@code offsetY} is not finite.
   * @throws IllegalStateException    if called off the main thread or after the NPC was removed.
   * @since 1.0.0
   */
  void offsetY(double offsetY);

  /**
   * Returns the vertical gap between two lines.
   *
   * @return the line spacing in blocks, {@code 0.28} by default.
   * @since 1.0.0
   */
  double lineSpacing();

  /**
   * Sets the vertical gap between two lines.
   *
   * <p>The default matches the height of a rendered name tag, so lines sit flush against each other
   * the way a multi line hologram is normally expected to look.
   *
   * @param lineSpacing the spacing in blocks; must be positive.
   * @throws IllegalArgumentException if {@code lineSpacing} is not positive or not finite.
   * @throws IllegalStateException    if called off the main thread or after the NPC was removed.
   * @since 1.0.0
   */
  void lineSpacing(double lineSpacing);

  /**
   * Returns the vertical space one item line takes up.
   *
   * @return the item line height in blocks, {@code 0.6} by default.
   * @since 1.0.0
   */
  double itemLineHeight();

  /**
   * Sets the vertical space one item line takes up.
   *
   * <p>A floating item is drawn much larger than a line of text, so it needs its own height; the
   * default leaves a small gap on either side of it.
   *
   * @param itemLineHeight the height in blocks; must be positive.
   * @throws IllegalArgumentException if {@code itemLineHeight} is not positive or not finite.
   * @throws IllegalStateException    if called off the main thread or after the NPC was removed.
   * @since 1.0.0
   */
  void itemLineHeight(double itemLineHeight);

  /**
   * Returns whether this hologram currently shows nothing.
   *
   * <p>A hologram with a renderer is never reported as empty, because whether it produces lines is
   * only known per viewer.
   *
   * @return {@code true} if there is no static content and no renderer.
   * @since 1.0.0
   */
  boolean isEmpty();

  /**
   * Rebuilds the hologram for every viewer.
   *
   * <p>Needed after the data a {@link HologramRenderer} reads has changed, since Effigy has no way
   * of noticing that by itself. Calls with static lines only are cheap but redundant, as
   * {@link #content(List)} already refreshes.
   *
   * @throws IllegalStateException if called off the main thread or after the NPC was removed.
   * @since 1.0.0
   */
  void refresh();

  /**
   * Rebuilds the hologram for one viewer.
   *
   * <p>Does nothing if the player does not see the NPC.
   *
   * @param viewer the viewer to update.
   * @throws NullPointerException  if {@code viewer} is {@code null}.
   * @throws IllegalStateException if called off the main thread or after the NPC was removed.
   * @since 1.0.0
   */
  void refresh(@NotNull Player viewer);
}
