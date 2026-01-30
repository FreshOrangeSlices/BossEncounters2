package com.orangeslices.bossencounters.raffle;

import com.orangeslices.bossencounters.raffle.effects.RafflePotionTable;
import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;
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
 *
 * Slot Authority:
 * - When applying to a specific armor piece, ONLY roll effects compatible with that armor slot.
 * - Curses are allowed on any armor piece (slot-agnostic), unless you later decide otherwise.
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

        // Defensive init check (will throw if not initialized)
        RaffleKeys.validateInit();

        if (maxSlots <= 0) maxSlots = DEFAULT_MAX_SLOTS;

        ItemMeta meta = armor.getItemMeta();
        if (meta == null) {
            return ApplyResult.fail("Armor has no meta.");
        }

        EquipmentSlot targetSlot = armorSlot(armor.getType());
        if (targetSlot == null) {
            return ApplyResult.fail("Could not determine armor slot.");
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        Map<RaffleEffectId, Integer> effects = readEffects(pdc);
        int slotsUsed = readSlotsUsed(pdc, effects.size());

        boolean hasCurse = hasCurse(effects);

        RaffleDebug.log("---- Raffle Apply Start ----");
        RaffleDebug.log("Item=" + armor.getType().name()
                + " slot=" + targetSlot.name()
                + " slotsUsed=" + slotsUsed + "/" + maxSlots
                + " effects=" + effectsToDebugString(effects)
                + " hasCurse=" + hasCurse);

        if (slotsUsed >= maxSlots) {
            RaffleDebug.log("FAIL: max slots reached.");
            return ApplyResult.fail("Max add-ons reached.");
        }

        if (pool.isEmpty()) {
            RaffleDebug.log("FAIL: pool is empty (check config raffle.effects).");
            return ApplyResult.fail("Raffle pool is empty.");
        }

        RaffleEffectId rolled = rollForSlot(hasCurse, targetSlot);
        if (rolled == null) {
            RaffleDebug.log("FAIL: no valid roll for slot=" + targetSlot.name()
                    + " (likely misconfigured pool / curse-locked with no valid GOOD effects).");
            return ApplyResult.fail("No valid effects to roll for this armor slot.");
        }

        int newLevel = 1;

        if (rolled.isCurse()) {
            // Should only happen if hasCurse == false, but keep it safe
            if (hasCurse) {
                RaffleDebug.log("FAIL: attempted to roll CURSE while curse-locked (guard).");
                return ApplyResult.fail("Armor is curse-locked (no more curses).");
            }
            effects.put(rolled, 1);
            newLevel = 1;
        } else {
            int current = effects.getOrDefault(rolled, 0);
            newLevel = current + 1;
            effects.put(rolled, newLevel);
        }

        // Consumes 1 slot every time
        int beforeSlots = slotsUsed;
        slotsUsed++;

        writeEffects(pdc, effects);
        pdc.set(RaffleKeys.SLOT_COUNT, PersistentDataType.INTEGER, slotsUsed);

        armor.setItemMeta(meta);

        RaffleDebug.log("ROLLED: " + rolled.name()
                + " type=" + (rolled.isCurse() ? "CURSE" : "GOOD")
                + " resultLevel=" + newLevel
                + " slots " + beforeSlots + "->" + slotsUsed
                + " effectsNow=" + effectsToDebugString(effects));
        RaffleDebug.log("---- Raffle Apply End ----");

        return ApplyResult.success(rolled, newLevel, slotsUsed, maxSlots);
    }

    /**
     * Slot-authoritative roll:
     * - If curse-locked => only GOOD effects compatible with this slot
     * - If not curse-locked => GOOD effects compatible with slot + (any curses)
     */
    private RaffleEffectId rollForSlot(boolean hasCurse, EquipmentSlot targetSlot) {
        List<RaffleEffectId> candidates = new ArrayList<>();

        for (RaffleEffectId id : pool.snapshot()) {
            if (id == null) continue;

            if (hasCurse && id.isCurse()) {
                // curse-locked: no more curses
                continue;
            }

            if (id.isCurse()) {
                // Curses: allowed on any armor piece (slot-agnostic)
                candidates.add(id);
                continue;
            }

            // Good effects: must be compatible with the armor slot being modified
            if (isGoodEffectCompatibleWithSlot(id, targetSlot)) {
                candidates.add(id);
            }
        }

        if (candidates.isEmpty()) return null;

        RaffleEffectId pick = candidates.get(RNG.nextInt(candidates.size()));
        RaffleDebug.log("ROLL MODE: slot=" + targetSlot.name()
                + (hasCurse ? " GOOD-only" : " slot-filtered")
                + " -> " + pick.name());
        return pick;
    }

    /**
     * Uses RafflePotionTable slot rules as the source of truth for potion-based GOOD effects.
     * If an effect isn't in the potion table (future custom GOOD), default to allowing it anywhere.
     */
    private boolean isGoodEffectCompatibleWithSlot(RaffleEffectId id, EquipmentSlot slot) {
        if (id == null || slot == null) return false;

        // Find potion entry (if present)
        RafflePotionTable.Entry entry = null;
        for (RafflePotionTable.Entry e : RafflePotionTable.entries()) {
            if (e.id == id) { entry = e; break; }
        }

        // Not a potion-table effect (ex: future custom GOOD): allow anywhere for now
        if (entry == null) return true;

        return switch (entry.slotRule) {
            case ANY_ARMOR -> true;
            case HELMET_ONLY -> slot == EquipmentSlot.HEAD;
            case CHESTPLATE_ONLY -> slot == EquipmentSlot.CHEST;
            case LEGGINGS_ONLY -> slot == EquipmentSlot.LEGS;
            case BOOTS_ONLY -> slot == EquipmentSlot.FEET;
        };
    }

    private static EquipmentSlot armorSlot(Material mat) {
        if (mat == null) return null;
        String n = mat.name();
        if (n.endsWith("_HELMET")) return EquipmentSlot.HEAD;
        if (n.endsWith("_CHESTPLATE")) return EquipmentSlot.CHEST;
        if (n.endsWith("_LEGGINGS")) return EquipmentSlot.LEGS;
        if (n.endsWith("_BOOTS")) return EquipmentSlot.FEET;
        return null;
    }

    private static boolean hasCurse(Map<RaffleEffectId, Integer> effects) {
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

        if (raw == null || raw.isBlank()) return out;

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

    private static boolean isArmor(Material m) {
        if (m == null) return false;
        String n = m.name();
        return n.endsWith("_HELMET")
                || n.endsWith("_CHESTPLATE")
                || n.endsWith("_LEGGINGS")
                || n.endsWith("_BOOTS");
    }

    private static String effectsToDebugString(Map<RaffleEffectId, Integer> effects) {
        if (effects == null || effects.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<RaffleEffectId, Integer> e : effects.entrySet()) {
            if (!first) sb.append(", ");
            sb.append(e.getKey().name()).append(":").append(e.getValue());
            first = false;
        }
        sb.append("}");
        return sb.toString();
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
