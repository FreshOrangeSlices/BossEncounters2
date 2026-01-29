package com.orangeslices.bossencounters.raffle;

import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public final class RaffleLoreUtil {

    private RaffleLoreUtil() {}

    /**
     * Rebuilds the raffle section of lore from PDC.
     * Currently overwrites the entire lore list (intentional for now).
     */
    public static void updateLore(ItemStack armor, int maxSlots) {
        if (armor == null || !armor.hasItemMeta()) return;

        // Defensive init check
        RaffleKeys.validateInit();

        ItemMeta meta = armor.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        String raw = pdc.get(RaffleKeys.EFFECTS, PersistentDataType.STRING);
        Integer slotsUsed = pdc.get(RaffleKeys.SLOT_COUNT, PersistentDataType.INTEGER);

        // Fallback: infer from effect count if slots not stored yet
        int inferred = countEffects(raw);
        int used = (slotsUsed != null ? slotsUsed : inferred);

        List<String> lore = new ArrayList<>();

        lore.add(ChatColor.GRAY + "Add-On Slots: "
                + ChatColor.WHITE + used
                + ChatColor.GRAY + "/"
                + ChatColor.WHITE + maxSlots);

        // Vague effect lines
        List<String> effectLines = buildEffectLines(raw);
        lore.addAll(effectLines);

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
            if (!part.trim().isEmpty()) count++;
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

            // Curses never level
            if (id.isCurse()) lvl = 1;
            if (lvl < 1) lvl = 1;

            String name = RaffleDisplay.vagueName(id);
            String lvlSuffix = (lvl > 1
                    ? ChatColor.DARK_GRAY + " " + roman(lvl)
                    : "");

            lines.add(ChatColor.GRAY + "- " + ChatColor.WHITE + name + lvlSuffix);
        }

        return lines;
    }

    private static String roman(int number) {
        return switch (number) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(number);
        };
    }
}
