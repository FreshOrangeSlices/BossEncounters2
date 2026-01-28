package com.orangeslices.bossencounters.raffle;

import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public final class RaffleService {

    // PDC keys (self-contained, won’t rely on your old keys)
    private static NamespacedKey KEY_SLOTS_USED(JavaPlugin plugin) { return new NamespacedKey(plugin, "raffle_slots_used"); }
    private static NamespacedKey KEY_GOOD_LEVELS(JavaPlugin plugin) { return new NamespacedKey(plugin, "raffle_good_levels"); } // "health_boost=2;fire_res=1"
    private static NamespacedKey KEY_CURSE(JavaPlugin plugin) { return new NamespacedKey(plugin, "raffle_curse"); } // "terror" etc

    private RaffleService() {}

    public static RaffleApplyResult applyToArmor(JavaPlugin plugin, ItemStack armorItem) {
        if (plugin == null) throw new IllegalArgumentException("plugin is null");
        if (armorItem == null || armorItem.getType().isAir()) {
            return RaffleApplyResult.fail("No armor item found.", 0, getMaxSlots(plugin));
        }

        ItemMeta meta = armorItem.getItemMeta();
        if (meta == null) return RaffleApplyResult.fail("That item can’t hold add-ons.", 0, getMaxSlots(plugin));

        FileConfiguration cfg = plugin.getConfig();
        int maxSlots = getMaxSlots(plugin);

        List<String> effects = normalizeList(cfg.getStringList("raffle.effects"));
        List<String> curses = normalizeList(cfg.getStringList("raffle.curses"));

        if (effects.isEmpty()) {
            return RaffleApplyResult.fail("Raffle pool is empty. Check config.yml -> raffle.effects", getUsedSlots(plugin, meta), maxSlots);
        }

        // Build pools
        Set<String> curseSet = new HashSet<>(curses);
        List<String> goodPool = effects.stream().filter(id -> !curseSet.contains(id)).collect(Collectors.toList());
        List<String> unifiedPool = new ArrayList<>(effects);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        int usedSlots = getUsedSlots(plugin, meta);
        if (usedSlots >= maxSlots) {
            return RaffleApplyResult.fail("This armor is out of add-on slots.", usedSlots, maxSlots);
        }

        String existingCurse = pdc.get(KEY_CURSE(plugin), PersistentDataType.STRING);
        boolean hasCurse = existingCurse != null && !existingCurse.isBlank();

        // If you already have a curse, future rolls are GOOD only
        List<String> rollPool = hasCurse ? goodPool : unifiedPool;

        if (rollPool.isEmpty()) {
            return RaffleApplyResult.fail("No valid effects to roll (pool empty after filters).", usedSlots, maxSlots);
        }

        String rolled = rollPool.get(ThreadLocalRandom.current().nextInt(rollPool.size()));

        boolean rolledIsCurse = curseSet.contains(rolled);

        // If rolled a curse but you already have one, force GOOD roll
        if (rolledIsCurse && hasCurse) {
            if (goodPool.isEmpty()) {
                return RaffleApplyResult.fail("No GOOD effects available after curse lock.", usedSlots, maxSlots);
            }
            rolled = goodPool.get(ThreadLocalRandom.current().nextInt(goodPool.size()));
            rolledIsCurse = false;
        }

        // Apply: slots ALWAYS increase by 1 on success
        usedSlots++;

        if (rolledIsCurse) {
            // Max 1 curse per armor
            if (hasCurse) {
                return RaffleApplyResult.fail("This armor already bears a curse.", usedSlots - 1, maxSlots);
            }
            pdc.set(KEY_CURSE(plugin), PersistentDataType.STRING, rolled);
            pdc.set(KEY_SLOTS_USED(plugin), PersistentDataType.INTEGER, usedSlots);

            armorItem.setItemMeta(meta);
            return RaffleApplyResult.ok(rolled, true, usedSlots, maxSlots);
        }

        // GOOD effect: duplicates level up (but still consumes slot)
        Map<String, Integer> levels = readGoodLevels(plugin, pdc);
        int newLevel = levels.getOrDefault(rolled, 0) + 1;
        levels.put(rolled, newLevel);

        writeGoodLevels(plugin, pdc, levels);
        pdc.set(KEY_SLOTS_USED(plugin), PersistentDataType.INTEGER, usedSlots);

        armorItem.setItemMeta(meta);
        return RaffleApplyResult.ok(rolled, false, usedSlots, maxSlots);
    }

    private static int getMaxSlots(JavaPlugin plugin) {
        return plugin.getConfig().getInt("raffle.max_slots_per_armor", 3);
    }

    private static int getUsedSlots(JavaPlugin plugin, ItemMeta meta) {
        Integer val = meta.getPersistentDataContainer().get(KEY_SLOTS_USED(plugin), PersistentDataType.INTEGER);
        return val == null ? 0 : Math.max(0, val);
    }

    private static List<String> normalizeList(List<String> raw) {
        if (raw == null) return Collections.emptyList();
        return raw.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase) // your config uses ids like "health_boost"
                .collect(Collectors.toList());
    }

    private static Map<String, Integer> readGoodLevels(JavaPlugin plugin, PersistentDataContainer pdc) {
        String encoded = pdc.get(KEY_GOOD_LEVELS(plugin), PersistentDataType.STRING);
        Map<String, Integer> out = new HashMap<>();
        if (encoded == null || encoded.isBlank()) return out;

        String[] pairs = encoded.split(";");
        for (String pair : pairs) {
            String p = pair.trim();
            if (p.isEmpty() || !p.contains("=")) continue;
            String[] kv = p.split("=", 2);
            String key = kv[0].trim().toLowerCase();
            String val = kv[1].trim();
            try {
                int lvl = Integer.parseInt(val);
                if (!key.isEmpty() && lvl > 0) out.put(key, lvl);
            } catch (NumberFormatException ignored) {}
        }
        return out;
    }

    private static void writeGoodLevels(JavaPlugin plugin, PersistentDataContainer pdc, Map<String, Integer> levels) {
        // stable encoding
        String encoded = levels.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey().toLowerCase() + "=" + Math.max(1, e.getValue()))
                .collect(Collectors.joining(";"));
        if (encoded.isBlank()) {
            pdc.remove(KEY_GOOD_LEVELS(plugin));
        } else {
            pdc.set(KEY_GOOD_LEVELS(plugin), PersistentDataType.STRING, encoded);
        }
    }
}
