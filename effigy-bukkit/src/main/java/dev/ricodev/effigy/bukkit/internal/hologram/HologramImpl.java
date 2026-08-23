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
import dev.ricodev.effigy.hologram.HologramRenderer;
import dev.ricodev.effigy.hologram.NpcHologram;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

/**
 * A stack of invisible marker armour stands that follows an NPC around.
 *
 * <p>The tricky part of a per viewer hologram is not drawing it but keeping track of what each
 * client currently has. Two viewers can see a different number of lines at the same time, so this
 * class remembers, per viewer, how many entities it has actually spawned. A refresh then does the
 * minimum: it updates the text of the lines that already exist, spawns the ones that are new, and
 * destroys the ones that are no longer needed. Rebuilding everything instead would make the text
 * flicker on every refresh.
 *
 * <p>Entity ids are allocated once per line index and reused for the lifetime of the NPC, so a
 * hologram whose text changes every second does not slowly exhaust the id space.
 *
 * <p>Not part of the public API.
 *
 * @since 1.0.0
 */
public final class HologramImpl implements NpcHologram {

  /** Where the bottom line sits above the feet of the NPC, just clear of a player's head. */
  private static final double DEFAULT_OFFSET_Y = 2.15;
  /** The vertical size of a rendered name tag, so that stacked lines sit flush. */
  private static final double DEFAULT_LINE_SPACING = 0.28;
  /**
   * How far above a marker armour stand its name tag is drawn. The entity itself has no size, so the
   * text appears at this distance above the position the spawn packet carries.
   */
  private static final double NAME_TAG_OFFSET = 0.5;

  private final Npc npc;
  private final PacketBridge packets;
  private final Runnable stateCheck;

  /** Entity ids by line index, grown on demand and never handed back. */
  private final List<Integer> entityIds = new ArrayList<>();
  /** How many lines each viewer currently has spawned. */
  private final Map<Player, Integer> spawnedLines = new ConcurrentHashMap<>();

  private volatile List<String> lines = Collections.emptyList();
  private volatile HologramRenderer renderer;
  private volatile double offsetY = DEFAULT_OFFSET_Y;
  private volatile double lineSpacing = DEFAULT_LINE_SPACING;

  /**
   * Creates the hologram of an NPC.
   *
   * @param npc        the owning NPC.
   * @param packets    the packet bridge to send through.
   * @param stateCheck a callback that throws if the NPC may not be modified right now.
   * @since 1.0.0
   */
  public HologramImpl(@NotNull Npc npc, @NotNull PacketBridge packets, @NotNull Runnable stateCheck) {
    this.npc = Objects.requireNonNull(npc, "npc");
    this.packets = Objects.requireNonNull(packets, "packets");
    this.stateCheck = Objects.requireNonNull(stateCheck, "stateCheck");
  }

  /**
   * Computes where the name tag of one line has to be drawn.
   *
   * <p>Lines are numbered from the top, but stacked from the bottom, so the last line ends up at the
   * offset and every earlier one a spacing higher.
   *
   * @param baseY       the y coordinate of the feet of the NPC.
   * @param offsetY     how far above the feet the bottom line sits.
   * @param lineSpacing the gap between two lines.
   * @param index       the index of the line, zero being the topmost.
   * @param lineCount   how many lines the hologram has in total.
   * @return the y coordinate the text of that line should appear at.
   * @since 1.0.0
   */
  public static double lineY(double baseY, double offsetY, double lineSpacing, int index, int lineCount) {
    return baseY + offsetY + (lineCount - 1 - index) * lineSpacing;
  }

  @NotNull
  @Override
  public Npc npc() {
    return this.npc;
  }

  @NotNull
  @Override
  @Unmodifiable
  public List<String> lines() {
    return this.lines;
  }

  @Override
  public void lines(@NotNull List<String> lines) {
    Objects.requireNonNull(lines, "lines");
    this.stateCheck.run();

    List<String> copy = new ArrayList<>(lines.size());
    for (String line : lines) {
      copy.add(Objects.requireNonNull(line, "line"));
    }
    this.lines = Collections.unmodifiableList(copy);
    this.refresh();
  }

  @Override
  public void lines(@NotNull String... lines) {
    Objects.requireNonNull(lines, "lines");
    this.lines(Arrays.asList(lines));
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
  public boolean isEmpty() {
    return this.renderer == null && this.lines.isEmpty();
  }

  @Override
  public void refresh() {
    this.stateCheck.run();
    for (Player viewer : this.npc.viewers()) {
      if (viewer.isOnline()) {
        this.render(viewer);
      }
    }
  }

  @Override
  public void refresh(@NotNull Player viewer) {
    Objects.requireNonNull(viewer, "viewer");
    this.stateCheck.run();
    if (this.npc.isViewer(viewer)) {
      this.render(viewer);
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
  }

  /**
   * Destroys the hologram on a viewer that is losing sight of the NPC.
   *
   * @param viewer the leaving viewer.
   * @since 1.0.0
   */
  public void onViewerRemoved(@NotNull Player viewer) {
    Integer spawned = this.spawnedLines.remove(viewer);
    if (spawned != null && spawned > 0 && viewer.isOnline()) {
      this.packets.sendDespawn(viewer, this.idsUpTo(spawned));
    }
  }

  /**
   * Forgets a viewer without sending anything, for a player whose connection is already gone.
   *
   * @param viewer the player to forget.
   * @since 1.0.0
   */
  public void forgetViewer(@NotNull Player viewer) {
    this.spawnedLines.remove(viewer);
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
   * Renders the current state of the hologram for one viewer, sending only what changed.
   *
   * @param viewer the viewer to update.
   * @since 1.0.0
   */
  private void render(@NotNull Player viewer) {
    List<String> content = this.contentFor(viewer);
    int wanted = content.size();
    int spawned = this.spawnedLines.getOrDefault(viewer, 0);
    Location base = this.npc.location();

    // Destroy the surplus first: the lines that stay are about to be repositioned anyway.
    if (spawned > wanted) {
      int[] surplus = new int[spawned - wanted];
      for (int index = wanted; index < spawned; index++) {
        surplus[index - wanted] = this.entityId(index);
      }
      this.packets.sendDespawn(viewer, surplus);
    }

    for (int index = 0; index < wanted; index++) {
      int entityId = this.entityId(index);
      Location position = this.positionOf(base, index, wanted);
      if (index >= spawned) {
        this.packets.sendHologramLineSpawn(viewer, entityId, position);
      } else {
        this.packets.sendHologramLineMove(viewer, entityId, position);
      }

      List<EntityData<?>> metadata = this.packets.buildHologramLineMetadata(content.get(index));
      this.packets.sendMetadata(viewer, entityId, metadata);
    }

    if (wanted == 0) {
      this.spawnedLines.remove(viewer);
    } else {
      this.spawnedLines.put(viewer, wanted);
    }
  }

  /**
   * Repositions the lines every viewer already has, without touching their text.
   *
   * @since 1.0.0
   */
  private void moveForAll() {
    Location base = this.npc.location();
    for (Map.Entry<Player, Integer> entry : this.spawnedLines.entrySet()) {
      Player viewer = entry.getKey();
      if (!viewer.isOnline()) {
        continue;
      }
      int count = entry.getValue();
      for (int index = 0; index < count; index++) {
        this.packets.sendHologramLineMove(
          viewer, this.entityId(index), this.positionOf(base, index, count));
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
  private List<String> contentFor(@NotNull Player viewer) {
    HologramRenderer current = this.renderer;
    if (current == null) {
      return this.lines;
    }

    List<String> rendered = current.render(this.npc, viewer);
    return rendered == null ? Collections.emptyList() : rendered;
  }

  /**
   * Computes the spawn position of one line.
   *
   * @param base      the location of the NPC.
   * @param index     the index of the line, zero being the topmost.
   * @param lineCount how many lines the hologram has for this viewer.
   * @return the location the armour stand of that line has to sit at.
   * @since 1.0.0
   */
  @NotNull
  private Location positionOf(@NotNull Location base, int index, int lineCount) {
    double textY = lineY(base.getY(), this.offsetY, this.lineSpacing, index, lineCount);
    Location position = base.clone();
    // The spawn packet carries the position of the entity, the text floats above it.
    position.setY(textY - NAME_TAG_OFFSET);
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
}
