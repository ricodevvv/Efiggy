/*
 * This file is part of Effigy, licensed under the MIT License.
 * Copyright (c) 2026 ricodevvv and contributors.
 */

/**
 * Core API of the Effigy NPC library.
 *
 * <p>{@link dev.ricodev.effigy.Effigy} is the entry point: it owns a
 * {@link dev.ricodev.effigy.NpcRegistry}, hands out {@link dev.ricodev.effigy.NpcBuilder} instances
 * and shuts everything down again when the plugin is disabled. The implementation lives in the
 * {@code effigy-bukkit} module, which is the only place that touches the protocol.
 *
 * @since 1.0.0
 */
package dev.ricodev.effigy;
