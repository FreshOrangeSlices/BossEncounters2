package com.orangeslices.bossencounters.raffle;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * PersistentDataContainer keys used by the raffle system.
 */
public final class RaffleKeys {

    private RaffleKeys() {}

    public static NamespacedKey EFFECTS;      // stored effects + levels
    public static NamespacedKey SLOT_COUNT;   // how many slots are used

    /**
     * Must be called once during plugin startup.
     */
    public static void init(Plugin plugin) {
        EFFECTS = new NamespacedKey(plugin, "raffle_effects");
        SLOT_COUNT = new NamespacedKey(plugin, "raffle_slots");
    }
}
