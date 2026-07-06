# DustyEconomy

A Java/Gradle Spigot-Paper plugin converted from the original `economy.sk` Skript script.

## Feature mapping

| Skript feature | Java equivalent |
|---|---|
| `variables: {orbs::*} = 0`, `{souls::*} = 0` and the `on join` init block | `EconomyManager.registerPlayer()`, called from `PlayerJoinListener` |
| `command /sell` | `commands/SellCommand.java` |
| `function formatCurrency(...)` | `util/CurrencyFormatter.formatCurrency()` |
| `function formatCredits(...)` | `util/CurrencyFormatter.formatCredits()` |
| `every 1 second: loop all players` (fastboard updates) | `EconomyPlugin`'s `BukkitRunnable`, ticking every 20 ticks |
| player's `fastboard` (third-party Skript addon) | `scoreboard/SidebarManager.java`, built on vanilla Bukkit `Scoreboard`/`Team` APIs — no extra dependency needed |
| `command /sidebar-update <player>` (`permission: console`) | `commands/SidebarUpdateCommand.java` — allows console or `dustyeconomy.sidebar` |
| `command /currencyset <text> <number> <player>` | `commands/CurrencySetCommand.java` |
| `command /currencyadd <text> <number> <player>` | `commands/CurrencyAddCommand.java` |
| Skript's global `{money::%uuid%}` variables (persisted to `variables.csv`) | `EconomyManager`, persisted to `plugins/DustyEconomy/data.yml` |
| Hardcoded sell prices (cobblestone/coal/diamond) | `config.yml`'s `sell-prices` section — now easy to edit/extend without touching code |

## Notable differences from the Skript version

- **No external "fastboard" dependency.** The Skript relied on a fastboard addon/library. The Java version implements the same sidebar using Bukkit's built-in scoreboard API, so there's nothing extra to install on the server.
- **Sell prices are configurable.** Instead of an `if/else if` chain in code, item -> price/currency mappings live in `config.yml` so server admins can add new sellable items without recompiling.
- **Data persistence.** Balances are saved to `plugins/DustyEconomy/data.yml` on disable and loaded on enable/join, replacing Skript's variable storage.
- Numbers are stored as `double` (matches Skript's flexible `number` type).

## Project layout

```
build.gradle
settings.gradle
src/main/resources/plugin.yml
src/main/resources/config.yml
src/main/java/com/dustyrpg/economy/
    EconomyPlugin.java          # main class, wiring + scoreboard tick
    Currency.java               # MONEY / ORBS / SOULS / CREDITS enum
    commands/
        SellCommand.java
        CurrencySetCommand.java
        CurrencyAddCommand.java
        SidebarUpdateCommand.java
    data/
        EconomyManager.java     # balance storage + YAML persistence
    listeners/
        PlayerJoinListener.java
    scoreboard/
        SidebarManager.java     # native scoreboard sidebar
    util/
        CurrencyFormatter.java  # formatCurrency() / formatCredits()
```

## Building

This project targets Java 17 and the Paper API (1.20.4). It has no external
runtime dependencies, so it builds a plain jar via Gradle's built-in `java`
plugin — no shading needed.

> Earlier revisions of this project used the Gradle Shadow plugin, but
> Shadow 8.1.1 is incompatible with newer Gradle releases (9.x), causing a
> `MissingPropertyException: No such property: mode` failure during
> `shadowJar`. Since there's nothing to shade, the fix is simply to build a
> normal jar instead.

```bash
# If you don't already have a gradle wrapper jar, generate one first
# (requires a local Gradle install and network access):
gradle wrapper --gradle-version 8.7

# Then build:
./gradlew build
```

The compiled plugin jar will be at `build/libs/DustyEconomy-1.0.0.jar`.
Drop it into your server's `plugins/` folder.

> Note: the `gradle-wrapper.jar` binary itself isn't included here (binary
> files aren't generated in this conversion). Run `gradle wrapper` once
> with a local Gradle installation to create it, or just build with your
> own installed Gradle: `gradle build`.

## Permissions

- `dustyeconomy.admin` (default: op) — `/currencyset`, `/currencyadd`
- `dustyeconomy.sidebar` (default: op) — `/sidebar-update` for non-console senders (console can always run it)

## Customizing sell prices

Edit `plugins/DustyEconomy/config.yml`:

```yaml
sell-prices:
  COBBLESTONE:
    price: 1
    currency: money
  COAL:
    price: 10
    currency: money
  DIAMOND:
    price: 5
    currency: credits
```

Material names must match Bukkit's `Material` enum. Currency must be one of
`money`, `orbs`, `souls`, `credits`. Restart or implement a `/reload`-triggered
call to `SellCommand.loadPrices()` to pick up changes.
