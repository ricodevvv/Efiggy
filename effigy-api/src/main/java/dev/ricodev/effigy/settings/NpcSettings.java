/*
 * This file is part of Effigy, licensed under the MIT License.
 * Copyright (c) 2026 ricodevvv and contributors.
 */
package dev.ricodev.effigy.settings;

import dev.ricodev.effigy.Npc;
import dev.ricodev.effigy.protocol.SkinLayer;
import java.time.Duration;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

/**
 * The knobs that control how an NPC is tracked, displayed and allowed to be interacted with.
 *
 * <p>Settings are immutable. Build them with {@link #builder()}, derive a variation with
 * {@link #toBuilder()}, and apply them either at construction time through
 * {@link dev.ricodev.effigy.NpcBuilder#settings(NpcSettings)} or later through
 * {@link Npc#settings(NpcSettings)}.
 *
 * <p>The defaults are chosen to be safe on a busy server: a 48 block view distance, no tab list
 * entry, no imitation behaviour and a 250 millisecond interaction cooldown.
 *
 * @since 1.0.0
 */
public final class NpcSettings {

  private static final NpcSettings DEFAULTS = builder().build();

  private final VisibilityRule visibilityRule;
  private final int viewDistance;
  private final TabListVisibility tabListVisibility;
  private final int tabListRemovalDelay;
  private final boolean lookAtViewer;
  private final boolean imitateSneak;
  private final boolean imitateSwing;
  private final Duration interactionCooldown;
  private final Set<SkinLayer> skinLayers;

  private NpcSettings(@NotNull Builder builder) {
    this.visibilityRule = builder.visibilityRule;
    this.viewDistance = builder.viewDistance;
    this.tabListVisibility = builder.tabListVisibility;
    this.tabListRemovalDelay = builder.tabListRemovalDelay;
    this.lookAtViewer = builder.lookAtViewer;
    this.imitateSneak = builder.imitateSneak;
    this.imitateSwing = builder.imitateSwing;
    this.interactionCooldown = builder.interactionCooldown;
    this.skinLayers = builder.skinLayers.isEmpty()
      ? Collections.emptySet()
      : Collections.unmodifiableSet(EnumSet.copyOf(builder.skinLayers));
  }

  /**
   * Returns the shared instance holding the default settings.
   *
   * @return the default settings.
   * @since 1.0.0
   */
  @NotNull
  public static NpcSettings defaults() {
    return DEFAULTS;
  }

  /**
   * Creates a builder pre-filled with the default settings.
   *
   * @return a new settings builder.
   * @since 1.0.0
   */
  @NotNull
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Creates a builder pre-filled with the values of these settings.
   *
   * @return a new settings builder.
   * @since 1.0.0
   */
  @NotNull
  public Builder toBuilder() {
    return new Builder(this);
  }

  /**
   * Returns the rule deciding which players may see the NPC.
   *
   * @return the visibility rule, {@link VisibilityRule#all()} by default.
   * @since 1.0.0
   */
  @NotNull
  public VisibilityRule visibilityRule() {
    return this.visibilityRule;
  }

  /**
   * Returns the radius in blocks within which the NPC is shown.
   *
   * @return the view distance in blocks, {@code 48} by default.
   * @since 1.0.0
   */
  public int viewDistance() {
    return this.viewDistance;
  }

  /**
   * Returns the squared view distance, for comparisons against a squared distance.
   *
   * @return {@link #viewDistance()} squared.
   * @since 1.0.0
   */
  public double viewDistanceSquared() {
    return (double) this.viewDistance * this.viewDistance;
  }

  /**
   * Returns how the NPC appears in the tab list.
   *
   * @return the tab list mode, {@link TabListVisibility#HIDDEN} by default.
   * @since 1.0.0
   */
  @NotNull
  public TabListVisibility tabListVisibility() {
    return this.tabListVisibility;
  }

  /**
   * Returns how many ticks the tab list entry lingers before it is removed again.
   *
   * <p>Only relevant on servers older than 1.19.3 and when the tab list is
   * {@link TabListVisibility#HIDDEN}: those clients drop the skin of an entity whose tab list entry
   * disappears too early, so the entry has to survive a moment past the spawn packet.
   *
   * @return the removal delay in ticks, {@code 30} by default.
   * @since 1.0.0
   */
  public int tabListRemovalDelay() {
    return this.tabListRemovalDelay;
  }

  /**
   * Returns whether the NPC turns its head towards each viewer.
   *
   * <p>Every viewer sees the NPC looking at them personally, because the rotation is sent per
   * player. Costs two small packets per viewer per tracking cycle.
   *
   * @return {@code true} if the NPC follows its viewers with its head.
   * @since 1.0.0
   */
  public boolean lookAtViewer() {
    return this.lookAtViewer;
  }

  /**
   * Returns whether the NPC sneaks when its viewer sneaks.
   *
   * @return {@code true} if sneaking is imitated.
   * @since 1.0.0
   */
  public boolean imitateSneak() {
    return this.imitateSneak;
  }

  /**
   * Returns whether the NPC swings its arm when its viewer swings.
   *
   * @return {@code true} if arm swings are imitated.
   * @since 1.0.0
   */
  public boolean imitateSwing() {
    return this.imitateSwing;
  }

  /**
   * Returns how long a player must wait between two interactions with the NPC.
   *
   * <p>Vanilla clients send a burst of packets for a single right click, and a modified client can
   * send thousands per second. The cooldown is applied before the interact event is fired, so
   * listeners never see the duplicates.
   *
   * @return the per-player interaction cooldown, 250 milliseconds by default.
   * @since 1.0.0
   */
  @NotNull
  public Duration interactionCooldown() {
    return this.interactionCooldown;
  }

  /**
   * Returns the skin overlay parts the NPC shows.
   *
   * @return an immutable set of visible layers, {@link SkinLayer#all()} by default.
   * @since 1.0.0
   */
  @NotNull
  @Unmodifiable
  public Set<SkinLayer> skinLayers() {
    return this.skinLayers;
  }

  @Override
  public String toString() {
    return "NpcSettings(viewDistance=" + this.viewDistance
      + ", tabList=" + this.tabListVisibility
      + ", lookAtViewer=" + this.lookAtViewer + ')';
  }

  /**
   * Mutable builder for {@link NpcSettings}.
   *
   * <p>Not thread-safe. Every setter validates eagerly, so a misconfigured value fails at the call
   * site instead of much later inside the tracking task.
   *
   * @since 1.0.0
   */
  public static final class Builder {

    private VisibilityRule visibilityRule = VisibilityRule.all();
    private int viewDistance = 48;
    private TabListVisibility tabListVisibility = TabListVisibility.HIDDEN;
    private int tabListRemovalDelay = 30;
    private boolean lookAtViewer;
    private boolean imitateSneak;
    private boolean imitateSwing;
    private Duration interactionCooldown = Duration.ofMillis(250);
    private Set<SkinLayer> skinLayers = SkinLayer.all();

    private Builder() {
    }

    private Builder(@NotNull NpcSettings settings) {
      this.visibilityRule = settings.visibilityRule;
      this.viewDistance = settings.viewDistance;
      this.tabListVisibility = settings.tabListVisibility;
      this.tabListRemovalDelay = settings.tabListRemovalDelay;
      this.lookAtViewer = settings.lookAtViewer;
      this.imitateSneak = settings.imitateSneak;
      this.imitateSwing = settings.imitateSwing;
      this.interactionCooldown = settings.interactionCooldown;
      this.skinLayers = settings.skinLayers;
    }

    /**
     * Sets the rule deciding which players may see the NPC.
     *
     * @param visibilityRule the rule to use.
     * @return this builder.
     * @throws NullPointerException if {@code visibilityRule} is {@code null}.
     * @since 1.0.0
     */
    @NotNull
    @Contract("_ -> this")
    public Builder visibilityRule(@NotNull VisibilityRule visibilityRule) {
      this.visibilityRule = Objects.requireNonNull(visibilityRule, "visibilityRule");
      return this;
    }

    /**
     * Sets the radius in blocks within which the NPC is shown.
     *
     * <p>Keep this below the view distance of the server: a client that unloads the chunk of the NPC
     * silently drops the entity, and the library would keep believing it is still spawned.
     *
     * @param viewDistance the view distance in blocks.
     * @return this builder.
     * @throws IllegalArgumentException if {@code viewDistance} is not positive.
     * @since 1.0.0
     */
    @NotNull
    @Contract("_ -> this")
    public Builder viewDistance(int viewDistance) {
      if (viewDistance <= 0) {
        throw new IllegalArgumentException("viewDistance must be positive, got " + viewDistance);
      }
      this.viewDistance = viewDistance;
      return this;
    }

    /**
     * Sets how the NPC appears in the tab list.
     *
     * @param tabListVisibility the tab list mode to use.
     * @return this builder.
     * @throws NullPointerException if {@code tabListVisibility} is {@code null}.
     * @since 1.0.0
     */
    @NotNull
    @Contract("_ -> this")
    public Builder tabListVisibility(@NotNull TabListVisibility tabListVisibility) {
      this.tabListVisibility = Objects.requireNonNull(tabListVisibility, "tabListVisibility");
      return this;
    }

    /**
     * Sets how many ticks the tab list entry lingers on legacy servers before it is removed.
     *
     * @param ticks the delay in ticks; {@code 0} removes the entry in the same tick.
     * @return this builder.
     * @throws IllegalArgumentException if {@code ticks} is negative.
     * @since 1.0.0
     */
    @NotNull
    @Contract("_ -> this")
    public Builder tabListRemovalDelay(int ticks) {
      if (ticks < 0) {
        throw new IllegalArgumentException("tabListRemovalDelay must not be negative, got " + ticks);
      }
      this.tabListRemovalDelay = ticks;
      return this;
    }

    /**
     * Sets whether the NPC turns its head towards each viewer.
     *
     * @param lookAtViewer {@code true} to follow viewers with the head.
     * @return this builder.
     * @since 1.0.0
     */
    @NotNull
    @Contract("_ -> this")
    public Builder lookAtViewer(boolean lookAtViewer) {
      this.lookAtViewer = lookAtViewer;
      return this;
    }

    /**
     * Sets whether the NPC sneaks when its viewer sneaks.
     *
     * @param imitateSneak {@code true} to imitate sneaking.
     * @return this builder.
     * @since 1.0.0
     */
    @NotNull
    @Contract("_ -> this")
    public Builder imitateSneak(boolean imitateSneak) {
      this.imitateSneak = imitateSneak;
      return this;
    }

    /**
     * Sets whether the NPC swings its arm when its viewer swings.
     *
     * @param imitateSwing {@code true} to imitate arm swings.
     * @return this builder.
     * @since 1.0.0
     */
    @NotNull
    @Contract("_ -> this")
    public Builder imitateSwing(boolean imitateSwing) {
      this.imitateSwing = imitateSwing;
      return this;
    }

    /**
     * Sets how long a player must wait between two interactions with the NPC.
     *
     * @param cooldown the cooldown to apply; {@link Duration#ZERO} disables it.
     * @return this builder.
     * @throws NullPointerException     if {@code cooldown} is {@code null}.
     * @throws IllegalArgumentException if {@code cooldown} is negative.
     * @since 1.0.0
     */
    @NotNull
    @Contract("_ -> this")
    public Builder interactionCooldown(@NotNull Duration cooldown) {
      Objects.requireNonNull(cooldown, "cooldown");
      if (cooldown.isNegative()) {
        throw new IllegalArgumentException("interactionCooldown must not be negative, got " + cooldown);
      }
      this.interactionCooldown = cooldown;
      return this;
    }

    /**
     * Sets the skin overlay parts the NPC shows.
     *
     * @param layers the layers to show; copied defensively.
     * @return this builder.
     * @throws NullPointerException if {@code layers} is {@code null} or contains {@code null}.
     * @since 1.0.0
     */
    @NotNull
    @Contract("_ -> this")
    public Builder skinLayers(@NotNull Set<SkinLayer> layers) {
      Objects.requireNonNull(layers, "layers");
      EnumSet<SkinLayer> copy = EnumSet.noneOf(SkinLayer.class);
      for (SkinLayer layer : layers) {
        copy.add(Objects.requireNonNull(layer, "layer"));
      }
      this.skinLayers = copy;
      return this;
    }

    /**
     * Builds the settings.
     *
     * @return the immutable settings described by this builder.
     * @since 1.0.0
     */
    @NotNull
    public NpcSettings build() {
      return new NpcSettings(this);
    }
  }
}
