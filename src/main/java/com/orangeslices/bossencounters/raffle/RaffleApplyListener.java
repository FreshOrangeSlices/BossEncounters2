package com.orangeslices.bossencounters.raffle;

import com.orangeslices.bossencounters.BossEncountersPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Sneak + Right-Click to apply a Raffle Token to armor.
 *
 * Rules:
 * - Token must be in MAIN HAND
 * - Armor must be in OFFHAND
 * - Only consumes token on success
 */
public final class RaffleApplyListener implements Listener {

    private final BossEncountersPlugin plugin;

    public RaffleApplyListener(BossEncountersPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onApply(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        // Paper fires twice (main/offhand). We only handle the main-hand event.
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();

        // Must be sneaking
        if (!player.isSneaking()) return;

        ItemStack token = player.getInventory().getItemInMainHand();
        if (!RaffleTokenFactory.isToken(token)) return;

        ItemStack armor = player.getInventory().getItemInOffHand();
        if (armor == null || armor.getType() == Material.AIR) {
            player.sendMessage(ChatColor.RED + "Hold armor in your offhand.");
            return;
        }

        // We are handling this interaction now — prevent opening chests/doors/etc.
        event.setCancelled(true);

        int maxSlots = plugin.raffleMaxSlotsPerArmor();
        RaffleService.ApplyResult result = plugin.raffleService().applyToArmor(armor, maxSlots);

        if (!result.success) {
            player.sendMessage(ChatColor.RED + result.message);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.9f);
            return;
        }

        // Update vague lore
        RaffleLoreUtil.updateLore(armor, maxSlots);

        // Consume exactly 1 token (only on success)
        consumeOneMainHand(player);

        // Vague player feedback (no real effect names)
        player.sendMessage(ChatColor.LIGHT_PURPLE + "Something shifts within the armor...");
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.2f);
    }

    private void consumeOneMainHand(Player player) {
        ItemStack stack = player.getInventory().getItemInMainHand();
        if (stack == null || stack.getType() == Material.AIR) return;

        int amt = stack.getAmount();
        if (amt <= 1) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        } else {
            stack.setAmount(amt - 1);
            player.getInventory().setItemInMainHand(stack);
        }
    }
}
