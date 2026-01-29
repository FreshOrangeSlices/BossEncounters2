package com.orangeslices.bossencounters.raffle;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * PersistentDataContainer keys used by the raffle system.
 * Must be initialized once on plugin startup.
 */
public final class RaffleKeys {

    private RaffleKeys() {}

    public static NamespacedKey EFFECTS;     // stored effects + levels
    public static NamespacedKey SLOT_COUNT;  // how many slots are used

    /**
     * Must be called once during plugin startup.
     */
    public static void init(Plugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null when initializing RaffleKeys.");
        }

        EFFECTS = new NamespacedKey(plugin, "raffle_effects");
        SLOT_COUNT = new NamespacedKey(plugin, "raffle_slots");
    }

    /**
     * Defensive check to prevent silent null usage.
     */
    public static void validateInit() {
        if (EFFECTS == null || SLOT_COUNT == null) {
            throw new IllegalStateException(
                    "RaffleKeys not initialized. Call RaffleKeys.init(plugin) in onEnable()."
            );
        }
    }
}
