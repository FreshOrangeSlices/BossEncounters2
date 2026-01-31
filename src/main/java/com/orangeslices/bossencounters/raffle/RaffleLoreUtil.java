package com.orangeslices.bossencounters.raffle;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class RaffleLoreUtil {

    private RaffleLoreUtil() {
        // utility
    }

    /**
     * Updates lore after a raffle application.
     * Called by RaffleApplyListener.
     */
    public static void updateLore(ItemStack item, int slotsUsed) {
        if (item == null) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        List<String> lore = new ArrayList<>();

        // Slot usage line (authoritative)
        lore.add("§7Raffle Slots: §f" + slotsUsed);

        // Effect presence (vague, hides curses by design)
        Map<RaffleEffectId, Integer> effects =
                raffle.effects.RaffleEffectReader.readFromItem(item);

        boolean hasGoodEffects = effects.keySet().stream()
                .anyMatch(id -> id != null && id.isGood());

        if (hasGoodEffects) {
            lore.add("§7Infused with mysterious power");
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    /**
     * Display name helper (used elsewhere if needed).
     */
    public static String displayName(RaffleEffectId id) {
        if (id == null) return "Unknown";
        return titleCase(id.name());
    }

    private static String titleCase(String raw) {
        String[] parts = raw.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (p.isEmpty()) continue;

            sb.append(Character.toUpperCase(p.charAt(0)))
              .append(p.substring(1));

            if (i < parts.length - 1) sb.append(' ');
        }
        return sb.toString();
    }
}
