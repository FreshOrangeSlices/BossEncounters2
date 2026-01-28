package com.orangeslices.bossencounters.raffle;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class RaffleTokenFactory {

    private RaffleTokenFactory() {}

    /**
     * Creates a single-use, non-stackable raffle token.
     * Non-stackable is enforced by a unique UUID stored in PDC.
     */
    public static ItemStack createToken(Plugin plugin) {
        ItemStack item = new ItemStack(Material.PAPER, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Raffle Token");

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Apply to armor to add a");
        lore.add(ChatColor.GRAY + "mysterious effect.");
        lore.add("");
        lore.add(ChatColor.DARK_GRAY + "Sneak + Right-Click");
        meta.setLore(lore);

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        // Mark as raffle token + make it non-stackable
        // We'll reuse RaffleKeys.EFFECTS key space? No — keep it separate:
        // We'll store two small tags using NamespacedKey created in plugin.
        meta.getPersistentDataContainer().set(
                plugin.getNamespacedKey("raffle_token"),
                PersistentDataType.BYTE,
                (byte) 1
        );
        meta.getPersistentDataContainer().set(
                plugin.getNamespacedKey("raffle_token_uuid"),
                PersistentDataType.STRING,
                UUID.randomUUID().toString()
        );

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Check if an ItemStack is a raffle token.
     */
    public static boolean isToken(Plugin plugin, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        if (!item.hasItemMeta()) return false;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        Byte flag = meta.getPersistentDataContainer().get(
                plugin.getNamespacedKey("raffle_token"),
                PersistentDataType.BYTE
        );
        return flag != null && flag == (byte) 1;
    }
}
