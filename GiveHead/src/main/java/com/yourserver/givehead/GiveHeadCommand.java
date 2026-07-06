package com.yourserver.givehead;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * /givehead <player> <mob-or-skin> <head name...>
 *
 * Supports two kinds of skin source in config:
 *   a) Player/skin name (e.g. "MHF_Zombie") - resolved via Mojang API
 *   b) Base64 texture property (the eyJ... value from head databases) -
 *      the embedded skin URL is applied via Bukkit's PlayerProfile API,
 *      no external API call needed
 */
public final class GiveHeadCommand implements CommandExecutor, TabCompleter {

    private static final Pattern TEXTURE_URL_PATTERN =
            Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");

    private final GiveHeadPlugin plugin;

    /**
     * Cache of the base (un-named) textured head per base64 value. Building
     * the profile freshly for every request could yield item components that
     * aren't byte-for-byte identical, which stops otherwise-identical heads
     * from stacking. Cloning a single cached prototype guarantees identical
     * components, so heads stack. Accessed only from the main thread.
     */
    private final Map<String, ItemStack> texturedHeadCache = new HashMap<>();

    /**
     * Same idea as {@link #texturedHeadCache}, but for the player-skin path
     * (e.g. {@code MHF_Zombie}). A resolved skin already has a stable, per-skin
     * profile UUID, but cloning one cached prototype per skin guarantees the
     * item components are byte-for-byte identical so these heads always stack.
     * Accessed only from the main thread.
     */
    private final Map<String, ItemStack> skinHeadCache = new HashMap<>();

    public GiveHeadCommand(GiveHeadPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        HeadConfig cfg = plugin.getHeadConfig();

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("givehead.admin")) {
                sender.sendMessage(Text.color("&cYou don't have permission to do that."));
                return true;
            }
            plugin.reloadHeadConfig();
            sender.sendMessage(Text.color(cfg.msgReloaded()));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(Text.color(cfg.msgUsage()));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(Text.color(cfg.msgPlayerNotFound()));
            return true;
        }

        String mobOrSkin = args[1];
        String headNameText = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        if (cfg.isPrettyPrintHeadNames()) {
            headNameText = Text.prettifyMobToken(headNameText);
        }

        // Check for texture-property value first
        String textureValue = cfg.resolveTexture(mobOrSkin);
        if (textureValue != null) {
            ItemStack head = buildTexturedHead(textureValue, headNameText);
            if (head != null) {
                deliverItem(sender, target, head, headNameText);
            } else {
                sender.sendMessage(Text.color("&cFailed to apply texture data. Check console for errors."));
            }
            return true;
        }

        // Fall back to player-name resolution
        String skinName = cfg.resolveSkinName(mobOrSkin);
        giveHead(sender, target, skinName, headNameText);
        return true;
    }

    private void giveHead(CommandSender sender, Player target, String skinName, String headNameText) {
        SkinCache skins = plugin.getSkinCache();

        PlayerProfile cached = skins.getCached(skinName);
        if (cached != null) {
            deliver(sender, target, skinName, cached, headNameText);
            return;
        }

        String targetName = target.getName();
        skins.resolve(skinName).thenAcceptAsync(profile -> {
            Player stillOnline = Bukkit.getPlayerExact(targetName);
            if (stillOnline == null) {
                return;
            }
            deliver(sender, stillOnline, skinName, profile, headNameText);
        }, runnable -> Bukkit.getScheduler().runTask(plugin, runnable));
    }

    private void deliver(CommandSender sender, Player target, String skinName,
                         PlayerProfile profile, String headNameText) {
        ItemStack head = buildHead(skinName, profile, headNameText);
        deliverItem(sender, target, head, headNameText);
    }

    private void deliverItem(CommandSender sender, Player target, ItemStack head, String headNameText) {
        HeadConfig cfg = plugin.getHeadConfig();

        Map<Integer, ItemStack> overflow = target.getInventory().addItem(head);
        if (!overflow.isEmpty()) {
            overflow.values().forEach(item ->
                    target.getWorld().dropItemNaturally(target.getLocation(), item));
        }

        String displayName = Text.color(headNameText + " Head");
        target.sendMessage(Text.color(cfg.msgGivenConfirm().replace("%head%", displayName)));
        if (sender != target) {
            sender.sendMessage(Text.color(cfg.msgGivenToTarget()
                    .replace("%target%", target.getName())
                    .replace("%head%", displayName)));
        }
    }

    private ItemStack buildHead(String skinName, PlayerProfile profile, String headNameText) {
        // Cache one fully-resolved prototype per skin (only once the profile
        // is texture-complete, so a failed lookup isn't cached and can still
        // resolve later). Cloning it keeps components byte-identical -> stacks.
        String key = skinName.toLowerCase();
        ItemStack prototype = skinHeadCache.get(key);
        if (prototype == null) {
            prototype = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta protoMeta = (SkullMeta) prototype.getItemMeta();
            if (protoMeta != null) {
                protoMeta.setOwnerProfile(profile);
                prototype.setItemMeta(protoMeta);
            }
            if (profile.isComplete()) {
                skinHeadCache.put(key, prototype);
            }
        }

        ItemStack skull = prototype.clone();
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(headNameText + " Head"));
            skull.setItemMeta(meta);
        }
        return skull;
    }

    /**
     * Builds a player head with a custom texture applied via Bukkit's stable
     * {@link PlayerProfile}/{@link PlayerTextures} API.
     *
     * The old approach reflected a {@code com.mojang.authlib.GameProfile}
     * straight onto {@code CraftMetaSkull}'s private {@code profile} field.
     * That worked on 1.20.4 and earlier, but on 1.20.5+ (and 1.21.x, incl.
     * 1.21.4) that field is no longer a {@code GameProfile} - it's an NMS
     * {@code ResolvableProfile} - so the reflective {@code Field#set} throws
     * an {@code IllegalArgumentException} and the head silently fails.
     *
     * The base64 texture value from minecraft-heads.com etc. decodes to JSON
     * of the form {@code {"textures":{"SKIN":{"url":"http://textures...."}}}}.
     * We extract that skin URL and hand it to the version-agnostic Bukkit
     * API, which builds the correct profile for whatever server version is
     * running.
     */
    private ItemStack buildTexturedHead(String base64Texture, String headNameText) {
        ItemStack prototype = texturedHeadCache.get(base64Texture);
        if (prototype == null) {
            prototype = createTexturedHead(base64Texture);
            if (prototype == null) {
                return null;
            }
            texturedHeadCache.put(base64Texture, prototype);
        }

        ItemStack skull = prototype.clone();
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(headNameText + " Head"));
            skull.setItemMeta(meta);
        }
        return skull;
    }

    /** Builds the base (un-named) textured head for a base64 value, or null on failure. */
    private ItemStack createTexturedHead(String base64Texture) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta == null) return null;

        try {
            String decoded = new String(
                    Base64.getDecoder().decode(base64Texture.trim()), StandardCharsets.UTF_8);
            Matcher matcher = TEXTURE_URL_PATTERN.matcher(decoded);
            if (!matcher.find()) {
                plugin.getLogger().warning(
                        "[GiveHead] Could not find a skin URL in the supplied texture value.");
                return null;
            }

            // Derive the profile UUID deterministically from the texture so
            // that identical textures produce identical profiles. Player
            // heads only stack when their owner profiles match, so a random
            // UUID per head (the old behaviour) meant base64 heads never
            // stacked even when they were visually identical.
            UUID profileId = UUID.nameUUIDFromBytes(
                    base64Texture.trim().getBytes(StandardCharsets.UTF_8));
            PlayerProfile profile = Bukkit.createPlayerProfile(profileId, "");
            PlayerTextures textures = profile.getTextures();
            textures.setSkin(new URL(matcher.group(1)));
            profile.setTextures(textures);

            meta.setOwnerProfile(profile);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning(
                    "[GiveHead] Invalid base64 texture value (not a valid head texture): "
                            + e.getMessage());
            return null;
        } catch (Exception e) {
            plugin.getLogger().warning("[GiveHead] Failed to apply texture property: " + e.getMessage());
            return null;
        }

        skull.setItemMeta(meta);
        return skull;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> options = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toList());
            options.add("reload");
            return filter(options, args[0]);
        }

        if (args.length == 2) {
            List<String> options = new ArrayList<>();
            for (EntityType type : EntityType.values()) {
                if (type.isAlive()) {
                    options.add(type.name());
                }
            }
            return filter(options, args[1]);
        }

        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        return options.stream()
                .filter(option -> option.toLowerCase().startsWith(lower))
                .collect(Collectors.toList());
    }
}
