# Effigy

Packet NPCs for Spigot and Paper, from 1.8.8 up to 26.3, with holograms included. Built on
[PacketEvents](https://github.com/retrooper/packetevents).

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
  guide.hologram().content(
      HologramLine.animated(TextAnimation.wave("&7Village Shop", ChatColor.YELLOW, Duration.ofMillis(80))),
      HologramLine.item(new ItemStack(Material.EMERALD)),
      HologramLine.text("&8Right click to browse"));
}, mainThread);
```

The NPCs only exist as packets, so the server never ticks them and other plugins never see them.
Events are plain Bukkit events, a click fires once no matter how many packets the client sends,
and skins are cached so a hundred NPCs with the same skin cost one request to Mojang.

## Setup

You need Java 8+, Spigot or Paper 1.8.8 – 26.3 and PacketEvents 2.14.0+, either installed as a
plugin or shaded into yours.

Effigy isn't on Maven Central yet, so grab it from JitPack:

```kotlin
repositories {
  mavenCentral()
  maven("https://jitpack.io")
  maven("https://repo.codemc.io/repository/maven-releases/")
}

dependencies {
  implementation("com.github.ricodevvv.Efiggy:effigy-bukkit:main-SNAPSHOT")
  compileOnly("com.github.retrooper:packetevents-spigot:2.14.0")
}
```

There's no tagged release yet, so use `main-SNAPSHOT` or a commit hash. If you shade Effigy,
relocate `dev.ricodev.effigy` so two plugins bundling different versions don't clash.

With PacketEvents as a plugin, add `depend: [packetevents]` to your `plugin.yml` and create the
instance in `onEnable`:

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

If you shade PacketEvents instead, also call `EffigyBukkit.bootstrapPacketEvents(this)` in
`onLoad`. It has to happen there, before players can connect; Effigy tells you if you got it wrong.

## Threading

Getters work from any thread. Anything that changes an NPC has to run on the main thread and throws
otherwise, because a spawn packet sent from an async task can leave a ghost entity on the client
that nothing clears. Events always fire on the main thread. `ProfileResolver` futures complete off
it, so hop back before touching Bukkit.

## Clicks

```java
@EventHandler
public void onInteract(NpcInteractEvent event) {
  if (event.action() == NpcInteractEvent.Action.RIGHT_CLICK
      && "shop".equals(event.npc().metadata().get("role"))) {
    this.openShop(event.player());
  }
}
```

Duplicate packets are dropped and there's a per-player cooldown (`interactionCooldown`, 250 ms by
default), so you get one event per real click.

## Visibility

```java
VisibilityRule staffDuringEvent = VisibilityRule.permission("lobby.staff")
    .and((npc, player) -> this.eventRunning);
```

The rule is checked for every nearby player a few times a second, so keep it cheap. `show(Player)`
and `hide(Player)` skip it, but the next check can undo them if the rule disagrees.

## Holograms

Every NPC has one. It's empty until you give it lines, and it follows the NPC around:

```java
npc.hologram().lines("&e&lVillage Shop", "&7Right click to browse");
```

`content` lets you mix text, floating items and animated lines:

```java
npc.hologram().content(
    HologramLine.animated(TextAnimation.typewriter("&eWelcome", Duration.ofMillis(60))),
    HologramLine.item(new ItemStack(Material.DIAMOND_SWORD)),
    HologramLine.text("&8Right click to browse"));
```

There are four animations: `typewriter`, `wave`, `colorCycle` and `of` for your own frames. They
count visible characters, so colour codes never show up half typed.

For text that changes per player, use a renderer and call `refresh()` when the data changes.
Effigy never refreshes on its own:

```java
npc.hologram().renderer((npc, viewer) -> Arrays.asList(
    HologramLine.text("&e&lVillage Shop"),
    HologramLine.text("&7Balance: &a" + economy.balance(viewer))));

Bukkit.getScheduler().runTaskTimer(plugin, () -> npc.hologram().refresh(), 20L, 20L);
```

Spacing is set with `offsetY`, `lineSpacing` and `itemLineHeight` on the hologram.

## Old versions

One jar works on every version. A few things just don't exist on older ones:

- Glowing needs 1.9. On 1.8 it does nothing.
- Off hand needs 1.9. On 1.8 it's ignored, and every click reports `HAND`.
- Poses need 1.14. Before that only `CROUCHING` works, as sneaking.
- Before 1.19.3 the tab list entry gets removed after `tabListRemovalDelay`, since those versions
  can't hide it any other way.
- On 1.8 and 1.9 item lines ride an invisible armour stand so they don't fall. Looks the same.

Players on other client versions through ViaVersion or ViaBackwards work as usual.

Effigy doesn't save NPCs, has no pathfinding or AI, and doesn't support Folia.

## Building

```bash
./gradlew build
```

Any JDK 17+ works and the jar always comes out as Java 8. The code compiles against the 1.8.8 API,
and `check` compiles it again against the latest Paper API to catch anything that got removed. That
second pass needs JDK 21; Gradle downloads it if you don't have it. Undocumented public API breaks
the build.

`effigy-api` has the interfaces, `effigy-bukkit` the implementation. Anything under
`dev.ricodev.effigy.bukkit.internal` can change without notice.

## License

MIT. See [LICENSE](LICENSE).
