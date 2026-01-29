package com.orangeslices.bossencounters.raffle;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Core rules engine:
 * - armor only
 * - max 3 slots
 * - unified pool, equal chance
 * - max 1 curse per armor; after curse -> GOOD only
 * - GOOD duplicates level up (+1) and still consume a slot
 * - CURSES never level
 *
 * This class only stores results. It does not trigger effects (equip system comes later).
 */
public final class RaffleService {

    public static final int DEFAULT_MAX_SLOTS = 3;

    private static final Random RNG = new Random();

    private final RafflePool pool;

    public RaffleService(RafflePool pool) {
        this.pool = pool;
    }

    public ApplyResult applyToArmor(ItemStack armor, int maxSlots) {
        if (armor == null || armor.getType() == Material.AIR) {
            return ApplyResult.fail("No armor item found.");
        }
        if (!isArmor(armor.getType())) {
            return ApplyResult.fail("That item is not armor.");
        }
        if (RaffleKeys.EFFECTS == null || RaffleKeys.SLOT_COUNT == null) {
            return ApplyResult.fail("RaffleKeys not initialized (call RaffleKeys.init(plugin) on startup).");
        }

        // Clamp just in case caller passes weird values
        if (maxSlots <= 0) maxSlots = DEFAULT_MAX_SLOTS;

        ItemMeta meta = armor.getItemMeta();
        if (meta == null) {
            return ApplyResult.fail("Armor has no meta.");
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // Read stored effects
        Map<RaffleEffectId, Integer> effects = readEffects(pdc);
        int slotsUsed = readSlotsUsed(pdc, effects.size());

        if (slotsUsed >= maxSlots) {
            return ApplyResult.fail("This armor already has the max add-ons (" + maxSlots + ").");
        }

        // Determine curse-lock status
        boolean hasCurse = hasAnyCurse(effects);

        // Ensure pool is loaded
        if (pool.isEmpty()) {
            return ApplyResult.fail("Raffle pool is empty. Check config.yml -> raffle.effects");
        }

        // Roll
        RaffleEffectId rolled = rollWithCurseLock(hasCurse);
        if (rolled == null) {
            return ApplyResult.fail("No valid effects available to roll (check your raffle.effects list).");
        }

        // Apply rules
        int newLevel = 1;

        if (rolled.isCurse()) {
            if (hasCurse) {
                // Should never happen because of curse-lock roll filtering, but guard anyway
                return ApplyResult.fail("Armor is curse-locked (no more curses).");
            }
            // Curses never level; always level 1
            effects.put(rolled, 1);
        } else {
            // GOOD: duplicates level up (+1)
            int current = effects.getOrDefault(rolled, 0);
            newLevel = Math.max(1, current + 1);
            effects.put(rolled, newLevel);
        }

        // Consumes exactly 1 slot per apply, regardless of duplicate/level-up
        slotsUsed += 1;

        // Write back
        writeEffects(pdc, effects);
        pdc.set(RaffleKeys.SLOT_COUNT, PersistentDataType.INTEGER, slotsUsed);

        armor.setItemMeta(meta);

        return ApplyResult.success(rolled, newLevel, slotsUsed, maxSlots);
    }

    private RaffleEffectId rollWithCurseLock(boolean hasCurse) {
        if (!hasCurse) {
            return pool.roll();
        }

        // curse-locked: roll from GOOD-only subset of configured pool
        List<RaffleEffectId> goods = new ArrayList<>();
        for (RaffleEffectId id : pool.snapshot()) {
            if (id != null && id.isGood()) goods.add(id);
        }
        if (goods.isEmpty()) return null;

        int idx = RNG.nextInt(goods.size());
        return goods.get(idx);
    }

    private static boolean hasAnyCurse(Map<RaffleEffectId, Integer> effects) {
        for (RaffleEffectId id : effects.keySet()) {
            if (id != null && id.isCurse()) return true;
        }
        return false;
    }

    private static int readSlotsUsed(PersistentDataContainer pdc, int fallback) {
        Integer stored = pdc.get(RaffleKeys.SLOT_COUNT, PersistentDataType.INTEGER);
        return stored != null ? stored : fallback;
    }

    /**
     * Stored format: "ID:level,ID:level"
     */
    private static Map<RaffleEffectId, Integer> readEffects(PersistentDataContainer pdc) {
        String raw = pdc.get(RaffleKeys.EFFECTS, PersistentDataType.STRING);
        Map<RaffleEffectId, Integer> out = new LinkedHashMap<>();
        if (raw == null || raw.trim().isEmpty()) return out;

        String[] parts = raw.split(",");
        for (String part : parts) {
            String token = part.trim();
            if (token.isEmpty()) continue;

            String[] kv = token.split(":");
            if (kv.length != 2) continue;

            RaffleEffectId id = RaffleEffectId.fromString(kv[0]);
            if (id == null) continue;

            int lvl;
            try {
                lvl = Integer.parseInt(kv[1].trim());
            } catch (NumberFormatException e) {
                lvl = 1;
            }

            // Curses never level; clamp
            if (id.isCurse()) lvl = 1;
            if (lvl < 1) lvl = 1;

            out.put(id, lvl);
        }
        return out;
    }

    private static void writeEffects(PersistentDataContainer pdc, Map<RaffleEffectId, Integer> effects) {
        if (effects == null || effects.isEmpty()) {
            pdc.remove(RaffleKeys.EFFECTS);
            return;
        }

        StringBuilder sb = new StringBuilder();
        boolean first = true;

        for (Map.Entry<RaffleEffectId, Integer> e : effects.entrySet()) {
            if (e.getKey() == null) continue;

            int lvl = (e.getValue() == null ? 1 : e.getValue());
            if (e.getKey().isCurse()) lvl = 1;
            if (lvl < 1) lvl = 1;

            if (!first) sb.append(",");
            sb.append(e.getKey().name()).append(":").append(lvl);
            first = false;
        }

        pdc.set(RaffleKeys.EFFECTS, PersistentDataType.STRING, sb.toString());
    }

    private static boolean isArmor(Material mat) {
        if (mat == null) return false;
        String name = mat.name();
        return name.endsWith("_HELMET")
                || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS");
    }

    // ---------------- Result object ----------------

    public static final class ApplyResult {
        public final boolean success;
        public final String message;

        public final RaffleEffectId effectId;
        public final int level;
        public final int slotsUsed;
        public final int maxSlots;

        private ApplyResult(boolean success, String message,
                            RaffleEffectId effectId, int level,
                            int slotsUsed, int maxSlots) {
            this.success = success;
            this.message = message;
            this.effectId = effectId;
            this.level = level;
            this.slotsUsed = slotsUsed;
            this.maxSlots = maxSlots;
        }

        public static ApplyResult success(RaffleEffectId id, int level, int slotsUsed, int maxSlots) {
            return new ApplyResult(true, "Applied: " + id.name() + " (Lvl " + level + ")",
                    id, level, slotsUsed, maxSlots);
        }

        public static ApplyResult fail(String msg) {
            return new ApplyResult(false, msg, null, 0, 0, 0);
        }
    }
}
