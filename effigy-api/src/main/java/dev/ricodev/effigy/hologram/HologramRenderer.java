/*
 * This file is part of Effigy, licensed under the MIT License.
 * Copyright (c) 2026 ricodevvv and contributors.
 */
package dev.ricodev.effigy.hologram;

import dev.ricodev.effigy.Npc;
import java.util.List;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Produces the hologram text of an NPC for one specific viewer.
 *
 * <p>A renderer is what makes a hologram say something different to every player: a name, a rank, a
 * price in the currency of that player, a quest step. Without one a hologram shows the same static
 * lines to everybody.
 *
 * <pre>{@code
 * npc.hologram().renderer((npc, viewer) -> List.of(
 *     HologramLine.text("&e&lVillage Shop"),
 *     HologramLine.text("&7Balance: &a" + economy.balance(viewer)),
 *     HologramLine.item(shop.featuredItemFor(viewer))));
 * }</pre>
 *
 * <p><strong>Threading and cost.</strong> The renderer runs on the main thread, once per viewer
 * whenever the hologram is refreshed. Keep it to reading already computed state; a database query
 * here would stall the server for as many viewers as the NPC has.
 *
 * <p>Effigy never calls a renderer on a timer. Call {@link NpcHologram#refresh()} when the
 * underlying data changed.
 *
 * @since 1.0.0
 */
@FunctionalInterface
public interface HologramRenderer {

  /**
   * Produces the lines shown to one viewer.
   *
   * <p>The first element is rendered at the top. Every kind of {@link HologramLine} is allowed,
   * including animated ones, which lets the animation itself differ per viewer.
   *
   * @param npc    the NPC the hologram belongs to.
   * @param viewer the player the lines are rendered for.
   * @return the lines to display; an empty list hides the hologram from that viewer.
   * @since 1.0.0
   */
  @NotNull
  List<HologramLine> render(@NotNull Npc npc, @NotNull Player viewer);
}
