package com.orangeslices.bossencounters.raffle;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Raffle rules:
 * - Armor only
 * - Max 3 slots
 * - Unified equal pool
 * - Max 1 curse per item
 * - After curse → GOOD-only rolls
 * - GOOD duplicates level up
 * - Curses never level
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
            return ApplyResult.fail("No armor item.");
        }
        if (!isArmor(armor.getType())) {
            return ApplyResult.fail("Item is not armor.");
        }
        if (RaffleKeys.EFFECTS == null || RaffleKeys.SLOT_COUNT == null) {
            return ApplyResult.fail("RaffleKeys not initialized.");
        }

        if (maxSlots <= 0) maxSlots = DEFAULT_MAX_SLOTS;

        ItemMeta meta = armor.getItemMeta();
        if (meta == null) {
            return ApplyResult.fail("Armor has no meta.");
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        Map<RaffleEffectId, Integer> effects = readEffects(pdc);
        int slotsUsed = readSlotsUsed(pdc, effects.size());

        if (slotsUsed >= maxSlots) {
            return ApplyResult.fail("Max add-ons reached.");
        }

        boolean hasCurse = hasCurse(effects);

        if (pool.isEmpty()) {
            return ApplyResult.fail("Raffle pool is empty.");
        }

        RaffleEffectId rolled = roll(hasCurse);
        if (rolled == null) {
            return ApplyResult.fail("No valid effects to roll.");
        }

        int newLevel = 1;

        if (rolled.isCurse()) {
            effects.put(rolled, 1);
        } else {
            int current = effects.getOrDefault(rolled, 0);
            newLevel = current + 1;
            effects.put(rolled, newLevel);
        }

        slotsUsed++;

        writeEffects(pdc, effects);
        pdc.set(RaffleKeys.SLOT_COUNT, PersistentDataType.INTEGER, slotsUsed);

        armor.setItemMeta(meta);

        return ApplyResult.success(rolled, newLevel, slotsUsed, maxSlots);
    }

    private RaffleEffectId roll(boolean hasCurse) {
        if (!hasCurse) {
            return pool.roll();
        }

        List<RaffleEffectId> goods = new ArrayList<>();
        for (RaffleEffectId id : pool.snapshot()) {
            if (id.isGood()) goods.add(id);
        }

        if (goods.isEmpty()) return null;
        return goods.get(RNG.nextInt(goods.size()));
    }

    private static boolean hasCurse(Map<RaffleEffectId, Integer> effects) {
        for (RaffleEffectId id : effects.keySet()) {
            if (id.isCurse()) return true;
        }
        return false;
    }

    private static int readSlotsUsed(PersistentDataContainer pdc, int fallback) {
        Integer stored = pdc.get(RaffleKeys.SLOT_COUNT, PersistentDataType.INTEGER);
        return stored != null ? stored : fallback;
    }

    private static Map<RaffleEffectId, Integer> readEffects(PersistentDataContainer pdc) {
        String raw = pdc.get(RaffleKeys.EFFECTS, PersistentDataType.STRING);
        Map<RaffleEffectId, Integer> out = new LinkedHashMap<>();

        if (raw == null || raw.isBlank()) return out;

        for (String part : raw.split(",")) {
            String[] kv = part.split(":");
            if (kv.length != 2) continue;

            RaffleEffectId id = RaffleEffectId.fromString(kv[0]);
            if (id == null) continue;

            int lvl;
            try {
                lvl = Integer.parseInt(kv[1]);
            } catch (NumberFormatException e) {
                lvl = 1;
            }

            if (id.isCurse()) lvl = 1;
            if (lvl < 1) lvl = 1;

            out.put(id, lvl);
        }
        return out;
    }

    private static void writeEffects(PersistentDataContainer pdc, Map<RaffleEffectId, Integer> effects) {
        if (effects.isEmpty()) {
            pdc.remove(RaffleKeys.EFFECTS);
            return;
        }

        StringBuilder sb = new StringBuilder();
        boolean first = true;

        for (var e : effects.entrySet()) {
            if (!first) sb.append(",");
            sb.append(e.getKey().name()).append(":").append(e.getValue());
            first = false;
        }

        pdc.set(RaffleKeys.EFFECTS, PersistentDataType.STRING, sb.toString());
    }

    private static boolean isArmor(Material m) {
        String n = m.name();
        return n.endsWith("_HELMET")
                || n.endsWith("_CHESTPLATE")
                || n.endsWith("_LEGGINGS")
                || n.endsWith("_BOOTS");
    }

    // ---------------- result ----------------

    public static final class ApplyResult {
        public final boolean success;
        public final String message;
        public final RaffleEffectId effect;
        public final int level, slotsUsed, maxSlots;

        private ApplyResult(boolean s, String m, RaffleEffectId e, int l, int u, int max) {
            success = s;
            message = m;
            effect = e;
            level = l;
            slotsUsed = u;
            maxSlots = max;
        }

        public static ApplyResult success(RaffleEffectId id, int lvl, int used, int max) {
            return new ApplyResult(true, "Applied " + id.name(), id, lvl, used, max);
        }

        public static ApplyResult fail(String msg) {
            return new ApplyResult(false, msg, null, 0, 0, 0);
        }
    }
}
