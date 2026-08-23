/*
 * This file is part of Effigy, licensed under the MIT License.
 * Copyright (c) 2026 ricodevvv and contributors.
 */
package dev.ricodev.effigy.bukkit.internal.util;

import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

/**
 * Argument and state checks shared by the implementation.
 *
 * <p>Not part of the public API.
 *
 * @since 1.0.0
 */
public final class Preconditions {

  private Preconditions() {
    throw new AssertionError("Preconditions is a utility class");
  }

  /**
   * Ensures that the calling thread is the server main thread.
   *
   * <p>Called at the top of every mutating operation. Failing loudly here is worth the two
   * nanoseconds it costs: a packet sent from an async task interleaved with the spawn packets of the
   * tracking task produces ghost entities that are extremely hard to reproduce.
   *
   * @param operation a short description of what was attempted, used in the error message.
   * @throws IllegalStateException if the current thread is not the main thread.
   * @since 1.0.0
   */
  public static void mainThread(@NotNull String operation) {
    if (!Bukkit.isPrimaryThread()) {
      throw new IllegalStateException(
        operation + " must be called from the server main thread, but was called from "
          + Thread.currentThread().getName());
    }
  }

  /**
   * Ensures that a condition holds.
   *
   * @param condition the condition to verify.
   * @param message   the message of the exception thrown when it does not hold.
   * @throws IllegalArgumentException if {@code condition} is {@code false}.
   * @since 1.0.0
   */
  public static void argument(boolean condition, @NotNull String message) {
    if (!condition) {
      throw new IllegalArgumentException(message);
    }
  }

  /**
   * Ensures that a state condition holds.
   *
   * @param condition the condition to verify.
   * @param message   the message of the exception thrown when it does not hold.
   * @throws IllegalStateException if {@code condition} is {@code false}.
   * @since 1.0.0
   */
  public static void state(boolean condition, @NotNull String message) {
    if (!condition) {
      throw new IllegalStateException(message);
    }
  }
}
