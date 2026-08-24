/*
 * This file is part of Effigy, licensed under the MIT License.
 * Copyright (c) 2026 ricodevvv and contributors.
 */
package dev.ricodev.effigy.bukkit.internal.hologram;

import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import dev.ricodev.effigy.Npc;
import dev.ricodev.effigy.bukkit.internal.protocol.PacketBridge;
import dev.ricodev.effigy.bukkit.internal.util.EntityIds;
import dev.ricodev.effigy.bukkit.internal.util.Preconditions;
import dev.ricodev.effigy.hologram.HologramLine;
import dev.ricodev.effigy.hologram.HologramRenderer;
import dev.ricodev.effigy.hologram.NpcHologram;
import dev.ricodev.effigy.hologram.TextAnimation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

/**
 * A stack of client-side entities that follows an NPC around.
 *
 * <p>The awkward part of a per viewer hologram is not drawing it but knowing what each client
 * currently has, because two viewers can be looking at a different number of lines, of different
 * kinds, at the same moment. Every viewer therefore gets a {@link ViewerState} recording exactly
 * what was sent to it. A refresh then does the minimum: text already on screen is updated in place,
 * new lines are spawned, surplus ones destroyed, and a line whose kind changed is despawned and
 * respawned. Rebuilding everything instead would make the hologram flicker on every refresh, which
 * with an animation running would mean flickering several times a second.
 *
 * <p>Entity ids are allocated once per line index and reused for the lifetime of the NPC. The same
 * id may be an armour stand on one client and a dropped item on another, which is harmless: entity
 * ids only ever mean anything to the client they were sent to.
 *
 * <p>Not part of the public API.
 *
 * @since 1.0.0
 */
public final class HologramImpl implements NpcHologram {

  /** Where the bottom line sits above the feet of the NPC, just clear of a player's head. */
  private static final double DEFAULT_OFFSET_Y = 2.15;
  /** The vertical size of a rendered name tag, so that stacked text lines sit flush. */
  private static final double DEFAULT_LINE_SPACING = 0.28;
  /** The vertical size of a floating item, with a small gap on either side of it. */
  private static final double DEFAULT_ITEM_LINE_HEIGHT = 0.6;
  /**
   * How far above a marker armour stand its name tag is drawn. The entity itself has no size, so
   * the text appears at this distance above the position the spawn packet carries.
   */
  private static final double NAME_TAG_OFFSET = 0.5;

  private final Npc npc;
  private final PacketBridge packets;
  private final Runnable stateCheck;
  private final AnimationHub animations;

  /** Entity ids by line index, grown on demand and never handed back. */
  private final List<Integer> entityIds = new ArrayList<>();
  /** What each viewer currently has on screen. */
  private final Map<Player, ViewerState> states = new ConcurrentHashMap<>();
  /** The clock every animation of this hologram is measured against. */
  private final long startedNanos = System.nanoTime();

  private volatile List<HologramLine> content = Collections.emptyList();
  private volatile HologramRenderer renderer;
  private volatile double offsetY = DEFAULT_OFFSET_Y;
  private volatile double lineSpacing = DEFAULT_LINE_SPACING;
  private volatile double itemLineHeight = DEFAULT_ITEM_LINE_HEIGHT;

  /**
   * Creates the hologram of an NPC.
   *
   * @param npc        the owning NPC.
   * @param packets    the packet bridge to send through.
   * @param stateCheck a callback that throws if the NPC may not be modified right now.
   * @param animations where to report that this hologram wants to be ticked.
   * @since 1.0.0
   */
  public HologramImpl(
    @NotNull Npc npc,
    @NotNull PacketBridge packets,
    @NotNull Runnable stateCheck,
    @NotNull AnimationHub animations
  ) {
    this.npc = Objects.requireNonNull(npc, "npc");
    this.packets = Objects.requireNonNull(packets, "packets");
    this.stateCheck = Objects.requireNonNull(stateCheck, "stateCheck");
    this.animations = Objects.requireNonNull(animations, "animations");
  }

  /**
   * Computes where the anchor of every line has to sit.
   *
   * <p>Lines are numbered from the top but stacked from the bottom: the last one anchors at the
   * offset and each line above it clears the full height of everything below. Because the height
   * depends on the kind of line, a hologram that mixes text and items still stacks without overlap.
   *
   * @param baseY          the y coordinate of the feet of the NPC.
   * @param offsetY        how far above the feet the bottom line sits.
   * @param lineSpacing    the height of a text line.
   * @param itemLineHeight the height of an item line.
   * @param content        the lines, the first one being the topmost.
   * @return the anchor y coordinate of each line, in the same order as the content.
   * @since 1.0.0
   */
  @NotNull
  public static double[] layout(
    double baseY,
    double offsetY,
    double lineSpacing,
    double itemLineHeight,
    @NotNull List<HologramLine> content
  ) {
    double[] positions = new double[content.size()];
    double cursor = baseY + offsetY;
    for (int index = content.size() - 1; index >= 0; index--) {
      positions[index] = cursor;
      cursor += content.get(index).type() == HologramLine.Type.ITEM ? itemLineHeight : lineSpacing;
    }
    return positions;
  }

  @NotNull
  @Override
  public Npc npc() {
    return this.npc;
  }

  @NotNull
  @Override
  @Unmodifiable
  public List<HologramLine> content() {
    return this.content;
  }

  @Override
  public void content(@NotNull List<HologramLine> content) {
    Objects.requireNonNull(content, "content");
    this.stateCheck.run();

    List<HologramLine> copy = new ArrayList<>(content.size());
    for (HologramLine line : content) {
      copy.add(Objects.requireNonNull(line, "line"));
    }
    this.content = Collections.unmodifiableList(copy);
    this.refresh();
  }

  @Override
  public void content(@NotNull HologramLine... content) {
    Objects.requireNonNull(content, "content");
    this.content(Arrays.asList(content));
  }

  @Override
  public void lines(@NotNull String... lines) {
    Objects.requireNonNull(lines, "lines");
    List<HologramLine> converted = new ArrayList<>(lines.length);
    for (String line : lines) {
      converted.add(HologramLine.text(Objects.requireNonNull(line, "line")));
    }
    this.content(converted);
  }

  @Nullable
  @Override
  public HologramRenderer renderer() {
    return this.renderer;
  }

  @Override
  public void renderer(@Nullable HologramRenderer renderer) {
    this.stateCheck.run();
    this.renderer = renderer;
    this.refresh();
  }

  @Override
  public double offsetY() {
    return this.offsetY;
  }

  @Override
  public void offsetY(double offsetY) {
    Preconditions.argument(Double.isFinite(offsetY), "The vertical offset must be finite");
    this.stateCheck.run();
    this.offsetY = offsetY;
    this.moveForAll();
  }

  @Override
  public double lineSpacing() {
    return this.lineSpacing;
  }

  @Override
  public void lineSpacing(double lineSpacing) {
    Preconditions.argument(
      Double.isFinite(lineSpacing) && lineSpacing > 0.0,
      "The line spacing must be a positive, finite number of blocks");
    this.stateCheck.run();
    this.lineSpacing = lineSpacing;
    this.moveForAll();
  }

  @Override
  public double itemLineHeight() {
    return this.itemLineHeight;
  }

  @Override
  public void itemLineHeight(double itemLineHeight) {
    Preconditions.argument(
      Double.isFinite(itemLineHeight) && itemLineHeight > 0.0,
      "The item line height must be a positive, finite number of blocks");
    this.stateCheck.run();
    this.itemLineHeight = itemLineHeight;
    this.moveForAll();
  }

  @Override
  public boolean isEmpty() {
    return this.renderer == null && this.content.isEmpty();
  }

  @Override
  public void refresh() {
    this.stateCheck.run();
    for (Player viewer : this.npc.viewers()) {
      if (viewer.isOnline()) {
        this.render(viewer);
      }
    }
    this.reportAnimationState();
  }

  @Override
  public void refresh(@NotNull Player viewer) {
    Objects.requireNonNull(viewer, "viewer");
    this.stateCheck.run();
    if (this.npc.isViewer(viewer)) {
      this.render(viewer);
      this.reportAnimationState();
    }
  }

  /**
   * Sends the hologram to a viewer that has just been shown the NPC.
   *
   * @param viewer the new viewer.
   * @since 1.0.0
   */
  public void onViewerAdded(@NotNull Player viewer) {
    this.render(viewer);
    this.reportAnimationState();
  }

  /**
   * Destroys the hologram on a viewer that is losing sight of the NPC.
   *
   * @param viewer the leaving viewer.
   * @since 1.0.0
   */
  public void onViewerRemoved(@NotNull Player viewer) {
    ViewerState state = this.states.remove(viewer);
    if (state != null && !state.content.isEmpty() && viewer.isOnline()) {
      this.packets.sendDespawn(viewer, this.idsUpTo(state.content.size()));
    }
    this.reportAnimationState();
  }

  /**
   * Forgets a viewer without sending anything, for a player whose connection is already gone.
   *
   * @param viewer the player to forget.
   * @since 1.0.0
   */
  public void forgetViewer(@NotNull Player viewer) {
    this.states.remove(viewer);
    this.reportAnimationState();
  }

  /**
   * Moves the hologram along with the NPC it belongs to.
   *
   * @since 1.0.0
   */
  public void onNpcMoved() {
    this.moveForAll();
  }

  /**
   * Releases everything held by this hologram when its NPC is removed.
   *
   * @since 1.0.0
   */
  public void onNpcRemoved() {
    this.states.clear();
    this.animations.setAnimating(this, false);
  }

  /**
   * Advances every animated line by whatever the clock says and resends the ones that changed.
   *
   * <p>Runs once per tick while this hologram is registered with the {@link AnimationHub}. It never
   * calls the {@link HologramRenderer}: the content each viewer has is already recorded, so an
   * animation costs one metadata packet per changed line and nothing else.
   *
   * @since 1.0.0
   */
  public void tickAnimations() {
    for (Map.Entry<Player, ViewerState> entry : this.states.entrySet()) {
      Player viewer = entry.getKey();
      if (!viewer.isOnline()) {
        continue;
      }

      ViewerState state = entry.getValue();
      for (int index = 0; index < state.content.size(); index++) {
        HologramLine line = state.content.get(index);
        TextAnimation animation = line.animation();
        if (animation == null) {
          continue;
        }

        int frame = this.frameOf(animation);
        if (frame == state.frames[index]) {
          continue;
        }
        state.frames[index] = frame;
        this.packets.sendMetadata(
          viewer,
          this.entityId(index),
          this.packets.buildHologramLineMetadata(animation.frame(frame)));
      }
    }
  }

  /**
   * Returns which frame of an animation is due right now.
   *
   * @param animation the animation to read the clock for.
   * @return the frame index, already wrapped into the loop.
   * @since 1.0.0
   */
  private int frameOf(@NotNull TextAnimation animation) {
    long elapsed = System.nanoTime() - this.startedNanos;
    long interval = Math.max(1L, animation.interval().toNanos());
    return (int) ((elapsed / interval) % animation.frameCount());
  }

  /**
   * Tells the hub whether this hologram still needs ticking.
   *
   * @since 1.0.0
   */
  private void reportAnimationState() {
    boolean animating = false;
    for (ViewerState state : this.states.values()) {
      for (HologramLine line : state.content) {
        if (line.type() == HologramLine.Type.ANIMATED_TEXT) {
          animating = true;
          break;
        }
      }
      if (animating) {
        break;
      }
    }
    this.animations.setAnimating(this, animating);
  }

  /**
   * Renders the current state of the hologram for one viewer, sending only what changed.
   *
   * @param viewer the viewer to update.
   * @since 1.0.0
   */
  private void render(@NotNull Player viewer) {
    List<HologramLine> wanted = this.contentFor(viewer);
    ViewerState previous = this.states.get(viewer);
    List<HologramLine> current = previous == null ? Collections.emptyList() : previous.content;

    // Destroy the surplus first; the lines that stay are about to be repositioned anyway.
    if (current.size() > wanted.size()) {
      int[] surplus = new int[current.size() - wanted.size()];
      for (int index = wanted.size(); index < current.size(); index++) {
        surplus[index - wanted.size()] = this.entityId(index);
      }
      this.packets.sendDespawn(viewer, surplus);
    }

    if (wanted.isEmpty()) {
      this.states.remove(viewer);
      return;
    }

    Location base = this.npc.location();
    double[] positions = layout(
      base.getY(), this.offsetY, this.lineSpacing, this.itemLineHeight, wanted);
    ViewerState state = new ViewerState(wanted);

    for (int index = 0; index < wanted.size(); index++) {
      HologramLine line = wanted.get(index);
      int entityId = this.entityId(index);
      boolean existed = index < current.size();
      boolean sameKind = existed && current.get(index).type() == line.type();

      if (existed && !sameKind) {
        // A text line cannot become an item line in place; the client has to be given a new entity.
        this.packets.sendDespawn(viewer, entityId);
      }

      Location position = this.positionOf(base, positions[index], line.type());
      if (sameKind) {
        this.packets.sendHologramLineMove(viewer, entityId, position);
      } else if (line.type() == HologramLine.Type.ITEM) {
        this.packets.sendItemLineSpawn(viewer, entityId, position);
      } else {
        this.packets.sendHologramLineSpawn(viewer, entityId, position);
      }

      this.packets.sendMetadata(viewer, entityId, this.metadataOf(line, state, index));
    }
    this.states.put(viewer, state);
  }

  /**
   * Builds the metadata for one line and records the animation frame it was built from.
   *
   * @param line  the line to render.
   * @param state the state being assembled for the viewer.
   * @param index the index of the line.
   * @return the metadata entries to send.
   * @since 1.0.0
   */
  @NotNull
  private List<EntityData<?>> metadataOf(
    @NotNull HologramLine line,
    @NotNull ViewerState state,
    int index
  ) {
    if (line.type() == HologramLine.Type.ITEM) {
      ItemStack item = line.item();
      return this.packets.buildItemLineMetadata(Objects.requireNonNull(item, "item"));
    }

    TextAnimation animation = line.animation();
    if (animation == null) {
      return this.packets.buildHologramLineMetadata(Objects.requireNonNull(line.text(), "text"));
    }

    int frame = this.frameOf(animation);
    state.frames[index] = frame;
    return this.packets.buildHologramLineMetadata(animation.frame(frame));
  }

  /**
   * Repositions the lines every viewer already has, without touching their content.
   *
   * @since 1.0.0
   */
  private void moveForAll() {
    Location base = this.npc.location();
    for (Map.Entry<Player, ViewerState> entry : this.states.entrySet()) {
      Player viewer = entry.getKey();
      if (!viewer.isOnline()) {
        continue;
      }

      List<HologramLine> lines = entry.getValue().content;
      double[] positions = layout(
        base.getY(), this.offsetY, this.lineSpacing, this.itemLineHeight, lines);
      for (int index = 0; index < lines.size(); index++) {
        this.packets.sendHologramLineMove(
          viewer,
          this.entityId(index),
          this.positionOf(base, positions[index], lines.get(index).type()));
      }
    }
  }

  /**
   * Returns the lines a specific viewer should see.
   *
   * @param viewer the viewer to render for.
   * @return the lines, empty if the hologram is unused.
   * @since 1.0.0
   */
  @NotNull
  private List<HologramLine> contentFor(@NotNull Player viewer) {
    HologramRenderer current = this.renderer;
    if (current == null) {
      return this.content;
    }

    List<HologramLine> rendered = current.render(this.npc, viewer);
    if (rendered == null || rendered.isEmpty()) {
      return Collections.emptyList();
    }

    // A renderer is plugin code; a null in the middle of its list must not reach the packet layer.
    List<HologramLine> copy = new ArrayList<>(rendered.size());
    for (HologramLine line : rendered) {
      if (line != null) {
        copy.add(line);
      }
    }
    return copy;
  }

  /**
   * Turns the anchor of a line into the position its entity has to be spawned at.
   *
   * @param base   the location of the NPC.
   * @param anchor the y coordinate the line should appear at.
   * @param type   what kind of line it is.
   * @return the spawn location of the entity.
   * @since 1.0.0
   */
  @NotNull
  private Location positionOf(@NotNull Location base, double anchor, @NotNull HologramLine.Type type) {
    Location position = base.clone();
    // An item is drawn at its own position; a name tag floats above the entity carrying it.
    position.setY(type == HologramLine.Type.ITEM ? anchor : anchor - NAME_TAG_OFFSET);
    position.setYaw(0.0f);
    position.setPitch(0.0f);
    return position;
  }

  /**
   * Returns the entity id of a line, allocating one the first time the index is used.
   *
   * @param index the line index.
   * @return the entity id reserved for that index.
   * @since 1.0.0
   */
  private int entityId(int index) {
    synchronized (this.entityIds) {
      while (this.entityIds.size() <= index) {
        this.entityIds.add(EntityIds.next());
      }
      return this.entityIds.get(index);
    }
  }

  /**
   * Collects the entity ids of the first {@code count} lines.
   *
   * @param count how many ids to return.
   * @return the ids, in line order.
   * @since 1.0.0
   */
  private int[] idsUpTo(int count) {
    int[] ids = new int[count];
    for (int index = 0; index < count; index++) {
      ids[index] = this.entityId(index);
    }
    return ids;
  }

  /**
   * What one viewer currently has on screen, and which animation frame each line was drawn at.
   *
   * @since 1.0.0
   */
  private static final class ViewerState {

    private final List<HologramLine> content;
    private final int[] frames;

    private ViewerState(@NotNull List<HologramLine> content) {
      this.content = content;
      this.frames = new int[content.size()];
      // -1 is not a valid frame, so the first tick after a render always sends an update if the
      // clock moved on in the meantime.
      Arrays.fill(this.frames, -1);
    }
  }
}
