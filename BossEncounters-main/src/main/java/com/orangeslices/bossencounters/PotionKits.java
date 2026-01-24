package com.orangeslices.bossencounters;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class PotionKits {

    private PotionKits() {}

    public enum PotionTokenType {
        HASTE,
        STRENGTH,
        FIRE_RESISTANCE,
        HEALTH_BOOST,
        NIGHT_VISION,
        WATER_BREATHING
    }

    public static ItemStack makePotionKit(BossEncountersPlugin plugin, PotionTokenType type, int level) {
        level = clampLevel(level);

        Material mat = materialFor(type);
        String name = displayNameFor(type, level);
        List<String> lore = loreFor(type, level);

        ItemStack item = new ItemStack(mat, 1);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(name);
        meta.setLore(lore);

        // Universal token tags used by AddOnListener
        NamespacedKey tokenTypeKey = new NamespacedKey(plugin, "token_type");
        NamespacedKey tokenLevelKey = new NamespacedKey(plugin, "token_level");

        meta.getPersistentDataContainer().set(tokenTypeKey, PersistentDataType.STRING, type.name());
        meta.getPersistentDataContainer().set(tokenLevelKey, PersistentDataType.INTEGER, level);

        item.setItemMeta(meta);
        return item;
    }

    /* =========================
       Token Definitions
       ========================= */

    private static Material materialFor(PotionTokenType type) {
        // Feel free to swap these to match your “Amethyst for weapons, Quartz for tools” idea later.
        return switch (type) {
            case HASTE -> Material.QUARTZ;
            case STRENGTH -> Material.BLAZE_POWDER;
            case FIRE_RESISTANCE -> Material.MAGMA_CREAM;
            case HEALTH_BOOST -> Material.GLISTERING_MELON_SLICE;
            case NIGHT_VISION -> Material.GOLDEN_CARROT;
            case WATER_BREATHING -> Material.PRISMARINE_CRYSTALS;
        };
    }

    private static String displayNameFor(PotionTokenType type, int level) {
        // Color vibe per potion
        String roman = (level == 1) ? "I" : "II";
        return switch (type) {
            case HASTE -> "§eHaste Kit " + roman;
            case STRENGTH -> "§cStrength Kit " + roman;
            case FIRE_RESISTANCE -> "§6Fire Resistance Kit " + roman;
            case HEALTH_BOOST -> "§dHealth Boost Kit " + roman;
            case NIGHT_VISION -> "§9Night Vision Kit " + roman;
            case WATER_BREATHING -> "§3Water Breathing Kit " + roman;
        };
    }

    private static List<String> loreFor(PotionTokenType type, int level) {
        String roman = (level == 1) ? "I" : "II";

        // These lines are “documentation on the item” like you wanted.
        return switch (type) {
            case HASTE -> List.of(
                    "§7Upgrade Token",
                    "§aEffect: Haste " + roman + " while holding",
                    "§7Applies to: Tools / Weapons",
                    "§7Use: Apply to a tool/weapon",
                    "§8(No particles • Icon shown)"
            );
            case STRENGTH -> List.of(
                    "§7Upgrade Token",
                    "§aEffect: Strength " + roman + " while holding",
                    "§7Applies to: Weapons",
                    "§7Use: Apply to a weapon",
                    "§8(No particles • Icon shown)"
            );
            case FIRE_RESISTANCE -> List.of(
                    "§7Upgrade Token",
                    "§aEffect: Fire Resistance " + roman + " while equipped",
                    "§7Applies to: Armor",
                    "§7Use: Apply to any armor piece",
                    "§8(No particles • Icon shown)"
            );
            case HEALTH_BOOST -> List.of(
                    "§7Upgrade Token",
                    "§aEffect: Health Boost " + roman + " while equipped",
                    "§7Applies to: Armor",
                    "§7Use: Apply to any armor piece",
                    "§8(No particles • Icon shown)"
            );
            case NIGHT_VISION -> List.of(
                    "§7Upgrade Token",
                    "§aEffect: Night Vision " + roman + " while equipped",
                    "§7Applies to: Helmet",
                    "§7Use: Apply to a helmet",
                    "§8(No particles • Icon shown)"
            );
            case WATER_BREATHING -> List.of(
                    "§7Upgrade Token",
                    "§aEffect: Water Breathing " + roman + " while equipped",
                    "§7Applies to: Helmet",
                    "§7Use: Apply to a helmet",
                    "§8(No particles • Icon shown)"
            );
        };
    }

    private static int clampLevel(int lvl) {
        if (lvl < 1) return 1;
        if (lvl > 2) return 2;
        return lvl;
    }
}
