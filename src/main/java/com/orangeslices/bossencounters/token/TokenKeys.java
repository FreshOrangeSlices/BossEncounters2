package com.orangeslices.bossencounters.token;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * Centralized PDC keys for BossEncounters.
 *
 * Why:
 * - Prevents typos / mismatched strings across listeners
 * - Makes refactors safe (change key once)
 * - Gives a single “source of truth” for what metadata exists
 */
public final class TokenKeys {

    private final Plugin plugin;

    public TokenKeys(Plugin plugin) {
        this.plugin = plugin;
    }

    /* -------------------------
       Core boss key
       ------------------------- */
    public NamespacedKey isBoss() {
        return new NamespacedKey(plugin, "is_boss");
    }

    /* -------------------------
       Token / addon markers
       ------------------------- */

    /** Marks an item as an AddOn token item (the consumable kit/token itself). */
    public NamespacedKey isTokenItem() {
        return new NamespacedKey(plugin, "is_token_item");
    }

    /** Stores which token type this token item represents (string). */
    public NamespacedKey tokenType() {
        return new NamespacedKey(plugin, "token_type");
    }

    /** Optional: token tier/rarity (string/int depending on your usage). */
    public NamespacedKey tokenTier() {
        return new NamespacedKey(plugin, "token_tier");
    }

    /* -------------------------
       Applied effects on gear (PDC stored on equipment)
       ------------------------- */

    /** Sharpening kit level applied to a weapon/tool. */
    public NamespacedKey sharpenLevel() {
        return new NamespacedKey(plugin, "sharpen_level");
    }

    /** Mark kit level applied to gear (if you use one). */
    public NamespacedKey markLevel() {
        return new NamespacedKey(plugin, "mark_level");
    }

    /* -------------------------
       Potion addon levels applied to armor pieces
       ------------------------- */

    public NamespacedKey hasteLevel() {
        return new NamespacedKey(plugin, "haste_level");
    }

    public NamespacedKey strengthLevel() {
        return new NamespacedKey(plugin, "strength_level");
    }

    public NamespacedKey speedLevel() {
        return new NamespacedKey(plugin, "speed_level");
    }

    public NamespacedKey fireResLevel() {
        return new NamespacedKey(plugin, "fire_res_level");
    }

    public NamespacedKey healthBoostLevel() {
        return new NamespacedKey(plugin, "health_boost_level");
    }

    public NamespacedKey nightVisionLevel() {
        return new NamespacedKey(plugin, "night_vision_level");
    }

    /* -------------------------
       Generic helpers
       ------------------------- */

    /**
     * For future use: a generic per-slot key prefix approach if we unify storage later.
     * Keeping this here so we have one place to evolve key strategy.
     */
    public NamespacedKey custom(String key) {
        return new NamespacedKey(plugin, key);
    }
}
