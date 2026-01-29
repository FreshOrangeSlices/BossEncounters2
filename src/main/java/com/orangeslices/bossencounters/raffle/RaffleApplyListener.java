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

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sneak + Right-Click to apply a Raffle Token to armor.
 *
 * Guardrails:
 * - MAIN HAND must be the token (consistent UX)
 * - OFFHAND must be armor
 * - Cancels interaction to prevent opening blocks when token is used
 * - Anti-double-fire: ignores OFF_HAND event and adds short per-player cooldown
 * - Only consumes token on success
 */
public final class RaffleApplyListener implements Listener {

    private final BossEncountersPlugin plugin;

    // Very small cooldown to prevent spam/double triggers from clients/macros
    private final ConcurrentHashMap<UUID, Long> lastUseMs = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 250;

    public RaffleApplyListener(BossEncountersPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onApply(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        // Paper fires once per hand. We only handle main-hand event.
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();

        // Must be sneaking
        if (!player.isSneaking()) return;

        // Token must be in MAIN HAND
        ItemStack token = player.getInventory().getItemInMainHand();
        if (!RaffleTokenFactory.isToken(token)) return;

        // If token is used, we own this interaction. Prevent opening blocks etc.
        event.setCancelled(true);

        // Cooldown guard
        long now = System.currentTimeMillis();
        long last = lastUseMs.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < COOLDOWN_MS) {
            return;
        }
        lastUseMs.put(player.getUniqueId(), now);

        // Armor must be in OFFHAND
        ItemStack armor = player.getInventory().getItemInOffHand();
        if (armor == null || armor.getType() == Material.AIR) {
            player.sendMessage(ChatColor.RED + "Hold armor in your offhand.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.9f);
            return;
        }

        // Quick armor validation (clearer error than deep service messages)
        if (!isArmor(armor.getType())) {
            player.sendMessage(ChatColor.RED + "That item isn't armor.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.9f);
            return;
        }

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

        // Vague feedback (no real effect names)
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

    private boolean isArmor(Material mat) {
        if (mat == null) return false;
        String name = mat.name();
        return name.endsWith("_HELMET")
                || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS");
    }
}
