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

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * /givehead <player> <mob-or-skin> <head name...>
 *
 * Supports two kinds of skin source in config:
 *   a) Player/skin name (e.g. "MHF_Zombie") - resolved via Mojang API
 *   b) Base64 texture property (the eyJ... value from head databases) -
 *      applied directly via GameProfile reflection, no API call needed
 */
public final class GiveHeadCommand implements CommandExecutor, TabCompleter {

    private final GiveHeadPlugin plugin;

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
            deliver(sender, target, cached, headNameText);
            return;
        }

        String targetName = target.getName();
        skins.resolve(skinName).thenAcceptAsync(profile -> {
            Player stillOnline = Bukkit.getPlayerExact(targetName);
            if (stillOnline == null) {
                return;
            }
            deliver(sender, stillOnline, profile, headNameText);
        }, runnable -> Bukkit.getScheduler().runTask(plugin, runnable));
    }

    private void deliver(CommandSender sender, Player target, PlayerProfile profile, String headNameText) {
        ItemStack head = buildHead(profile, headNameText);
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

    private ItemStack buildHead(PlayerProfile profile, String headNameText) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            meta.setOwnerProfile(profile);
            meta.setDisplayName(Text.color(headNameText + " Head"));
            skull.setItemMeta(meta);
        }
        return skull;
    }

    /**
     * Builds a player head with a custom texture applied via GameProfile
     * reflection. This is the standard Spigot pattern for applying base64
     * texture data (from minecraft-heads.com etc.) without needing a real
     * player account.
     */
    private ItemStack buildTexturedHead(String base64Texture, String headNameText) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta == null) return skull;

        try {
            // com.mojang.authlib.GameProfile is bundled with every Spigot/Paper server
            Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
            Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");
            Class<?> propertyMapClass = Class.forName("com.mojang.authlib.properties.PropertyMap");

            Constructor<?> profileConstructor = gameProfileClass.getConstructor(UUID.class, String.class);
            Object profile = profileConstructor.newInstance(UUID.randomUUID(), "custom_head");

            Method getProperties = gameProfileClass.getMethod("getProperties");
            Object properties = getProperties.invoke(profile);

            Constructor<?> propertyConstructor = propertyClass.getConstructor(String.class, String.class);
            Object textureProperty = propertyConstructor.newInstance("textures", base64Texture);

            Method putMethod = propertyMapClass.getMethod("put", Object.class, Object.class);
            putMethod.invoke(properties, "textures", textureProperty);

            Field profileField = meta.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            profileField.set(meta, profile);
        } catch (Exception e) {
            plugin.getLogger().warning("[GiveHead] Failed to apply texture property: " + e.getMessage());
            return null;
        }

        meta.setDisplayName(Text.color(headNameText + " Head"));
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
