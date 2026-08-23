/*
 * This file is part of Effigy, licensed under the MIT License.
 * Copyright (c) 2026 ricodevvv and contributors.
 */
package dev.ricodev.effigy.settings;

/**
 * How an NPC should appear in the player list a client shows on the tab key.
 *
 * <p>Clients only render the skin of an entity whose profile they have received through a player
 * info packet, so every NPC gets a tab list entry for at least a moment regardless of the value
 * chosen here. What differs is how long it stays and whether the client is told to display it.
 *
 * @since 1.0.0
 */
public enum TabListVisibility {

  /**
   * The NPC never shows up in the player list.
   *
   * <p>On 1.19.3 and newer this is a single flag on the player info packet, so the entry is present
   * but not listed and the skin stays intact forever. On older clients the entry has to be removed
   * again shortly after the spawn, which is what
   * {@link NpcSettings#tabListRemovalDelay()} controls.
   *
   * <p>This is the default, and it also stops NPC names from polluting chat autocompletion.
   */
  HIDDEN,

  /**
   * The NPC stays in the player list like a real player.
   *
   * <p>Keep in mind that this raises the player count shown by the client and lets anyone tab
   * complete the NPC name into a command.
   */
  VISIBLE
}
