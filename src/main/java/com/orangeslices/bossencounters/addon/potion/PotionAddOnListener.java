package com.orangeslices.bossencounters.addon.potion;

import com.orangeslices.bossencounters.BossEncountersPlugin;
import com.orangeslices.bossencounters.token.TokenKeys;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

public final class PotionAddOnListener implements Listener {

    private final BossEncountersPlugin plugin;

    private final TokenKeys keys;

    private final NamespacedKey hasteKey;
    private final NamespacedKey strengthKey;

    private final NamespacedKey fireResKey;
    private final NamespacedKey waterBreathingKey;
    private final NamespacedKey nightVisionKey;
    private final NamespacedKey healthBoostKey;

    private BukkitTask task;

    public PotionAddOnListener(BossEncountersPlugin plugin) {
        this.plugin = plugin;

        this.keys = new TokenKeys(plugin);

        // Centralized PDC keys
        this.hasteKey = keys.hasteLevel();
        this.strengthKey = keys.strengthLevel();

        this.fireResKey = keys.fireResLevel();
        this.healthBoostKey = keys.healthBoostLevel();

        this.nightVisionKey = keys.nightVisionLevel();

        // Still supported (legacy token not in TokenType list)
        this.waterBreathingKey = keys.custom("water_breathing_level");
    }

    public void start() {
        if (task != null) task.cancel();

        task = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                () -> plugin.getServer().getOnlinePlayers().forEach(this::refreshPotionAddOns),
                20L,
                40L
        );
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
    }

    private void refreshPotionAddOns(Player player) {
        if (player == null) return;

        int haste = getHeldLevel(player.getInventory().getItemInMainHand(), hasteKey);
        int strength = getHeldLevel(player.getInventory().getItemInMainHand(), strengthKey);

        int fireRes = getArmorMaxLevel(player, fireResKey);
        int healthBoost = getArmorMaxLevel(player, healthBoostKey);

        ItemStack helmet = player.getInventory().getHelmet();
        int water = getItemLevel(helmet, waterBreathingKey);
        int night = getItemLevel(helmet, nightVisionKey);

        applyIfBetter(player, PotionEffectType.HASTE, haste, 120);
        applyIfBetter(player, PotionEffectType.STRENGTH, strength, 120);

        applyIfBetter(player, PotionEffectType.FIRE_RESISTANCE, fireRes, 120);
        applyIfBetter(player, PotionEffectType.HEALTH_BOOST, healthBoost, 120);

        applyIfBetter(player, PotionEffectType.WATER_BREATHING, water, 120);
        applyIfBetter(player, PotionEffectType.NIGHT_VISION, night, 350);
    }

    private void applyIfBetter(Player player, PotionEffectType type, int level, int duration) {
        if (type == null || level <= 0) return;

        int amplifier = level - 1;
        PotionEffect current = player.getPotionEffect(type);

        if (current != null) {
            if (current.getAmplifier() > amplifier) return;
            if (current.getAmplifier() == amplifier && current.getDuration() > duration) return;
        }

        player.addPotionEffect(
                new PotionEffect(type, duration, amplifier, true, false, true)
        );
    }

    private int getHeldLevel(ItemStack item, NamespacedKey key) {
        return clamp(getItemLevel(item, key));
    }

    private int getArmorMaxLevel(Player p, NamespacedKey key) {
        int max = 0;
        max = Math.max(max, getItemLevel(p.getInventory().getHelmet(), key));
        max = Math.max(max, getItemLevel(p.getInventory().getChestplate(), key));
        max = Math.max(max, getItemLevel(p.getInventory().getLeggings(), key));
        max = Math.max(max, getItemLevel(p.getInventory().getBoots(), key));
        return clamp(max);
    }

    private int getItemLevel(ItemStack item, NamespacedKey key) {
        if (item == null) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;
        return meta.getPersistentDataContainer().getOrDefault(key, PersistentDataType.INTEGER, 0);
    }

    /**
     * Preserve current behavior: global clamp 0..2 for potion add-ons.
     * (Even if a token claims higher, this keeps it stable.)
     */
    private int clamp(int lvl) {
        return Math.max(0, Math.min(2, lvl));
    }
}
