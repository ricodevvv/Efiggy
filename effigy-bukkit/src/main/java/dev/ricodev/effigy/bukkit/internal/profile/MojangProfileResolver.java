/*
 * This file is part of Effigy, licensed under the MIT License.
 * Copyright (c) 2026 ricodevvv and contributors.
 */
package dev.ricodev.effigy.bukkit.internal.profile;

import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ricodev.effigy.profile.NpcProfile;
import dev.ricodev.effigy.profile.ProfileProperty;
import dev.ricodev.effigy.profile.ProfileResolver;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The default {@link ProfileResolver}: asks Mojang, then remembers the answer.
 *
 * <p>The session API is rate limited per address, roughly to one request per profile per minute,
 * and answers a burst with HTTP 429 for several minutes afterwards. Two mechanisms keep a server
 * from ever hitting that wall:
 *
 * <ul>
 *   <li><strong>Caching.</strong> A resolved profile is kept for a configurable time to live,
 *       thirty minutes by default, which is far shorter than how often players change their skin
 *       and far longer than how often a lobby respawns its NPCs.
 *   <li><strong>Request collapsing.</strong> The cache stores the in-flight future rather than the
 *       finished value, so a hundred NPCs asking for the same skin during startup produce exactly
 *       one HTTP request. A failed lookup is evicted immediately so that it is retried later.
 * </ul>
 *
 * <p><strong>Threading.</strong> Every method returns immediately. Requests run on two internal
 * threads and futures complete there, never on the main thread, so callers have to hop back before
 * touching Bukkit. Plain {@link HttpURLConnection} is used rather than the Java 11 HTTP client so
 * the resolver also runs on the Java 8 servers 1.8 is usually paired with.
 *
 * <p>Not part of the public API.
 *
 * @since 1.0.0
 */
public final class MojangProfileResolver implements ProfileResolver, AutoCloseable {

  private static final String NAME_ENDPOINT = "https://api.mojang.com/users/profiles/minecraft/";
  private static final String PROFILE_ENDPOINT =
    "https://sessionserver.mojang.com/session/minecraft/profile/";
  private static final Pattern NAME_PATTERN = Pattern.compile("^\\w{1,16}$");
  private static final Pattern DASHES = Pattern.compile(
    "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})");
  private static final int TIMEOUT_MILLIS = 10_000;
  private static final String USER_AGENT = "Effigy/1.0 (+https://github.com/ricodevvv/Efiggy)";

  private final ExecutorService executor;
  private final PacketEventsAPI<?> packetEvents;
  private final Duration timeToLive;

  private final Map<UUID, CachedProfile> byUniqueId = new ConcurrentHashMap<>();
  private final Map<String, CachedProfile> byName = new ConcurrentHashMap<>();

  /**
   * Creates a resolver with the default time to live of thirty minutes.
   *
   * @param packetEvents the packet library instance, used to read the profile of online players.
   * @throws NullPointerException if {@code packetEvents} is {@code null}.
   * @since 1.0.0
   */
  public MojangProfileResolver(@NotNull PacketEventsAPI<?> packetEvents) {
    this(packetEvents, Duration.ofMinutes(30));
  }

  /**
   * Creates a resolver with a custom time to live.
   *
   * @param packetEvents the packet library instance, used to read the profile of online players.
   * @param timeToLive   how long a resolved profile stays cached.
   * @throws NullPointerException     if any argument is {@code null}.
   * @throws IllegalArgumentException if {@code timeToLive} is negative.
   * @since 1.0.0
   */
  public MojangProfileResolver(@NotNull PacketEventsAPI<?> packetEvents, @NotNull Duration timeToLive) {
    this.packetEvents = Objects.requireNonNull(packetEvents, "packetEvents");
    Objects.requireNonNull(timeToLive, "timeToLive");
    if (timeToLive.isNegative()) {
      throw new IllegalArgumentException("timeToLive must not be negative, got " + timeToLive);
    }
    this.timeToLive = timeToLive;

    ThreadFactory threadFactory = new NamedThreadFactory();
    this.executor = Executors.newFixedThreadPool(2, threadFactory);
  }

  @NotNull
  @Override
  public CompletableFuture<NpcProfile> resolve(@NotNull UUID uniqueId) {
    Objects.requireNonNull(uniqueId, "uniqueId");
    return this.cached(this.byUniqueId, uniqueId, () -> this.requestProfile(uniqueId));
  }

  @NotNull
  @Override
  public CompletableFuture<NpcProfile> resolveByName(@NotNull String name) {
    Objects.requireNonNull(name, "name");
    if (!NAME_PATTERN.matcher(name).matches()) {
      throw new IllegalArgumentException("'" + name + "' cannot be a Minecraft name");
    }

    String key = name.toLowerCase(Locale.ROOT);
    return this.cached(this.byName, key, () -> this.requestUniqueId(name).thenCompose(this::requestProfile));
  }

  @NotNull
  @Override
  public CompletableFuture<NpcProfile> resolve(@NotNull Player player) {
    Objects.requireNonNull(player, "player");

    List<ProfileProperty> properties = new ArrayList<>();
    UserProfile profile = this.packetEvents.getPlayerManager().getUser(player).getProfile();
    if (profile != null) {
      for (TextureProperty property : profile.getTextureProperties()) {
        properties.add(ProfileProperty.of(property.getName(), property.getValue(), property.getSignature()));
      }
    }
    // A random unique id keeps the clone from replacing the real player on the client.
    return CompletableFuture.completedFuture(
      NpcProfile.of(UUID.randomUUID(), player.getName(), properties));
  }

  /**
   * Removes every cached profile.
   *
   * @since 1.0.0
   */
  public void invalidateAll() {
    this.byUniqueId.clear();
    this.byName.clear();
  }

  @Override
  public void close() {
    this.invalidateAll();
    this.executor.shutdownNow();
  }

  /**
   * Returns the cached future for a key, starting a lookup if there is none or it has expired.
   *
   * @param cache  the cache to read.
   * @param key    the key to look up.
   * @param loader supplies the lookup when the cache misses.
   * @param <K>    the key type of the cache.
   * @return the cached or freshly started lookup.
   * @since 1.0.0
   */
  @NotNull
  private <K> CompletableFuture<NpcProfile> cached(
    @NotNull Map<K, CachedProfile> cache,
    @NotNull K key,
    @NotNull java.util.function.Supplier<CompletableFuture<NpcProfile>> loader
  ) {
    CachedProfile existing = cache.get(key);
    if (existing != null && !existing.expired(this.timeToLive)) {
      return existing.future;
    }

    CachedProfile fresh = new CachedProfile(new CompletableFuture<>());
    CachedProfile previous = existing == null
      ? cache.putIfAbsent(key, fresh)
      : (cache.replace(key, existing, fresh) ? null : cache.get(key));
    if (previous != null && !previous.expired(this.timeToLive)) {
      // Another thread won the race; use its lookup instead of starting a second one.
      return previous.future;
    }

    loader.get().whenComplete((profile, throwable) -> {
      if (throwable != null) {
        // Do not cache failures: a rate limit or a network hiccup must not poison the entry.
        cache.remove(key, fresh);
        fresh.future.completeExceptionally(throwable);
      } else {
        fresh.future.complete(profile);
      }
    });
    return fresh.future;
  }

  /**
   * Looks up the unique id belonging to a name.
   *
   * @param name the name to resolve.
   * @return a future holding the unique id.
   * @since 1.0.0
   */
  @NotNull
  private CompletableFuture<UUID> requestUniqueId(@NotNull String name) {
    return this.request(NAME_ENDPOINT + name).thenApply(body -> {
      JsonObject json = expectObject(body, "no account named '" + name + "' exists");
      JsonElement id = json.get("id");
      if (id == null || !id.isJsonPrimitive()) {
        throw new CompletionException(new IllegalStateException(
          "Mojang returned no unique id for '" + name + "'"));
      }
      return UUID.fromString(DASHES.matcher(id.getAsString()).replaceAll("$1-$2-$3-$4-$5"));
    });
  }

  /**
   * Looks up the full, signed profile of a unique id.
   *
   * @param uniqueId the unique id to resolve.
   * @return a future holding the profile.
   * @since 1.0.0
   */
  @NotNull
  private CompletableFuture<NpcProfile> requestProfile(@NotNull UUID uniqueId) {
    String id = uniqueId.toString().replace("-", "");
    return this.request(PROFILE_ENDPOINT + id + "?unsigned=false").thenApply(body -> {
      JsonObject json = expectObject(body, "no profile with the unique id " + uniqueId + " exists");

      JsonElement name = json.get("name");
      if (name == null || !name.isJsonPrimitive()) {
        throw new CompletionException(new IllegalStateException(
          "Mojang returned no name for " + uniqueId));
      }

      List<ProfileProperty> properties = new ArrayList<>();
      JsonElement rawProperties = json.get("properties");
      if (rawProperties != null && rawProperties.isJsonArray()) {
        JsonArray array = rawProperties.getAsJsonArray();
        for (JsonElement element : array) {
          if (!element.isJsonObject()) {
            continue;
          }
          JsonObject property = element.getAsJsonObject();
          JsonElement propertyName = property.get("name");
          JsonElement propertyValue = property.get("value");
          if (propertyName == null || propertyValue == null) {
            continue;
          }
          JsonElement signature = property.get("signature");
          properties.add(ProfileProperty.of(
            propertyName.getAsString(),
            propertyValue.getAsString(),
            signature == null || signature.isJsonNull() ? null : signature.getAsString()));
        }
      }
      return NpcProfile.of(uniqueId, name.getAsString(), properties);
    });
  }

  /**
   * Performs one GET request on the internal threads and returns its body.
   *
   * @param url the url to request.
   * @return a future holding the response body, empty when Mojang knows no such name or profile.
   * @since 1.0.0
   */
  @NotNull
  private CompletableFuture<String> request(@NotNull String url) {
    return CompletableFuture.supplyAsync(() -> get(url), this.executor);
  }

  /**
   * Performs one blocking GET request.
   *
   * <p>Mojang answers an unknown name or profile with 204 or 404 and an empty body, both of which
   * come back as an empty string.
   *
   * @param url the url to request.
   * @return the response body.
   * @throws CompletionException if the request fails or Mojang answers with an error.
   * @since 1.0.0
   */
  @NotNull
  private static String get(@NotNull String url) {
    HttpURLConnection connection = null;
    try {
      connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
      connection.setConnectTimeout(TIMEOUT_MILLIS);
      connection.setReadTimeout(TIMEOUT_MILLIS);
      connection.setRequestProperty("Accept", "application/json");
      connection.setRequestProperty("User-Agent", USER_AGENT);

      int status = connection.getResponseCode();
      if (status == 200) {
        return readBody(connection.getInputStream());
      }
      if (status == 204 || status == 404) {
        return "";
      }
      if (status == 429) {
        throw new CompletionException(new IllegalStateException(
          "Mojang rate limited the request to " + url + "; try again in a few minutes"));
      }
      throw new CompletionException(new IllegalStateException(
        "Mojang answered " + status + " for " + url));
    } catch (IOException exception) {
      throw new CompletionException(exception);
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
  }

  /**
   * Reads a whole response body as UTF-8.
   *
   * @param stream the body stream; closed afterwards.
   * @return the body.
   * @throws IOException if reading fails.
   * @since 1.0.0
   */
  @NotNull
  private static String readBody(@NotNull InputStream stream) throws IOException {
    try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
      StringBuilder body = new StringBuilder();
      char[] buffer = new char[4096];
      int read;
      while ((read = reader.read(buffer)) != -1) {
        body.append(buffer, 0, read);
      }
      return body.toString();
    }
  }

  /**
   * Parses a response body that has to be a JSON object.
   *
   * <p>Goes through the {@link JsonParser} instance method, the only one the Gson 2.2.4 bundled with
   * 1.8 has; newer Gson versions deprecate it but still ship it.
   *
   * @param body           the body to parse.
   * @param missingMessage the message used when the body is empty.
   * @return the parsed object.
   * @since 1.0.0
   */
  @NotNull
  @SuppressWarnings("deprecation")
  private static JsonObject expectObject(@NotNull String body, @NotNull String missingMessage) {
    if (body.isEmpty()) {
      throw new CompletionException(new IllegalArgumentException(missingMessage));
    }
    JsonElement parsed = new JsonParser().parse(body);
    if (!parsed.isJsonObject()) {
      throw new CompletionException(new IllegalStateException("Mojang returned a malformed response"));
    }
    return parsed.getAsJsonObject();
  }

  /**
   * A cache entry: the lookup and when it was started.
   *
   * @since 1.0.0
   */
  private static final class CachedProfile {

    private final CompletableFuture<NpcProfile> future;
    private final long createdAt = System.nanoTime();

    private CachedProfile(@NotNull CompletableFuture<NpcProfile> future) {
      this.future = future;
    }

    /**
     * Returns whether this entry has outlived the given time to live.
     *
     * @param timeToLive how long an entry stays valid.
     * @return {@code true} if the entry should be refreshed.
     * @since 1.0.0
     */
    private boolean expired(@NotNull Duration timeToLive) {
      return System.nanoTime() - this.createdAt >= timeToLive.toNanos();
    }
  }

  /**
   * Names the HTTP threads so that a stack trace or a thread dump points at this library.
   *
   * @since 1.0.0
   */
  private static final class NamedThreadFactory implements ThreadFactory {

    private final AtomicInteger counter = new AtomicInteger();

    @Override
    public Thread newThread(@Nullable Runnable runnable) {
      Thread thread = new Thread(runnable, "effigy-profile-" + this.counter.incrementAndGet());
      thread.setDaemon(true);
      return thread;
    }
  }
}
