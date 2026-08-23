# Effigy

A packet NPC library for Paper and Spigot. Every public member is documented, the threading rules
are explicit and enforced, and the parts that can be tested without a server are tested.

Effigy is a from-scratch rewrite inspired by [NPC-Lib](https://github.com/juliarn/NPC-Lib). It gives
up NPC-Lib's multi-platform reach in exchange for an API that is far easier to hold in your head.

```java
effigy.profiles().resolveByName("Notch").thenAcceptAsync(profile -> {
  Npc guide = effigy.npc()
      .location(spawn)
      .profile(profile.withName("Guide"))
      .settings(settings -> settings
          .viewDistance(32)
          .lookAtViewer(true)
          .visibilityRule(VisibilityRule.permission("lobby.guide")))
      .metadata("role", "shop")
      .build();

  guide.equipment(EquipmentSlot.HAND, new ItemStack(Material.EMERALD));
  guide.glowing(ChatColor.GREEN);
  guide.hologram().lines("&e&lVillage Shop", "&7Right click to browse");
}, mainThread);
```

---

## What is different from NPC-Lib

| | NPC-Lib v3 | Effigy |
|---|---|---|
| Type signature | `Npc<W, P, I, E>` threaded through every class | `Npc`, no generics |
| Javadoc | essentially none | every public member, with `@throws` and threading notes |
| Events | custom event bus, custom subscription API | Bukkit events, `@EventHandler`, priorities, `ignoreCancelled` |
| Packet backends | ProtocolLib **and** PacketEvents, both partially maintained | PacketEvents only, one well-tested path |
| Threading contract | implicit | documented and enforced with a fail-fast check |
| Duplicate clicks | reach the plugin | collapsed by a per-player cooldown before the event fires |
| Player references | held until manually released | dropped on quit and on world change |
| Profile lookups | resolved per NPC | cached and collapsed, one HTTP request per skin |
| Version branching | packet-library enum constants | a parsed `MinecraftVersion` with readable ranges |
| Holograms | not included | built in, static or per viewer |
| Tests | none | the protocol-independent logic is covered |

The generics are the headline. In NPC-Lib a field holding an NPC is declared
`Npc<World, Player, ItemStack, Plugin>`, and that signature propagates into every method, every
listener and every collection of the plugin using it. Targeting Bukkit alone makes the same code
read as `Npc`.

## Requirements

- Java 17 or newer
- Paper or Spigot 1.17 – 1.21.x
- [PacketEvents](https://github.com/retrooper/packetevents) 2.x, either as a server plugin or shaded

## Installation

Effigy is not on Maven Central yet. Publish it locally with `./gradlew publishToMavenLocal`, or use
JitPack:

```kotlin
repositories {
  mavenCentral()
  maven("https://jitpack.io")
  maven("https://repo.codemc.io/repository/maven-releases/") // PacketEvents
}

dependencies {
  implementation("com.github.ricodevvv.Efiggy:effigy-bukkit:1.0.0")
  compileOnly("com.github.retrooper:packetevents-spigot:2.13.0")
}
```

Relocate `dev.ricodev.effigy` when you shade it, so two plugins bundling different versions do not
fight over the same classes.

## Bootstrapping

PacketEvents installs a channel handler into the server pipeline and has to do so before the first
player can connect, which means `onLoad`, not `onEnable`. Effigy checks this and fails with a
readable message rather than handing back an instance whose click handling silently does nothing.

**PacketEvents as a plugin** — add `depend: [packetevents]` to `plugin.yml`, then:

```java
@Override
public void onEnable() {
  this.effigy = EffigyBukkit.create(this);
}

@Override
public void onDisable() {
  if (this.effigy != null) {
    this.effigy.close();
  }
}
```

**PacketEvents shaded** — add one line to `onLoad`:

```java
@Override
public void onLoad() {
  EffigyBukkit.bootstrapPacketEvents(this);
}
```

## Threading

- **Read-only methods** are safe from any thread.
- **Mutating methods** must run on the main thread and throw `IllegalStateException` otherwise.
- **Events** always fire on the main thread, including the interaction event that starts life on a
  netty thread.
- **`ProfileResolver` futures** complete off the main thread. Hop back before touching Bukkit.

The strict rule is deliberate. A spawn packet sent from an async task and interleaved with the
packets of the tracking task produces a ghost entity on the client that no later packet can clear,
and the bug reproduces roughly once a week on a busy server. Failing at the call site is cheaper.

## Interactions

```java
@EventHandler
public void onInteract(NpcInteractEvent event) {
  if (event.action() != NpcInteractEvent.Action.RIGHT_CLICK) {
    return;
  }
  if ("shop".equals(event.npc().metadata().get("role"))) {
    this.openShop(event.player());
  }
}
```

A single right click makes a vanilla client send two packets, and a modified client can send
thousands per second. Effigy drops `INTERACT_AT`, applies the per-player
`NpcSettings#interactionCooldown` (250 ms by default), and only then fires the event, so a listener
sees one event per real click. The originating packet is always cancelled: the server must never
learn that a player attacked an entity that does not exist.

## Visibility

`VisibilityRule` is a `BiPredicate` over an NPC and a player, asked once per player per tracking
cycle:

```java
VisibilityRule staffDuringEvent = VisibilityRule.permission("lobby.staff")
    .and((npc, player) -> this.eventRunning);
```

Keep it fast and side effect free; it runs on the main thread. `show(Player)` and `hide(Player)`
bypass it for one-off overrides, though the tracking task may undo them on its next cycle if the
rule disagrees.

## Holograms

Every NPC owns a hologram. It starts empty and sends nothing until it has lines:

```java
npc.hologram().lines(
    "&e&lVillage Shop",
    "&7Right click to browse");
```

Each line is an invisible marker armour stand sent only to the viewers of the NPC, so a hologram
inherits the visibility rule, the view distance and the lifetime of its NPC, and follows it when it
is teleported. Marker stands have no hitbox, so a hologram can never swallow a click meant for the
NPC underneath it. Both `&` and `§` colour codes work.

For text that differs per player, set a renderer instead:

```java
npc.hologram().renderer((npc, viewer) -> List.of(
    "&e&lVillage Shop",
    "&7Balance: &a" + economy.balance(viewer),
    viewer.hasPermission("shop.vip") ? "&6VIP prices active" : "&8Buy VIP for a discount"));
```

A renderer runs on the main thread, once per viewer, only when the hologram is refreshed. Effigy
never puts it on a timer, so call `refresh()` yourself when the underlying data changed:

```java
Bukkit.getScheduler().runTaskTimer(plugin, () -> npc.hologram().refresh(), 20L, 20L);
```

A refresh sends the minimum: the text of lines that already exist is updated in place, new lines are
spawned and surplus ones destroyed. Two viewers can be looking at a different number of lines at the
same time without either of them seeing the text flicker.

`offsetY` (2.15 blocks by default) places the bottom line above the feet of the NPC and
`lineSpacing` (0.28) sets the gap between lines. The defaults put the bottom line just clear of a
standing player's head, with lines sitting flush against each other.

## What Effigy deliberately does not do

- **Persistence.** NPCs are cheap to recreate from your own config on enable.
- **Pathfinding and AI.** These entities do not exist on the server, so there is nothing to path
  through. `teleport` on a repeating task covers scripted movement.
- **Folia.** The scheduler calls assume a single main thread.

## Building

The Gradle wrapper is not committed, so generate it once:

```bash
gradle wrapper --gradle-version 8.12
./gradlew build          # compile, test, and build the javadoc
./gradlew publishToMavenLocal
```

Undocumented public API is a build failure, not a warning: the javadoc task runs with
`-Xdoclint:all` and `-Werror`.

## Project layout

```
effigy-api/      interfaces and value types; depends only on the Bukkit API
effigy-bukkit/   the implementation; the only module that knows about PacketEvents
```

Everything under `dev.ricodev.effigy.bukkit.internal` is an implementation detail. The public
surface is the API module plus `EffigyBukkit`.

## Licence

MIT. See [LICENSE](LICENSE).
