/*
 * This file is part of Effigy, licensed under the MIT License.
 * Copyright (c) 2026 ricodevvv and contributors.
 */
package dev.ricodev.effigy.hologram;

import java.util.Objects;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One line of a hologram: a piece of text, an animated piece of text, or a floating item.
 *
 * <p>Lines are immutable and can be shared between holograms. Which kind a line is decides both what
 * is spawned for it and how much vertical space it takes up, so a hologram mixing text and items
 * still stacks correctly.
 *
 * <pre>{@code
 * npc.hologram().content(
 *     HologramLine.animated(TextAnimation.wave("&7Village Shop", ChatColor.YELLOW, Duration.ofMillis(80))),
 *     HologramLine.item(new ItemStack(Material.DIAMOND_SWORD)),
 *     HologramLine.text("&8Right click to browse"));
 * }</pre>
 *
 * @see NpcHologram#content(java.util.List)
 * @since 1.0.0
 */
public final class HologramLine {

  private final Type type;
  private final String text;
  private final ItemStack item;
  private final TextAnimation animation;

  private HologramLine(
    @NotNull Type type,
    @Nullable String text,
    @Nullable ItemStack item,
    @Nullable TextAnimation animation
  ) {
    this.type = type;
    this.text = text;
    this.item = item;
    this.animation = animation;
  }

  /**
   * Creates a line of static text.
   *
   * <p>Colour codes may use either the section sign or an ampersand.
   *
   * @param text the text to show.
   * @return the line.
   * @throws NullPointerException if {@code text} is {@code null}.
   * @since 1.0.0
   */
  @NotNull
  public static HologramLine text(@NotNull String text) {
    Objects.requireNonNull(text, "text");
    return new HologramLine(Type.TEXT, text, null, null);
  }

  /**
   * Creates a line whose text loops through an animation.
   *
   * <p>Effigy drives the animation itself, on a task that only runs while at least one animated line
   * exists and that stops again once the last one is gone.
   *
   * @param animation the animation to play.
   * @return the line.
   * @throws NullPointerException if {@code animation} is {@code null}.
   * @since 1.0.0
   */
  @NotNull
  public static HologramLine animated(@NotNull TextAnimation animation) {
    Objects.requireNonNull(animation, "animation");
    return new HologramLine(Type.ANIMATED_TEXT, null, null, animation);
  }

  /**
   * Creates a line showing a slowly rotating item.
   *
   * <p>The item is a client-side dropped item with gravity disabled, so it hovers in place and spins
   * the way a dropped item does. It cannot be picked up: the server has no idea it exists. Before
   * 1.10 entities cannot switch gravity off, so there the item rides an invisible armour stand
   * instead, which looks the same.
   *
   * <p>The stack is copied, so later changes to it are not picked up. An item line is taller than a
   * text line; see {@link NpcHologram#itemLineHeight()}.
   *
   * @param item the item to display.
   * @return the line.
   * @throws NullPointerException if {@code item} is {@code null}.
   * @since 1.0.0
   */
  @NotNull
  public static HologramLine item(@NotNull ItemStack item) {
    Objects.requireNonNull(item, "item");
    return new HologramLine(Type.ITEM, null, item.clone(), null);
  }

  /**
   * Returns what kind of line this is.
   *
   * @return the line type.
   * @since 1.0.0
   */
  @NotNull
  public Type type() {
    return this.type;
  }

  /**
   * Returns the static text of this line.
   *
   * @return the text, or {@code null} unless {@link #type()} is {@link Type#TEXT}.
   * @since 1.0.0
   */
  @Nullable
  public String text() {
    return this.text;
  }

  /**
   * Returns the item of this line.
   *
   * @return a copy of the item, or {@code null} unless {@link #type()} is {@link Type#ITEM}.
   * @since 1.0.0
   */
  @Nullable
  public ItemStack item() {
    return this.item == null ? null : this.item.clone();
  }

  /**
   * Returns the animation of this line.
   *
   * @return the animation, or {@code null} unless {@link #type()} is {@link Type#ANIMATED_TEXT}.
   * @since 1.0.0
   */
  @Nullable
  public TextAnimation animation() {
    return this.animation;
  }

  /**
   * Returns the text this line shows at a point in the animation loop.
   *
   * <p>For a static text line the frame index is ignored. For an item line there is no text at all.
   *
   * @param frame how many intervals have elapsed since the hologram started animating.
   * @return the text to render, or {@code null} for an item line.
   * @since 1.0.0
   */
  @Nullable
  public String textAt(int frame) {
    switch (this.type) {
      case TEXT:
        return this.text;
      case ANIMATED_TEXT:
        return this.animation == null ? null : this.animation.frame(frame);
      default:
        return null;
    }
  }

  @Override
  public boolean equals(@Nullable Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof HologramLine)) {
      return false;
    }
    HologramLine that = (HologramLine) other;
    return this.type == that.type
      && Objects.equals(this.text, that.text)
      && Objects.equals(this.item, that.item)
      && Objects.equals(this.animation, that.animation);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.type, this.text, this.item, this.animation);
  }

  @Override
  public String toString() {
    switch (this.type) {
      case TEXT:
        return "HologramLine.text(" + this.text + ')';
      case ANIMATED_TEXT:
        return "HologramLine.animated(" + this.animation + ')';
      default:
        return "HologramLine.item(" + this.item + ')';
    }
  }

  /**
   * The kinds of line a hologram can hold.
   *
   * @since 1.0.0
   */
  public enum Type {

    /** A fixed piece of text. */
    TEXT,
    /** A piece of text that loops through a {@link TextAnimation}. */
    ANIMATED_TEXT,
    /** A floating, rotating item. */
    ITEM
  }
}
