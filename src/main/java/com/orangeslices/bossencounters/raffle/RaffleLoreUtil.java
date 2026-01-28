package com.orangeslices.bossencounters.raffle;

import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class RaffleLoreUtil {

    private RaffleLoreUtil() {}

    /**
     * Rebuilds the raffle section of lore from PDC.
     * For now, we keep it simple: overwrite the entire lore list.
     * (We can make it "non-destructive" later if you want to preserve other lore lines.)
     */
    public static void updateLore(ItemStack armor, int maxSlots) {
        if (armor == null || !armor.hasItemMeta()) return;

        ItemMeta meta = armor.getItemMeta();
        if (meta == null) return;

        if (RaffleKeys.EFFECTS == null || RaffleKeys.SLOT_COUNT == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        String raw = pdc.get(RaffleKeys.EFFECTS, PersistentDataType.STRING);
        Integer slotsUsed = pdc.get(RaffleKeys.SLOT_COUNT, PersistentDataType.INTEGER);

        // fallback: if slots weren't stored yet, infer from number of effects
        int inferred = countEffects(raw);
        int used = (slotsUsed != null ? slotsUsed : inferred);

        List<String> lore = new ArrayList<>();

        lore.add(ChatColor.GRAY + "Add-On Slots: " + ChatColor.WHITE + used + ChatColor.GRAY + "/" + ChatColor.WHITE + maxSlots);

        // Show vague names
        List<String> effectLines = buildEffectLines(raw);
        lore.addAll(effectLines);

        // Optional: a tiny hint line for mystery vibe
        if (!effectLines.isEmpty()) {
            lore.add(ChatColor.DARK_GRAY + "Something lingers within...");
        }

        meta.setLore(lore);
        armor.setItemMeta(meta);
    }

    private static int countEffects(String raw) {
        if (raw == null || raw.trim().isEmpty()) return 0;
        int count = 0;
        for (String part : raw.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) count++;
        }
        return count;
    }

    /**
     * Stored format: "ID:level,ID:level"
     */
    private static List<String> buildEffectLines(String raw) {
        List<String> lines = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) return lines;

        for (String part : raw.split(",")) {
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

            // Curses never level (but we can still display "I" if you want)
            if (id.isCurse()) lvl = 1;
            if (lvl < 1) lvl = 1;

            String name = RaffleDisplay.vagueName(id);

            // Vague display: no "curse" tag, no real ID.
            // We still show level for GOOD scaling, but it's subtle.
            String lvlSuffix = (lvl > 1 ? ChatColor.DARK_GRAY + " " + roman(lvl) : "");
            lines.add(ChatColor.GRAY + "- " + ChatColor.WHITE + name + lvlSuffix);
        }

        return lines;
    }

    private static String roman(int number) {
        // Small + safe for early testing (we only need 1-3 realistically right now)
        switch (number) {
            case 1: return "I";
            case 2: return "II";
            case 3: return "III";
            case 4: return "IV";
            case 5: return "V";
            default: return String.valueOf(number);
        }
    }
}
