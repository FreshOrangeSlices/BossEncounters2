package com.orangeslices.bossencounters.raffle;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Centralized PDC keys for the Raffle armor-effect system.
 * Keys are lazily initialized to avoid needing BossEncountersPlugin edits early.
 */
public final class RaffleKeys {

    private RaffleKeys() {}

    private static boolean initialized = false;

    // Armor storage (PDC on the armor item)
    public static NamespacedKey ARMOR_EFFECTS;      // String: id:lvl|id:lvl|id:lvl
    public static NamespacedKey ARMOR_HAS_CURSE;    // Byte (0/1)
    public static NamespacedKey ARMOR_SLOTS_USED;   // Integer (0-3)

    // Token identification (PDC on the raffle token item)
    public static NamespacedKey TOKEN_RAFFLE;       // Byte (0/1)

    /** Safe to call multiple times. */
    public static void init(Plugin plugin) {
        if (initialized) return;
        initialized = true;

        ARMOR_EFFECTS = new NamespacedKey(plugin, "raffle_armor_effects");
        ARMOR_HAS_CURSE = new NamespacedKey(plugin, "raffle_armor_has_curse");
        ARMOR_SLOTS_USED = new NamespacedKey(plugin, "raffle_armor_slots_used");

        TOKEN_RAFFLE = new NamespacedKey(plugin, "raffle_token");
    }

    /** Convenience init that avoids touching your main class for now. */
    public static void initAuto() {
        Plugin plugin = JavaPlugin.getProvidingPlugin(RaffleKeys.class);
        init(plugin);
    }
}
