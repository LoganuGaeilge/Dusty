# GiveHead

A tiny Bukkit/Spigot plugin replacing the old Skript `/givehead` command.

## Command

```
/givehead <player> <mob-or-skin> <head name...>
/givehead reload
```

- `<player>` - the **online** player who receives the head.
- `<mob-or-skin>` - either:
  - a mob/entity type (`ZOMBIE`, `SKELETON`, `PIG`, ...) which is looked up
    in `config.yml` under `mob-heads:` to find which offline-player skin to
    render, or
  - any arbitrary offline-player/skin name (e.g. `MHF_Chicken`, a real IGN),
    used directly - exactly like the original Skript version.
- `<head name...>` - display name text, spaces allowed, no quotes needed.
  `" Head"` is appended automatically (matches the Skript's
  `"%arg-3% Head"`).

Unmapped-but-recognised mob types automatically fall back to
`default-player:` in `config.yml`, so you only have to configure the mobs you
care about.

`/givehead reload` (permission `givehead.admin`) reloads `config.yml` live.

## Config

See `src/main/resources/config.yml` - `default-player`, `mob-heads`, and
`messages` are all editable without touching Java.

## Building

```
gradle build
```

or with the wrapper once generated:

```
./gradlew build
```

The jar lands in `build/libs/GiveHead-1.0.0.jar`.

## Why it now actually shows the right skins

`Bukkit.getOfflinePlayer(name)` (what the original approach — and Skript's
own offline-player parsing — ultimately leans on) does **not** reliably do a
live Mojang name → UUID → texture lookup on modern Spigot/Paper for a name
the server has never seen, like `MHF_Zombie`. It just fabricates a fake
offline UUID with no texture data, so the head renders as the default
Steve/Alex skin.

GiveHead instead uses Bukkit's `PlayerProfile` API and calls
`profile.update()`, which performs a genuine (asynchronous) Mojang lookup by
name and fills in the real skin texture - the same mechanism vanilla's
`/give ... SkullOwner:` NBT tag and Skript's `skull of` effect rely on under
the hood. Results are cached in `SkinCache` so repeated requests for the
same skin (the same mob dying over and over) are instant after the first
lookup, and every skin referenced in `config.yml` is pre-resolved on
startup/reload so there's no in-game delay the first time a mob drops a
head.

## Wiring it up to CustomEnchants

`CustomEnchants`' `%victim%` placeholder is just the killed entity's UUID,
which isn't useful for picking a head skin. A one-line addition was made to
`ActionExecutor.java` (see the patched copy included alongside this plugin)
that adds a new `%victim_type%` placeholder - the entity's type name (e.g.
`ZOMBIE`) - captured at kill-time before the entity is removed from the
world, so it's always accurate.

With that in place, a CustomEnchants `on-kill` action like:

```yaml
on-kill:
  - "chance: %level_num% * 10: cmd: givehead %player% %victim_type% %victim_type%"
```

always gives the correct head for whatever was actually killed - GiveHead
does the mob-to-skin lookup (and default-player fallback) on its own, so
CustomEnchants' config never needs to hardcode a skin name again.
