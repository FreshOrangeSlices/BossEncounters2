package com.orangeslices.bossencounters.raffle;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * Centralized PDC keys for the Raffle armor-effect system.
 * Keep all raffle-related keys here to avoid collisions and chaos.
 */
public final class RaffleKeys {

    private RaffleKeys() {}

    // Armor storage (PDC on the armor item)
    public static NamespacedKey ARMOR_EFFECTS;      // String, encoded list: id:lvl|id:lvl|id:lvl
    public static NamespacedKey ARMOR_HAS_CURSE;    // Byte (0/1)
    public static NamespacedKey ARMOR_SLOTS_USED;   // Integer (0-3)

    // Token identification (PDC on the raffle token item)
    public static NamespacedKey TOKEN_RAFFLE;       // Byte (0/1)

    /** Call once from onEnable() to initialize NamespacedKeys. */
    public static void init(Plugin plugin) {
        ARMOR_EFFECTS = new NamespacedKey(plugin, "raffle_armor_effects");
        ARMOR_HAS_CURSE = new NamespacedKey(plugin, "raffle_armor_has_curse");
        ARMOR_SLOTS_USED = new NamespacedKey(plugin, "raffle_armor_slots_used");

        TOKEN_RAFFLE = new NamespacedKey(plugin, "raffle_token");
    }
}

