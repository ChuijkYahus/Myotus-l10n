# Myotus

Myotus is a small Applied Energistics 2 extension library for Minecraft mod developers.

It provides public API hooks for optional integrations, AE2 terminal configuration tabs,
shared creative-tab entries, terminal upgrade cards, and a few pure helper APIs used by
addons.

Myotus is intentionally not a large standalone content mod. Its main job is to be a
stable bridge that other AE2-related mods can depend on.

## Supported Minecraft Lines

| Module | Loader | Minecraft | Java | AE2 | Artifact version |
| --- | --- | --- | --- | --- | --- |
| `forge-1-20-1` | Forge `47.x` | `1.20.1` | `17` | `15.4.x` | `15.1.3-SNAPSHOT` |
| `neoforge-1-21-1` | NeoForge `21.1.x` | `1.21.1` | `21` | `19.2.x` | `19.1.3-SNAPSHOT` |

The project has a shared `common` source set plus loader-specific modules. Forge and
NeoForge APIs are kept separate when Minecraft/loader differences make that healthier
than forcing artificial parity.

## What Myotus Provides

- `MyotusAPI` as the main public API entry point.
- Optional integration discovery and runtime checks.
- AE2 terminal config-tab registration.
- Shared Myotus creative-tab registration.
- Extensible argument adapters for annotation-driven commands.
- Item-backed AE2 terminal upgrade cards.
- Terminal upgrade query helpers.
- Client widget helpers.
- Pure experience math helpers for Applied Experienced / `fluid:xp` style integrations.
- A normal loader-discoverable Myotus mod artifact for addon development and runtime use.

## For Players and Modpacks

If another mod depends on Myotus, install Myotus alongside that mod, Applied Energistics 2,
and the correct loader for your Minecraft version.

Myotus can be included in modpacks. On its own it may look small, because most of its value
is exposed to other mods through API hooks.

## For Addon Developers

### Dependency Coordinates

Use the `myotus` artifact when compiling an addon against Myotus:

```gradle
repositories {
    mavenLocal() // useful while testing local builds
    mavenCentral()
    maven {
        name = "ModMaven"
        url = "https://modmaven.dev"
        content {
            includeGroup "appeng"
            includeGroup "de.mari_023"
        }
    }
}

dependencies {
    // Forge 1.20.1 line: mapped compile API plus the loader-discoverable runtime mod
    compileOnly "me.myogoo:myotus:15.1.3-SNAPSHOT:api"
    runtimeOnly fg.deobf("me.myogoo:myotus:15.1.3-SNAPSHOT")

    // NeoForge 1.21.1 line
    // compileOnly "me.myogoo:myotus:19.1.3-SNAPSHOT:api"
    // runtimeOnly "me.myogoo:myotus:19.1.3-SNAPSHOT"
}
```

The published `myotus` jar is the normal loader-discoverable Myotus mod artifact. It is
not an API-only jar: it includes loader metadata, Myotus runtime classes, assets, and data
resources. Addons should still prefer the public `me.myogoo.myotus.api.*` packages and
avoid depending on implementation packages unless a class is explicitly promoted into the
public API.

The `api` classifier is a mapped compile-time artifact. It is not a standalone runtime mod;
keep the normal unclassified artifact in the development runtime and declare Myotus in loader metadata.

When using Myotus features at runtime, also declare the matching Myotus mod dependency in
your loader metadata so the mod is present before your addon initializes.

### Local Publishing

From the repository root:

```bash
./gradlew :forge-1-20-1:publishToMavenLocal
./gradlew :neoforge-1-21-1:publishToMavenLocal
```

On Windows, use `gradlew.bat`. From WSL against a Windows-mounted workspace, this project
is commonly verified with:

```bash
/mnt/c/Windows/System32/cmd.exe /c "gradlew.bat build :forge-1-20-1:publishToMavenLocal :neoforge-1-21-1:publishToMavenLocal --console=plain"
```

## API Quick Start

### Access the API Safely

```java
MyotusAPI.tryGet().ifPresent(api -> {
    // Register API contributions during normal mod setup.
});
```

Avoid calling `MyotusAPI.get()` from static initializers. Use it during or after normal mod
setup when Myotus has installed its API instance.

### Optional Integration Checks

```java
if (MyotusAPI.integrations().isLoaded("examplemod")) {
    // Run code that should only be active when examplemod is present.
}
```

You can also query by Myotus integration annotation class or ASM `Type` where appropriate.

### Terminal Config Tabs

Register custom tabs for AE2 terminal configuration screens:

```java
MyotusAPI.configTabs().registerTerminalConfigTab(new MyoConfigTab(
        ResourceLocation.fromNamespaceAndPath("examplemod", "terminal_settings"),
        Component.literal("Example"),
        new ItemStack(Items.REDSTONE),
        "example_terminal.json",
        new ExampleConfigScreen()
));
```

When the tab is only valid for certain terminal contexts, attach a visibility rule:

```java
MyotusAPI.configTabs().registerTerminalConfigTab(new MyoConfigTab(
        ResourceLocation.fromNamespaceAndPath("examplemod", "portable_terminal_settings"),
        Component.literal("Portable"),
        new ItemStack(Items.REDSTONE),
        "portable_terminal.json",
        new PortableConfigScreen()
).visibleWhen(context -> context.isItemHost()));
```

### Shared Creative Tab Entries

Add items or preconfigured stacks to the shared Myotus creative tab:

```java
MyotusAPI.creativeTabs().registerCreativeTabItem(MY_ITEM);

MyotusAPI.creativeTabs().registerCreativeTabStack(
        () -> new ItemStack(MY_ITEM.get(), 1)
);
```

### Cycle Buttons

`MyoCycleButton` reports left and right clicks explicitly, including right clicks that an AE2 screen
forwards to child widgets as button `0`:

```java
var cycleButton = new MyoCycleButton(
        mouseButton -> {
            switch (mouseButton) {
                case LEFT -> selectNext();
                case RIGHT -> selectPrevious();
            }
        },
        this::selectedItem,
        this::tooltip
);
```

For separate actions without a switch, use the constructor accepting `leftClickAction` and
`rightClickAction`. Keyboard activation follows the left-click action.

### Command Arguments

Annotation-driven commands support built-in primitive, string, entity, and player parameters. Register
an adapter for an addon-specific parameter type through the public API before commands are built:

```java
MyotusAPI.commands().registerArgument(MyValue.class, myValueArgumentAdapter);
```

Each Java parameter type has exactly one adapter; duplicate registration fails instead of replacing the
existing parser silently.

### Terminal Upgrade Cards

Upgrade cards are normal items that implement `ITerminalUpgradeCard`:

```java
public class MyUpgradeCardItem extends Item implements ITerminalUpgradeCard {
    public MyUpgradeCardItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public void onTerminalOpen(MEStorageMenu menu, ItemStack stack) {
        // Apply behavior when the terminal opens.
    }

    @Override
    public void onTerminalClose(MEStorageMenu menu, ItemStack stack) {
        // Clean up behavior when the terminal closes.
    }
}
```

Useful helper queries:

```java
boolean installed = MyotusAPI.terminalUpgrades().hasUpgrade(menu, MY_UPGRADE_ID);
List<ItemStack> upgrades = MyotusAPI.terminalUpgrades().installedUpgrades(menu);
```

A terminal can only install one copy of the same upgrade item type. Upgrade card items
should generally have max stack size `1`.

### Experience Helpers

`MyotusAPI.experience()` uses raw vanilla XP points throughout. The math API is independent of
Applied Experienced and does not load optional-mod classes.

```java
var xp = MyotusAPI.experience();

long total = xp.total(30, 40, 50);
int level = xp.levelForTotal(total);
long progress = xp.intoLevel(total);
long next = xp.toNextLevel(level);
```

For a pure, non-mutating payment plan, supply named balances. Every amount must already be
normalized to raw XP points:

```java
import me.myogoo.myotus.api.experience.ExperienceMath;

var available = new ExperienceMath.ExperienceAmounts(30, 40, 50);
var plan = xp.plan(75, available);

if (plan.canPay()) {
    long playerUsed = plan.player();
    long fluidUsed = plan.fluidXp();
    long appliedUsed = plan.appliedExperiencedAmount();
}
```

The default order is player, fluid, then Applied Experienced. Pass a list to `plan(...)` for a
custom order.

ME fluid keys store fluid units, not XP points. A fluid source is therefore disabled by default;
register an explicit matcher and conversion rate from the owning integration's configuration:

```java
import me.myogoo.myotus.api.experience.ExperienceMath;

var fluidAdapter = xp.fluidStorage(
        key -> key.getId().equals(ResourceLocation.fromNamespaceAndPath("example", "liquid_xp")),
        250 // storage units per raw XP point
);

var adapters = List.of(xp.appliedExperiencedStorage(), fluidAdapter);
var priority = List.of(
        ExperienceMath.ExperienceSource.PLAYER,
        ExperienceMath.ExperienceSource.FLUID_XP,
        ExperienceMath.ExperienceSource.APPLIED_EXPERIENCED_AMOUNT
);

boolean payable = xp.canConsume(energy, storage, actionSource, player, 75, priority, adapters);
boolean consumed = payable && xp.consume(energy, storage, actionSource, player, 75, priority, adapters);
```

The built-in Applied Experienced adapter matches both the AE key type and key id. Stable ids remain
available for integrations that need them:

```java
String appexMod = xp.appliedModId();                 // "appex"
String appexKey = xp.appliedAeKeyId();               // "appex:experience"
String amountId = xp.appliedAmountComponentId();
```

## Repository Layout

```text
common/             Shared source and tests copied into loader modules
forge-1-20-1/       Forge 1.20.1 implementation and API surface
neoforge-1-21-1/    NeoForge 1.21.1 implementation and API surface
readme/             Mod-page README drafts/assets
repo/               Optional project-local Maven repository
```

## Building and Testing

From the repository root:

```bash
./gradlew build
./gradlew :common:test
./gradlew :forge-1-20-1:compileJava
./gradlew :neoforge-1-21-1:compileJava
```

Loader-specific runtime tasks live in each module:

```bash
./gradlew :forge-1-20-1:runClient
./gradlew :forge-1-20-1:runGameTestServer

./gradlew :neoforge-1-21-1:runClient
./gradlew :neoforge-1-21-1:runGameTestServer
```

## API Boundary Notes

- Prefer `me.myogoo.myotus.api.*` classes from the `myotus` artifact.
- Do not rely on `me.myogoo.myotus.util`, `dto`, `client`, `init`, or other implementation
  packages unless they are explicitly promoted into the public API.
- Optional-mod integrations should avoid importing optional classes into always-loaded code
  paths unless guarded by a safe loader-specific isolation pattern.
- Some Forge-only or NeoForge-only API differences are deliberate.

## License

Myotus is licensed under the **GNU Lesser General Public License v3.0**.
