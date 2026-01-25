package com.orangeslices.bossencounters.raffle;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Builds the non-stackable Raffle Token item.
 * (Logic is intentionally minimal for now — just creates the item + PDC tag.)
 */
public final class RaffleTokenFactory {

    private RaffleTokenFactory() {}

    public static ItemStack createToken() {
        RaffleKeys.initAuto();

        ItemStack item = new ItemStack(Material.FIREWORK_STAR, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(ChatColor.GOLD + "Raffle Add-On");
        meta.setLore(List.of(
                ChatColor.GRAY + "Sneak + Right-click to apply",
                ChatColor.DARK_GRAY + "Armor only."
        ));

        // Mark as a raffle token
        meta.getPersistentDataContainer().set(RaffleKeys.TOKEN_RAFFLE, PersistentDataType.BYTE, (byte) 1);

        item.setItemMeta(meta);
        return item;
    }

    public static boolean isRaffleToken(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        if (!item.hasItemMeta()) return false;

        RaffleKeys.initAuto();
        Byte val = item.getItemMeta().getPersistentDataContainer()
                .get(RaffleKeys.TOKEN_RAFFLE, PersistentDataType.BYTE);

        return val != null && val == (byte) 1;
    }
}

