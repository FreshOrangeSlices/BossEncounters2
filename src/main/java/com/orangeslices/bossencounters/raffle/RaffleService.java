package com.orangeslices.bossencounters.raffle;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * Implements the OFFICIAL RULES DOC:
 * - Armor only
 * - 3 total slots max
 * - Each apply consumes 1 slot
 * - Unified pool (good + curse), equal chance
 * - If armor already has any curse -> only GOOD can roll afterward
 * - GOOD duplicates level up (+1)
 * - CURSES never level
 * - At most 1 curse per armor (enforced by pool filtering once cursed)
 */
public final class RaffleService {

    private RaffleService() {}

    /** Result info for messaging/debug later. */
    public static final class ApplyResult {
        public final boolean success;
        public final String message;
        public final String rolledEffectId;
        public final boolean wasDuplicate;
        public final int newLevel;
        public final int slotsUsed;

        private ApplyResult(boolean success, String message, String rolledEffectId, boolean wasDuplicate, int newLevel, int slotsUsed) {
            this.success = success;
            this.message = message;
            this.rolledEffectId = rolledEffectId;
            this.wasDuplicate = wasDuplicate;
            this.newLevel = newLevel;
            this.slotsUsed = slotsUsed;
        }

        public static ApplyResult fail(String msg) {
            return new ApplyResult(false, msg, null, false, 0, 0);
        }

        public static ApplyResult ok(String msg, String id, boolean dup, int lvl, int slotsUsed) {
            return new ApplyResult(true, msg, id, dup, lvl, slotsUsed);
        }
    }

    public static ApplyResult applyToArmor(ItemStack armor) {
        if (!isArmor(armor)) {
            return ApplyResult.fail("Target item is not armor.");
        }

        RaffleKeys.initAuto();

        ItemMeta meta = armor.getItemMeta();
        if (meta == null) return ApplyResult.fail("Armor has no meta.");

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        int slotsUsed = getInt(pdc, RaffleKeys.ARMOR_SLOTS_USED, 0);
        if (slotsUsed >= getMaxSlots()) {
            return ApplyResult.fail("This armor is already full.");
        }

        boolean hasCurse = getByte(pdc, RaffleKeys.ARMOR_HAS_CURSE, (byte) 0) == (byte) 1;

        // Load pool (lazy)
        if (RafflePool.isEmpty()) {
            RafflePool.reloadFromConfig();
        }

        List<String> basePool = RafflePool.getPool();
        if (basePool.isEmpty()) {
            return ApplyResult.fail("Raffle pool is empty. Check config.yml -> raffle.effects");
        }

        Set<String> curseIds = getCurseIdSet();

        // If already cursed, remove all curses from available rolls
        List<String> rollPool = new ArrayList<>(basePool.size());
        for (String id : basePool) {
            if (hasCurse && curseIds.contains(id)) continue;
            rollPool.add(id);
        }

        if (rollPool.isEmpty()) {
            return ApplyResult.fail("No valid effects available to roll (pool filtered empty).");
        }

        // Read existing effects map from PDC string
        Map<String, Integer> effects = decodeEffects(getString(pdc, RaffleKeys.ARMOR_EFFECTS, ""));

        // Roll 1 uniformly
        String rolled = rollPool.get(new Random().nextInt(rollPool.size()));
        boolean isCurse = curseIds.contains(rolled);

        // If it would add a curse but we already have one, block (shouldn't happen due to filtering)
        if (hasCurse && isCurse) {
            return ApplyResult.fail("Armor is already cursed; cannot add another curse.");
        }

        boolean duplicate = effects.containsKey(rolled);
        int newLevel;

        if (isCurse) {
            // Curses never level; always base
            newLevel = 1;
            effects.put(rolled, 1);
            hasCurse = true;
        } else {
            int current = effects.getOrDefault(rolled, 0);
            newLevel = (current <= 0) ? 1 : (current + 1);
            effects.put(rolled, newLevel);
        }

        // IMPORTANT RULE: every application consumes a slot, even duplicates
        slotsUsed += 1;

        // Save back to PDC
        pdc.set(RaffleKeys.ARMOR_EFFECTS, PersistentDataType.STRING, encodeEffects(effects));
        pdc.set(RaffleKeys.ARMOR_SLOTS_USED, PersistentDataType.INTEGER, slotsUsed);
        pdc.set(RaffleKeys.ARMOR_HAS_CURSE, PersistentDataType.BYTE, (byte) (hasCurse ? 1 : 0));

        armor.setItemMeta(meta);
    RaffleLoreUtil.updateVagueLore(
    armorItem,
    plugin.getConfig().getInt("raffle.max_slots_per_armor", 3)
);
        String msg = duplicate
                ? "Effect upgraded: " + rolled + " -> lvl " + newLevel + " (slots " + slotsUsed + "/" + getMaxSlots() + ")"
                : "Effect added: " + rolled + " (slots " + slotsUsed + "/" + getMaxSlots() + ")";

        return ApplyResult.ok(msg, rolled, duplicate, newLevel, slotsUsed);
    }

    // ----------------------------
    // Helpers
    // ----------------------------

    private static int getMaxSlots() {
        Plugin plugin = JavaPlugin.getProvidingPlugin(RaffleService.class);
        return plugin.getConfig().getInt("raffle.max_slots_per_armor", 3);
    }

    /** Prefer config list raffle.curses; fallback to a sensible default set. */
    private static Set<String> getCurseIdSet() {
        Plugin plugin = JavaPlugin.getProvidingPlugin(RaffleService.class);
        FileConfiguration cfg = plugin.getConfig();

        List<String> configured = cfg.getStringList("raffle.curses");
        Set<String> out = new HashSet<>();
        if (configured != null && !configured.isEmpty()) {
            for (String s : configured) {
                if (s == null) continue;
                String t = s.trim().toLowerCase();
                if (!t.isEmpty()) out.add(t);
            }
            return out;
        }

        // Fallback defaults (matches your current shortlist)
        out.add("terror");
        out.add("echoes");
        out.add("broken_compass");
        out.add("shadow_twin");
        out.add("mandators_curse");
        out.add("ogre");
        return out;
    }

    private static boolean isArmor(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        Material m = item.getType();
        String name = m.name();
        return name.endsWith("_HELMET")
                || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS");
    }

    private static int getInt(PersistentDataContainer pdc, org.bukkit.NamespacedKey key, int def) {
        Integer v = pdc.get(key, PersistentDataType.INTEGER);
        return v != null ? v : def;
    }

    private static byte getByte(PersistentDataContainer pdc, org.bukkit.NamespacedKey key, byte def) {
        Byte v = pdc.get(key, PersistentDataType.BYTE);
        return v != null ? v : def;
    }

    private static String getString(PersistentDataContainer pdc, org.bukkit.NamespacedKey key, String def) {
        String v = pdc.get(key, PersistentDataType.STRING);
        return v != null ? v : def;
    }

    /**
     * Encodes unique effects as: id:lvl|id:lvl|id:lvl
     * Levels only matter for GOOD; curses will always store lvl 1.
     */
    static String encodeEffects(Map<String, Integer> effects) {
        if (effects == null || effects.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        // stable order (nice for debugging)
        List<String> keys = new ArrayList<>(effects.keySet());
        Collections.sort(keys);
        for (String id : keys) {
            int lvl = Math.max(1, effects.getOrDefault(id, 1));
            if (sb.length() > 0) sb.append("|");
            sb.append(id).append(":").append(lvl);
        }
        return sb.toString();
    }

    static Map<String, Integer> decodeEffects(String encoded) {
        Map<String, Integer> out = new HashMap<>();
        if (encoded == null || encoded.trim().isEmpty()) return out;

        String[] parts = encoded.split("\\|");
        for (String part : parts) {
            String p = part.trim();
            if (p.isEmpty()) continue;

            String[] kv = p.split(":");
            String id = kv[0].trim().toLowerCase();
            if (id.isEmpty()) continue;

            int lvl = 1;
            if (kv.length >= 2) {
                try {
                    lvl = Integer.parseInt(kv[1].trim());
                } catch (NumberFormatException ignored) {}
            }
            out.put(id, Math.max(1, lvl));
        }
        return out;
    }
}

