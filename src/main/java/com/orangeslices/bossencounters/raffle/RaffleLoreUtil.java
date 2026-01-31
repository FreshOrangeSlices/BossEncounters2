package com.orangeslices.bossencounters.raffle;

import com.orangeslices.bossencounters.raffle.effects.RaffleEffectReader;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.stream.Collectors;

public final class RaffleLoreUtil {

    private RaffleLoreUtil() {}

    /**
     * TEMP TESTING MODE:
     * - Shows GOOD effects AND CURSES in lore so you can verify what's on the item.
     * - Later we can hide curses again once testing is done.
     */
    public static void updateLore(ItemStack item, int slotsUsed) {
        if (item == null) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        Map<RaffleEffectId, Integer> effects = RaffleEffectReader.readFromItem(item);

        List<String> lore = new ArrayList<>();
        lore.add("§7Raffle Slots: §f" + slotsUsed + "§7/§f" + slotsUsed); // keeps your “slots used” visible

        if (effects.isEmpty()) {
            lore.add("§8No effects.");
            meta.setLore(lore);
            item.setItemMeta(meta);
            return;
        }

        // Sort: GOOD first, then CURSES, alphabetical inside each
        List<Map.Entry<RaffleEffectId, Integer>> sorted = effects.entrySet().stream()
                .filter(e -> e.getKey() != null)
                .sorted(Comparator
                        .comparing((Map.Entry<RaffleEffectId, Integer> e) -> e.getKey().isCurse())
                        .thenComparing(e -> e.getKey().name()))
                .collect(Collectors.toList());

        lore.add("§7Effects:");

        for (Map.Entry<RaffleEffectId, Integer> e : sorted) {
            RaffleEffectId id = e.getKey();
            int level = Math.max(1, e.getValue());

            String name = titleCase(id.name());

            if (id.isCurse()) {
                // CURSE line (red)
                lore.add("§c✖ " + name);
            } else {
                // GOOD line (green)
                // Only show level if it can level; otherwise keep it clean
                if (id.canLevel()) {
                    lore.add("§a✔ " + name + " §7" + toRoman(level));
                } else {
                    lore.add("§a✔ " + name);
                }
            }
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    private static String titleCase(String raw) {
        String[] parts = raw.toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
            if (i < parts.length - 1) sb.append(' ');
        }
        return sb.toString();
    }

    private static String toRoman(int n) {
        // Small range is all we need
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(n);
        };
    }
}
