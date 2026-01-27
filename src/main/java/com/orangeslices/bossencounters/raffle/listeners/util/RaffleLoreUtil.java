package com.orangeslices.bossencounters.raffle.util;

import com.orangeslices.bossencounters.raffle.RaffleKeys;
import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public final class RaffleLoreUtil {

    private static final String STRIP_HEADER = "Add-On Slots:";

    private RaffleLoreUtil() {}

    public static void updateVagueLore(ItemStack item, int maxSlots) {
        if (item == null) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String encoded = pdc.get(RaffleKeys.ARMOR_EFFECTS, PersistentDataType.STRING);

        int usedSlots = countSlots(encoded);

        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

        // Remove previous raffle lore block (keep everything else)
        lore.removeIf(line -> {
            String stripped = ChatColor.stripColor(line == null ? "" : line);
            if (stripped.startsWith(STRIP_HEADER)) return true;
            // Remove our flavor bullets only (we tag them with a unique prefix)
            return stripped.startsWith("• ") && (stripped.contains("lingers") || stripped.contains("altered") || stripped.contains("discern"));
        });

        lore.add(ChatColor.GRAY + "Add-On Slots: " + ChatColor.YELLOW + usedSlots + ChatColor.GRAY + "/" + ChatColor.YELLOW + maxSlots);

        // Vague flavor (slot-count based only)
        if (usedSlots >= 1) lore.add(ChatColor.DARK_GRAY + "• Something lingers within...");
        if (usedSlots >= 2) lore.add(ChatColor.DARK_GRAY + "• The armor feels altered");
        if (usedSlots >= 3) lore.add(ChatColor.DARK_GRAY + "• Its nature is hard to discern");

        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    private static int countSlots(String encoded) {
        if (encoded == null || encoded.trim().isEmpty()) return 0;
        // encoded format: id:lvl|id:lvl|id:lvl
        return encoded.split("\\|").length;
    }
}
