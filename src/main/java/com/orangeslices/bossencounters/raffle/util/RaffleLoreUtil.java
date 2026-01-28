package com.orangeslices.bossencounters.raffle.util;

import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class RaffleLoreUtil {

    private RaffleLoreUtil() {}

    public static void updateVagueLore(ItemStack armor, int usedSlots, int maxSlots) {
        if (armor == null || armor.getType().isAir()) return;

        ItemMeta meta = armor.getItemMeta();
        if (meta == null) return;

        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

        // Remove previous slot line we own
        lore.removeIf(line -> line != null && ChatColor.stripColor(line).startsWith("Add-On Slots:"));

        lore.add(ChatColor.DARK_GRAY + "Add-On Slots: " + ChatColor.GRAY + usedSlots
                + ChatColor.DARK_GRAY + "/" + ChatColor.GRAY + maxSlots);

        if (usedSlots > 0) {
            lore.add(ChatColor.DARK_GRAY + "" + ChatColor.ITALIC + "It hums faintly...");
        }

        meta.setLore(lore);
        armor.setItemMeta(meta);
    }
}
