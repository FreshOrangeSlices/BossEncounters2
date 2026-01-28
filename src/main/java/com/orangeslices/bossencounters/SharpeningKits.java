package com.orangeslices.bossencounters;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class SharpeningKits {

    private SharpeningKits() {}

    /**
     * TRIAL PHASE (Sharpening-only):
     * We are NOT using crafting recipes or essences anymore.
     *
     * If your plugin still calls SharpeningKits.registerRecipes(plugin) in onEnable(),
     * you can either remove that call or keep this as a harmless no-op.
     */
    public static void registerRecipes(BossEncountersPlugin plugin) {
        // No-op by design (essences + crafting removed).
    }

    /**
     * Creates the Sharpening Kit token.
     *
     * Drop-only for now (from BossDropListener).
     * Application method will be implemented separately (TokenApplyListener / inventory method, etc.).
     *
     * Tags:
     * - token_type = "SHARPENING"
     * - token_level = 1|2
     * - sharpen_kit_level (legacy) = 1|2  (kept so older listeners don't break)
     */
    public static ItemStack makeSharpeningKit(BossEncountersPlugin plugin, int level) {
        level = Math.min(2, Math.max(1, level));

        // Vanilla-feeling token material (trial-safe swap from PAPER)
        ItemStack item = new ItemStack(Material.AMETHYST_SHARD, 1);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(level == 1 ? "§fSharpening Kit I" : "§bSharpening Kit II");

        // Lore = player-facing instructions (we keep it generic until apply method is finalized)
        meta.setLore(List.of(
                "§7Upgrade Token",
                level == 1 ? "§aEffect: +1 bonus damage on hit" : "§aEffect: +2 bonus damage on hit",
                "§7Applies to: Swords, Axes, Spears, Tridents",
                "§7Use: Apply to a weapon",
                "§8Does not overwrite enchants"
        ));

        // New universal token tags (future-proof)
        NamespacedKey tokenTypeKey = new NamespacedKey(plugin, "token_type");
        NamespacedKey tokenLevelKey = new NamespacedKey(plugin, "token_level");
        meta.getPersistentDataContainer().set(tokenTypeKey, PersistentDataType.STRING, "SHARPENING");
        meta.getPersistentDataContainer().set(tokenLevelKey, PersistentDataType.INTEGER, level);

        // Legacy tag (kept for compatibility with any existing sharpening listeners)
        NamespacedKey legacyKitLevelKey = new NamespacedKey(plugin, "sharpen_kit_level");
        meta.getPersistentDataContainer().set(legacyKitLevelKey, PersistentDataType.INTEGER, level);

        item.setItemMeta(meta);
        return item;
    }
}
