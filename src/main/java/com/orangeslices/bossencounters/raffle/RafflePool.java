package com.orangeslices.bossencounters.raffle;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Loads the unified raffle effect pool from config.
 * Pool contains BOTH good and curse IDs (equal chance unless later weighted).
 */
public final class RafflePool {

    private static List<String> cachedPool = Collections.emptyList();

    private RafflePool() {}

    public static void reloadFromConfig() {
        Plugin plugin = JavaPlugin.getProvidingPlugin(RafflePool.class);
        FileConfiguration cfg = plugin.getConfig();

        List<String> list = cfg.getStringList("raffle.effects");
        List<String> cleaned = new ArrayList<>();
        for (String id : list) {
            if (id == null) continue;
            String trimmed = id.trim();
            if (!trimmed.isEmpty()) cleaned.add(trimmed.toLowerCase());
        }

        cachedPool = Collections.unmodifiableList(cleaned);
    }

    public static List<String> getPool() {
        return cachedPool;
    }

    public static boolean isEmpty() {
        return cachedPool.isEmpty();
    }
}
