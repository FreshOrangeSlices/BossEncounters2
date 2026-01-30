package com.orangeslices.bossencounters.raffle;

import com.orangeslices.bossencounters.BossEncountersPlugin;
import com.orangeslices.bossencounters.raffle.effects.RafflePotionTable;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
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
 * Config-driven:
 * - raffle.cooldown_ms
 * - raffle.require_token_mainhand
 * - raffle.require_armor_offhand
 * - raffle.message.*
 * - raffle.sound.*
 */
public final class RaffleApplyListener implements Listener {

    private final BossEncountersPlugin plugin;
    private final ConcurrentHashMap<UUID, Long> lastUseMs = new ConcurrentHashMap<>();

    public RaffleApplyListener(BossEncountersPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onApply(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        // Paper fires once per hand; only handle the main-hand event for consistency.
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        if (!player.isSneaking()) return;

        FileConfiguration cfg = plugin.getConfig();

        boolean requireTokenMainhand = cfg.getBoolean("raffle.require_token_mainhand", true);
        boolean requireArmorOffhand = cfg.getBoolean("raffle.require_armor_offhand", true);
        long cooldownMs = cfg.getLong("raffle.cooldown_ms", 250L);

        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();

        boolean mainIsToken = RaffleTokenFactory.isToken(main);
        boolean offIsToken = RaffleTokenFactory.isToken(off);

        // If no token involved, ignore
        if (!mainIsToken && !offIsToken) return;

        // If config requires mainhand token, enforce it
        if (requireTokenMainhand && !mainIsToken) {
            event.setCancelled(true);
            sendFail(player, cfg, "raffle.message.fail_generic", "Hold the token in your main hand.");
            playFailSound(player, cfg);
            return;
        }

        // If config requires armor offhand, enforce it
        if (requireArmorOffhand && mainIsToken) {
            if (off == null || off.getType() == Material.AIR) {
                event.setCancelled(true);
                sendFail(player, cfg, "raffle.message.fail_no_armor", null);
                playFailSound(player, cfg);
                return;
            }
            if (!isArmor(off.getType())) {
                event.setCancelled(true);
                sendFail(player, cfg, "raffle.message.fail_not_armor", null);
                playFailSound(player, cfg);
                return;
            }
        }

        // At this point: we are handling a token interaction, so cancel block use
        event.setCancelled(true);

        // Cooldown guard (0 disables)
        if (cooldownMs > 0) {
            long now = System.currentTimeMillis();
            long last = lastUseMs.getOrDefault(player.getUniqueId(), 0L);
            if (now - last < cooldownMs) return;
            lastUseMs.put(player.getUniqueId(), now);
        }

        // Determine armor target based on rules
        ItemStack armor;
        ItemStack token;

        if (requireTokenMainhand) {
            token = main;
            armor = requireArmorOffhand ? off : (mainIsToken ? off : main);
        } else {
            // Flexible mode: exactly one token in either hand
            if (mainIsToken && offIsToken) {
                sendFail(player, cfg, "raffle.message.fail_generic", "Hold armor in one hand and the token in the other.");
                playFailSound(player, cfg);
                return;
            }
            token = mainIsToken ? main : off;
            armor = mainIsToken ? off : main;
        }

        if (armor == null || armor.getType() == Material.AIR) {
            sendFail(player, cfg, "raffle.message.fail_no_armor", null);
            playFailSound(player, cfg);
            return;
        }

        if (!isArmor(armor.getType())) {
            sendFail(player, cfg, "raffle.message.fail_not_armor", null);
            playFailSound(player, cfg);
            return;
        }

        int maxSlots = plugin.raffleMaxSlotsPerArmor();
        RaffleService.ApplyResult result = plugin.raffleService().applyToArmor(armor, maxSlots);

        if (!result.success) {
            String reason = result.message;
            String msg = cfg.getString("raffle.message.fail_generic", "&c{reason}");
            msg = msg.replace("{reason}", reason == null ? "Failed." : reason);
            player.sendMessage(color(msg));
            playFailSound(player, cfg);
            return;
        }

        // Update vague lore
        RaffleLoreUtil.updateLore(armor, maxSlots);

        // Consume token only on success
        consumeOne(player, token);

        // Success feedback
        String success = cfg.getString("raffle.message.success", "&dSomething shifts within the armor...");
        player.sendMessage(color(success));
        playSuccessSound(player, cfg);
    }

    private void consumeOne(Player player, ItemStack stackRef) {
        if (stackRef == null || stackRef.getType() == Material.AIR) return;

        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();

        if (isSameItemRef(stackRef, main)) {
            consumeMain(player);
            return;
        }
        if (isSameItemRef(stackRef, off)) {
            consumeOff(player);
        }
    }

    private void consumeMain(Player player) {
        ItemStack stack = player.getInventory().getItemInMainHand();
        if (stack == null || stack.getType() == Material.AIR) return;

        int amt = stack.getAmount();
        if (amt <= 1) player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        else {
            stack.setAmount(amt - 1);
            player.getInventory().setItemInMainHand(stack);
        }
    }

    private void consumeOff(Player player) {
        ItemStack stack = player.getInventory().getItemInOffHand();
        if (stack == null || stack.getType() == Material.AIR) return;

        int amt = stack.getAmount();
        if (amt <= 1) player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        else {
            stack.setAmount(amt - 1);
            player.getInventory().setItemInOffHand(stack);
        }
    }

    private boolean isSameItemRef(ItemStack a, ItemStack b) {
        // Same reference check first (fast path)
        return a == b;
    }

    private void sendFail(Player p, FileConfiguration cfg, String key, String fallbackReason) {
        String msg = cfg.getString(key);
        if (msg == null || msg.isBlank()) {
            msg = cfg.getString("raffle.message.fail_generic", "&c{reason}");
            msg = msg.replace("{reason}", fallbackReason == null ? "Failed." : fallbackReason);
        }
        p.sendMessage(color(msg));
    }

    private void playSuccessSound(Player p, FileConfiguration cfg) {
        if (!cfg.getBoolean("raffle.sound.success.enabled", true)) return;
        String key = cfg.getString("raffle.sound.success.key", "BLOCK_ENCHANTMENT_TABLE_USE");
        float vol = (float) cfg.getDouble("raffle.sound.success.volume", 0.8);
        float pit = (float) cfg.getDouble("raffle.sound.success.pitch", 1.2);

        Sound s = safeSound(key);
        if (s != null) p.playSound(p.getLocation(), s, vol, pit);
    }

    private void playFailSound(Player p, FileConfiguration cfg) {
        if (!cfg.getBoolean("raffle.sound.fail.enabled", true)) return;
        String key = cfg.getString("raffle.sound.fail.key", "BLOCK_NOTE_BLOCK_BASS");
        float vol = (float) cfg.getDouble("raffle.sound.fail.volume", 0.7);
        float pit = (float) cfg.getDouble("raffle.sound.fail.pitch", 0.9);

        Sound s = safeSound(key);
        if (s != null) p.playSound(p.getLocation(), s, vol, pit);
    }

    private Sound safeSound(String key) {
        if (key == null) return null;
        try {
            return Sound.valueOf(key.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
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

    private String color(String s) {
        if (s == null) return "";
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    // ---------------------------
    // Helpers (safe placement)
    // ---------------------------

    private EquipmentSlot armorSlot(ItemStack armor) {
        if (armor == null || armor.getType() == null) return null;

        String t = armor.getType().name();
        if (t.endsWith("_HELMET")) return EquipmentSlot.HEAD;
        if (t.endsWith("_CHESTPLATE")) return EquipmentSlot.CHEST;
        if (t.endsWith("_LEGGINGS")) return EquipmentSlot.LEGS;
        if (t.endsWith("_BOOTS")) return EquipmentSlot.FEET;

        return null;
    }

    private boolean isEffectCompatibleWithSlot(RaffleEffectId id, EquipmentSlot slot) {
        if (id == null || slot == null) return false;

        // If it's NOT a potion-table effect (ex: custom curse), allow it on any armor
        RafflePotionTable.Entry entry = null;
        for (RafflePotionTable.Entry e : RafflePotionTable.entries()) {
            if (e.id == id) { entry = e; break; }
        }
        if (entry == null) return true;

        return switch (entry.slotRule) {
            case ANY_ARMOR -> true;
            case HELMET_ONLY -> slot == EquipmentSlot.HEAD;
            case CHESTPLATE_ONLY -> slot == EquipmentSlot.CHEST;
            case LEGGINGS_ONLY -> slot == EquipmentSlot.LEGS;
            case BOOTS_ONLY -> slot == EquipmentSlot.FEET;
        };
    }
}
