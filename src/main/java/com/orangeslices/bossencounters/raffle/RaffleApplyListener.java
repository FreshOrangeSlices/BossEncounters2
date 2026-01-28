package com.orangeslices.bossencounters.raffle;

import com.orangeslices.bossencounters.BossEncountersPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class RaffleApplyListener implements Listener {

    private final BossEncountersPlugin plugin;

    public RaffleApplyListener(BossEncountersPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onApply(PlayerInteractEvent event) {
        // Only care about right-click actions
        switch (event.getAction()) {
            case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> {}
            default -> { return; }
        }

        Player player = event.getPlayer();

        // Must be sneaking
        if (!player.isSneaking()) return;

        // Prevent double-fire (main hand + offhand) — we only handle main hand event.
        if (event.getHand() != EquipmentSlot.HAND) return;

        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();

        boolean mainIsToken = RaffleTokenFactory.isToken(main);
        boolean offIsToken = RaffleTokenFactory.isToken(off);

        // Need exactly one token in either hand
        if (!mainIsToken && !offIsToken) return;

        // Determine which item is the armor target (the other hand)
        ItemStack armor = mainIsToken ? off : main;
        if (armor == null || armor.getType() == Material.AIR) {
            player.sendMessage(ChatColor.RED + "Hold armor in the other hand.");
            return;
        }

        int maxSlots = plugin.raffleMaxSlotsPerArmor();
        RaffleService.ApplyResult result = plugin.raffleService().applyToArmor(armor, maxSlots);

        if (!result.success) {
            player.sendMessage(ChatColor.RED + result.message);
            return;
        }

        // Update vague lore
        RaffleLoreUtil.updateLore(armor, maxSlots);

        // Consume exactly 1 token (only on success)
        if (mainIsToken) {
            consumeOne(player.getInventory().getItemInMainHand());
        } else {
            consumeOne(player.getInventory().getItemInOffHand());
        }

        // Vague player feedback (no real effect names)
        player.sendMessage(ChatColor.LIGHT_PURPLE + "Something shifts within the armor...");
        event.setCancelled(true);
    }

    private void consumeOne(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) return;
        int amt = stack.getAmount();
        if (amt <= 1) {
            stack.setAmount(0);
        } else {
            stack.setAmount(amt - 1);
        }
    }
}
