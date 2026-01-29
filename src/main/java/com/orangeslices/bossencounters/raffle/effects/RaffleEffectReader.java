package com.orangeslices.bossencounters.raffle.effects;

import com.orangeslices.bossencounters.raffle.RaffleEffectId;
import com.orangeslices.bossencounters.raffle.RaffleKeys;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads raffle effects stored on armor (PDC) and returns a Map of effect -> level.
 *
 * Stored format (string): "ID:level,ID:level"
 * Example: "VITALITY:2,EMBER_WARD:1"
 *
 * NOTE:
 * - Curses are clamped to level 1 (never level)
 * - Levels are clamped to >= 1
 */
public final class RaffleEffectReader {

    private RaffleEffectReader() {}

    public static Map<RaffleEffectId, Integer> readFromItem(ItemStack item) {
        if (item == null) return Collections.emptyMap();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return Collections.emptyMap();
        return readFromPdc(meta.getPersistentDataContainer());
    }

    public static Map<RaffleEffectId, Integer> readFromPdc(PersistentDataContainer pdc) {
        if (pdc == null) return Collections.emptyMap();

        // Defensive init check (prevents silent null keys)
        RaffleKeys.validateInit();

        String raw = pdc.get(RaffleKeys.EFFECTS, PersistentDataType.STRING);
        if (raw == null || raw.trim().isEmpty()) return Collections.emptyMap();

        Map<RaffleEffectId, Integer> out = new LinkedHashMap<>();

        for (String part : raw.split(",")) {
            String token = part.trim();
            if (token.isEmpty()) continue;

            String[] kv = token.split(":");
            if (kv.length != 2) continue;

            RaffleEffectId id = RaffleEffectId.fromString(kv[0].trim());
            if (id == null) continue;

            int lvl;
            try {
                lvl = Integer.parseInt(kv[1].trim());
            } catch (NumberFormatException e) {
                lvl = 1;
            }

            if (lvl < 1) lvl = 1;
            if (id.isCurse()) lvl = 1;

            out.put(id, lvl);
        }

        return out;
    }

    /**
     * Utility for merging multiple armor pieces:
     * Keep the HIGHEST level per effect across all sources.
     */
    public static void mergeHighest(Map<RaffleEffectId, Integer> into, Map<RaffleEffectId, Integer> add) {
        if (into == null || add == null || add.isEmpty()) return;

        for (Map.Entry<RaffleEffectId, Integer> e : add.entrySet()) {
            RaffleEffectId id = e.getKey();
            if (id == null) continue;

            int lvl = (e.getValue() == null ? 1 : e.getValue());
            if (lvl < 1) lvl = 1;
            if (id.isCurse()) lvl = 1;

            int cur = into.getOrDefault(id, 0);
            if (lvl > cur) into.put(id, lvl);
        }
    }
}
