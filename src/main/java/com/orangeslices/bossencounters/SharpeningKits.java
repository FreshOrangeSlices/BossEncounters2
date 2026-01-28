package com.orangeslices.bossencounters;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class SharpeningKits {

    private SharpeningKits() {}

    /**
     * Call this once in onEnable().
     */
    public static void registerRecipes(BossEncountersPlugin plugin) {
        // --- Build the exact essence choices (must match your dropped essences) ---
        // Essence I = CHARCOAL with PDC essence_tier = 1
        ItemStack essenceI = makeEssenceForRecipe(plugin, 1);

        // --- Build kit outputs ---
        ItemStack kit1 = makeSharpeningKit(plugin, 1);
        ItemStack kit2 = makeSharpeningKit(plugin, 2);

        // -------------------------
        // Recipe: Sharpening Kit I
        // 2 iron + 1 Essence I + 1 grindstone
        // -------------------------
        NamespacedKey key1 = new NamespacedKey(plugin, "sharpening_kit_1");
        ShapelessRecipe r1 = new ShapelessRecipe(key1, kit1);

        r1.addIngredient(Material.IRON_INGOT);
        r1.addIngredient(Material.IRON_INGOT);
        r1.addIngredient(Material.GRINDSTONE);

        // ExactChoice ensures ONLY your tagged essence counts (not vanilla charcoal)
        r1.addIngredient(new RecipeChoice.ExactChoice(essenceI));

        // -------------------------
        // Recipe: Sharpening Kit II
        // 2 diamond + 1 Essence I + 1 grindstone
        // -------------------------
        NamespacedKey key2 = new NamespacedKey(plugin, "sharpening_kit_2");
        ShapelessRecipe r2 = new ShapelessRecipe(key2, kit2);

        r2.addIngredient(Material.DIAMOND);
        r2.addIngredient(Material.DIAMOND);
        r2.addIngredient(Material.GRINDSTONE);
        r2.addIngredient(new RecipeChoice.ExactChoice(essenceI));

        // Register (remove old ones if hot-reloading causes duplicates)
        Bukkit.removeRecipe(key1);
        Bukkit.removeRecipe(key2);

        Bukkit.addRecipe(r1);
        Bukkit.addRecipe(r2);
    }

    /**
     * Creates the sharpening kit item.
     * IMPORTANT: This must match the key used by SharpeningSmithListener:
     * NamespacedKey(plugin, "sharpen_kit_level")
     */
    public static ItemStack makeSharpeningKit(BossEncountersPlugin plugin, int level) {
        level = Math.min(2, Math.max(1, level));

        // Use a neutral base item so it’s clearly “custom”
        ItemStack item = new ItemStack(Material.PAPER, 1);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(level == 1 ? "§fSharpening Kit I" : "§bSharpening Kit II");

        meta.setLore(List.of(
                "§7Use in a Smithing Table",
                level == 1 ? "§aApplies +1 Attack Damage" : "§aApplies +2 Attack Damage",
                "§8Works on enchanted weapons too"
        ));

        // Tag kit level so the smithing listener can detect it
        NamespacedKey kitLevelKey = new NamespacedKey(plugin, "sharpen_kit_level");
        meta.getPersistentDataContainer().set(kitLevelKey, PersistentDataType.INTEGER, level);

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Builds an Essence itemstack that matches your drop format exactly enough for ExactChoice.
     * Your BossDropListener tags essences with:
     * NamespacedKey(plugin, "essence_tier") as INTEGER
     *
     * We only need the metadata + PDC to match; material must match too.
     */
    private static ItemStack makeEssenceForRecipe(BossEncountersPlugin plugin, int tier) {
        Material mat = switch (tier) {
            case 1 -> Material.CHARCOAL;        // Essence I
            case 2 -> Material.ECHO_SHARD;      // Essence II (not used in these recipes yet)
            case 3 -> Material.AMETHYST_SHARD;  // Essence III (not used in these recipes yet)
            default -> Material.CHARCOAL;
        };

        ItemStack item = new ItemStack(mat, 1);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(switch (tier) {
            case 1 -> "§7Essence I";
            case 2 -> "§bEssence II";
            case 3 -> "§dEssence III";
            default -> "§7Essence I";
        });

        meta.setLore(List.of(
                "§7Boss drop currency",
                "§8Condense 9 → 1 in crafting"
        ));

        NamespacedKey essenceKey = new NamespacedKey(plugin, "essence_tier");
        meta.getPersistentDataContainer().set(essenceKey, PersistentDataType.INTEGER, tier);

        item.setItemMeta(meta);
        return item;
    }
}
