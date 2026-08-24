/*
 * This file is part of Effigy, licensed under the MIT License.
 * Copyright (c) 2026 ricodevvv and contributors.
 */
package dev.ricodev.effigy.hologram;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.bukkit.ChatColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

/**
 * A looping sequence of text frames shown on one hologram line.
 *
 * <p>An animation is precomputed and immutable: the frames are built once, when the animation is
 * created, and the hologram merely walks the list. Nothing is generated per tick and nothing is
 * generated per viewer, so an animated line costs one small metadata packet per viewer per frame
 * change and no CPU in between.
 *
 * <pre>{@code
 * npc.hologram().content(
 *     HologramLine.animated(TextAnimation.wave("&7Village Shop", ChatColor.YELLOW, Duration.ofMillis(80))),
 *     HologramLine.text("&8Right click to browse"));
 * }</pre>
 *
 * <p><strong>Colour codes are respected.</strong> The generators below count and cut on visible
 * characters, never on the {@code &a} or {@code §a} pairs in between, so
 * {@link #typewriter(String, Duration)} on {@code "&eShop"} reveals {@code S}, {@code h}, {@code o},
 * {@code p} in yellow rather than revealing the {@code &} and the {@code e} as if they were letters.
 *
 * <p>Frames are capped at {@value #MAX_FRAMES}. An animation longer than that is almost always a
 * mistake, and every frame is a string held for the lifetime of the NPC.
 *
 * @see HologramLine#animated(TextAnimation)
 * @since 1.0.0
 */
public final class TextAnimation {

  /** The largest number of frames an animation may hold. */
  public static final int MAX_FRAMES = 512;

  /** The shortest interval that is worth sending; one server tick. */
  private static final Duration MIN_INTERVAL = Duration.ofMillis(50);

  private final List<String> frames;
  private final Duration interval;

  private TextAnimation(@NotNull List<String> frames, @NotNull Duration interval) {
    this.frames = frames;
    this.interval = interval;
  }

  /**
   * Creates an animation from explicit frames.
   *
   * @param interval how long each frame is shown; rounded up to one tick.
   * @param frames   the frames, shown in order and then looped.
   * @return the animation.
   * @throws NullPointerException     if any argument or frame is {@code null}.
   * @throws IllegalArgumentException if there are no frames or more than {@value #MAX_FRAMES}.
   * @since 1.0.0
   */
  @NotNull
  public static TextAnimation of(@NotNull Duration interval, @NotNull List<String> frames) {
    Objects.requireNonNull(interval, "interval");
    Objects.requireNonNull(frames, "frames");
    if (frames.isEmpty()) {
      throw new IllegalArgumentException("An animation needs at least one frame");
    }
    if (frames.size() > MAX_FRAMES) {
      throw new IllegalArgumentException(
        "An animation may hold at most " + MAX_FRAMES + " frames, got " + frames.size());
    }

    List<String> copy = new ArrayList<>(frames.size());
    for (String frame : frames) {
      copy.add(Objects.requireNonNull(frame, "frame"));
    }
    Duration effective = interval.compareTo(MIN_INTERVAL) < 0 ? MIN_INTERVAL : interval;
    return new TextAnimation(Collections.unmodifiableList(copy), effective);
  }

  /**
   * Creates an animation from explicit frames.
   *
   * @param interval how long each frame is shown; rounded up to one tick.
   * @param frames   the frames, shown in order and then looped.
   * @return the animation.
   * @throws NullPointerException     if any argument or frame is {@code null}.
   * @throws IllegalArgumentException if there are no frames or more than {@value #MAX_FRAMES}.
   * @since 1.0.0
   */
  @NotNull
  public static TextAnimation of(@NotNull Duration interval, @NotNull String... frames) {
    Objects.requireNonNull(frames, "frames");
    return of(interval, Arrays.asList(frames));
  }

  /**
   * Reveals the text one character at a time, then holds the finished text before looping.
   *
   * @param text     the text to type out, colour codes included.
   * @param interval how long each character takes to appear.
   * @return the animation.
   * @throws NullPointerException     if any argument is {@code null}.
   * @throws IllegalArgumentException if the text has no visible characters.
   * @since 1.0.0
   */
  @NotNull
  public static TextAnimation typewriter(@NotNull String text, @NotNull Duration interval) {
    return typewriter(text, interval, Duration.ofSeconds(2));
  }

  /**
   * Reveals the text one character at a time, then holds the finished text before looping.
   *
   * @param text     the text to type out, colour codes included.
   * @param interval how long each character takes to appear.
   * @param hold     how long the completed text stays before the animation loops.
   * @return the animation.
   * @throws NullPointerException     if any argument is {@code null}.
   * @throws IllegalArgumentException if the text has no visible characters.
   * @since 1.0.0
   */
  @NotNull
  public static TextAnimation typewriter(
    @NotNull String text,
    @NotNull Duration interval,
    @NotNull Duration hold
  ) {
    Objects.requireNonNull(text, "text");
    Objects.requireNonNull(interval, "interval");
    Objects.requireNonNull(hold, "hold");

    int length = visibleLength(text);
    if (length == 0) {
      throw new IllegalArgumentException("The text has no visible characters to type out");
    }

    List<String> frames = new ArrayList<>(length);
    for (int visible = 1; visible <= length; visible++) {
      frames.add(visiblePrefix(text, visible));
    }

    // The hold is expressed as repeated final frames, so the hologram only has to walk one list.
    long holdFrames = Math.max(0L, hold.toMillis() / Math.max(1L, interval.toMillis()));
    for (long index = 0; index < holdFrames && frames.size() < MAX_FRAMES; index++) {
      frames.add(text);
    }
    return of(interval, frames);
  }

  /**
   * Moves a brighter colour across the text one character at a time.
   *
   * <p>The classic shimmering server title. The text keeps whatever colours it already carries; the
   * highlight is applied to a single character and removed again after it.
   *
   * @param text      the text to animate, colour codes included.
   * @param highlight the colour of the moving character.
   * @param interval  how long the highlight rests on each character.
   * @return the animation.
   * @throws NullPointerException     if any argument is {@code null}.
   * @throws IllegalArgumentException if the text has no visible characters.
   * @since 1.0.0
   */
  @NotNull
  public static TextAnimation wave(
    @NotNull String text,
    @NotNull ChatColor highlight,
    @NotNull Duration interval
  ) {
    Objects.requireNonNull(text, "text");
    Objects.requireNonNull(highlight, "highlight");
    Objects.requireNonNull(interval, "interval");

    int length = visibleLength(text);
    if (length == 0) {
      throw new IllegalArgumentException("The text has no visible characters to highlight");
    }

    List<String> frames = new ArrayList<>(length);
    for (int position = 0; position < length && frames.size() < MAX_FRAMES; position++) {
      frames.add(highlightAt(text, position, highlight));
    }
    return of(interval, frames);
  }

  /**
   * Cycles the whole text through a list of colours.
   *
   * <p>Any colour codes already in the text are stripped, since a code inside the text would
   * override the cycling colour from that point on.
   *
   * @param text     the text to colour.
   * @param interval how long each colour is shown.
   * @param colors   the colours to cycle through, in order.
   * @return the animation.
   * @throws NullPointerException     if any argument or colour is {@code null}.
   * @throws IllegalArgumentException if fewer than two colours are given.
   * @since 1.0.0
   */
  @NotNull
  public static TextAnimation colorCycle(
    @NotNull String text,
    @NotNull Duration interval,
    @NotNull ChatColor... colors
  ) {
    Objects.requireNonNull(text, "text");
    Objects.requireNonNull(interval, "interval");
    Objects.requireNonNull(colors, "colors");
    if (colors.length < 2) {
      throw new IllegalArgumentException("A colour cycle needs at least two colours");
    }

    String stripped = stripCodes(text);
    List<String> frames = new ArrayList<>(colors.length);
    for (ChatColor color : colors) {
      frames.add(Objects.requireNonNull(color, "color").toString() + stripped);
    }
    return of(interval, frames);
  }

  /**
   * Returns the frames of this animation.
   *
   * @return an immutable list of frames, never empty.
   * @since 1.0.0
   */
  @NotNull
  @Unmodifiable
  public List<String> frames() {
    return this.frames;
  }

  /**
   * Returns how long each frame is shown.
   *
   * @return the frame interval, never shorter than one tick.
   * @since 1.0.0
   */
  @NotNull
  public Duration interval() {
    return this.interval;
  }

  /**
   * Returns how many frames this animation has.
   *
   * @return the frame count, at least one.
   * @since 1.0.0
   */
  public int frameCount() {
    return this.frames.size();
  }

  /**
   * Returns one frame, wrapping around so that any index is valid.
   *
   * @param index the frame index; may exceed {@link #frameCount()}.
   * @return the frame at that position in the loop.
   * @throws IllegalArgumentException if {@code index} is negative.
   * @since 1.0.0
   */
  @NotNull
  public String frame(int index) {
    if (index < 0) {
      throw new IllegalArgumentException("The frame index must not be negative, got " + index);
    }
    return this.frames.get(index % this.frames.size());
  }

  /**
   * Counts the characters a player actually sees, ignoring colour codes.
   *
   * @param text the text to measure.
   * @return the number of visible characters.
   * @throws NullPointerException if {@code text} is {@code null}.
   * @since 1.0.0
   */
  public static int visibleLength(@NotNull String text) {
    Objects.requireNonNull(text, "text");
    int length = 0;
    for (int index = 0; index < text.length(); index++) {
      if (isCodeAt(text, index)) {
        index++;
      } else {
        length++;
      }
    }
    return length;
  }

  /**
   * Removes every colour and formatting code from a text.
   *
   * @param text the text to strip.
   * @return the text without codes.
   * @throws NullPointerException if {@code text} is {@code null}.
   * @since 1.0.0
   */
  @NotNull
  public static String stripCodes(@NotNull String text) {
    Objects.requireNonNull(text, "text");
    StringBuilder builder = new StringBuilder(text.length());
    for (int index = 0; index < text.length(); index++) {
      if (isCodeAt(text, index)) {
        index++;
      } else {
        builder.append(text.charAt(index));
      }
    }
    return builder.toString();
  }

  /**
   * Cuts a text after a number of visible characters, keeping every code up to that point.
   *
   * <p>Keeping the codes is what makes a half typed line stay the colour it will end up being,
   * instead of starting white and changing colour as the animation passes the code.
   *
   * @param text    the text to cut.
   * @param visible how many visible characters to keep.
   * @return the prefix.
   * @throws NullPointerException if {@code text} is {@code null}.
   * @since 1.0.0
   */
  @NotNull
  public static String visiblePrefix(@NotNull String text, int visible) {
    Objects.requireNonNull(text, "text");
    if (visible <= 0) {
      return "";
    }

    StringBuilder builder = new StringBuilder(text.length());
    int seen = 0;
    for (int index = 0; index < text.length(); index++) {
      if (isCodeAt(text, index)) {
        builder.append(text.charAt(index)).append(text.charAt(index + 1));
        index++;
        continue;
      }
      if (seen == visible) {
        break;
      }
      builder.append(text.charAt(index));
      seen++;
    }
    return builder.toString();
  }

  /**
   * Recolours a single visible character of a text.
   *
   * <p>The codes that were in force before the character are reapplied after it, so the rest of the
   * line keeps its original colour.
   *
   * @param text      the text to modify.
   * @param position  the index of the visible character to recolour.
   * @param highlight the colour to apply to it.
   * @return the text with one character recoloured.
   * @since 1.0.0
   */
  @NotNull
  private static String highlightAt(@NotNull String text, int position, @NotNull ChatColor highlight) {
    StringBuilder builder = new StringBuilder(text.length() + 8);
    StringBuilder active = new StringBuilder(4);
    int seen = 0;

    for (int index = 0; index < text.length(); index++) {
      if (isCodeAt(text, index)) {
        String code = text.substring(index, index + 2);
        // A colour resets the formatting that came before it, exactly like the client does.
        active.setLength(0);
        active.append(code);
        builder.append(code);
        index++;
        continue;
      }

      if (seen == position) {
        builder.append(highlight).append(text.charAt(index)).append(active);
      } else {
        builder.append(text.charAt(index));
      }
      seen++;
    }
    return builder.toString();
  }

  /**
   * Returns whether a colour or formatting code starts at the given index.
   *
   * @param text  the text to inspect.
   * @param index the index to look at.
   * @return {@code true} if a two character code starts there.
   * @since 1.0.0
   */
  private static boolean isCodeAt(@NotNull String text, int index) {
    char marker = text.charAt(index);
    if ((marker != '&' && marker != ChatColor.COLOR_CHAR) || index + 1 >= text.length()) {
      return false;
    }
    return "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(text.charAt(index + 1)) >= 0;
  }

  @Override
  public String toString() {
    return "TextAnimation(frames=" + this.frames.size() + ", interval=" + this.interval + ')';
  }
}
