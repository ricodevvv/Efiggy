/*
 * This file is part of Effigy, licensed under the MIT License.
 * Copyright (c) 2026 ricodevvv and contributors.
 */
package dev.ricodev.effigy.event;

import dev.ricodev.effigy.Npc;
import java.util.Objects;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;

/**
 * Base class of every event this library fires.
 *
 * <p>Effigy deliberately uses the Bukkit event bus rather than a bus of its own, so listeners are
 * registered with {@code @EventHandler} exactly like any other plugin event and benefit from the
 * priorities, the {@code ignoreCancelled} flag and the timings support the server already provides.
 *
 * <p>All events are fired synchronously on the server main thread, including the ones that originate
 * from a packet received on a netty thread, so listeners may use the whole Bukkit API.
 *
 * @since 1.0.0
 */
public abstract class NpcEvent extends Event {

  private final Npc npc;

  /**
   * Creates a new event for the given NPC.
   *
   * @param npc the NPC the event is about.
   * @throws NullPointerException if {@code npc} is {@code null}.
   * @since 1.0.0
   */
  protected NpcEvent(@NotNull Npc npc) {
    super(false);
    this.npc = Objects.requireNonNull(npc, "npc");
  }

  /**
   * Returns the NPC this event is about.
   *
   * @return the involved NPC.
   * @since 1.0.0
   */
  @NotNull
  public Npc npc() {
    return this.npc;
  }
}
