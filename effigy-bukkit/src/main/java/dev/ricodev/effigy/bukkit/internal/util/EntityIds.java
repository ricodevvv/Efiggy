/*
 * This file is part of Effigy, licensed under the MIT License.
 * Copyright (c) 2026 ricodevvv and contributors.
 */
package dev.ricodev.effigy.bukkit.internal.util;

import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Allocates entity ids that no real entity of the server will ever use.
 *
 * <p>The preferred source is the counter the server itself uses, reached through the packet library,
 * because an id taken from it can never collide. When that counter is unreachable, for example
 * because a server fork moved it, the fallback counts down from a very high value: ids are handed
 * out in ascending order by the server, so a descending sequence near the top of the range stays out
 * of its way for the lifetime of any realistic server session.
 *
 * <p>Not part of the public API.
 *
 * @since 1.0.0
 */
public final class EntityIds {

  private static final AtomicInteger FALLBACK = new AtomicInteger(Integer.MAX_VALUE - 1);
  private static volatile boolean serverCounterUsable = true;

  private EntityIds() {
    throw new AssertionError("EntityIds is a utility class");
  }

  /**
   * Allocates the next free entity id.
   *
   * @return an entity id that is not in use by any server side entity.
   * @since 1.0.0
   */
  public static int next() {
    if (serverCounterUsable) {
      try {
        return SpigotReflectionUtil.generateEntityId();
      } catch (Throwable throwable) {
        // Remember the failure so that every later call goes straight to the fallback.
        serverCounterUsable = false;
      }
    }
    return FALLBACK.getAndDecrement();
  }

  /**
   * Returns whether ids currently come from the server counter or from the fallback.
   *
   * @return {@code true} while the server counter is being used.
   * @since 1.0.0
   */
  public static boolean usingServerCounter() {
    return serverCounterUsable;
  }
}
