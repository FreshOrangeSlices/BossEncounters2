package com.orangeslices.bossencounters.raffle.effects;

import com.orangeslices.bossencounters.BossEncountersPlugin;
import com.orangeslices.bossencounters.raffle.RaffleEffectId;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;

/**
 * Raffle potion-style effects refresher.
 *
 * Mirrors the stable Potion Add-On approach:
 * - periodic refresh
 * - applyIfBetter()
 * - highest level across armor
 *
 * Effect mappings live in RafflePotionTable (expandable).
 */
public final class RafflePotionEngine {

    private final BossEncountersPlugin plugin;
    private BukkitTask task;

    public RafflePotionEngine(BossEncountersPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();

        // start after 1s, refresh every 2s
        task = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                () -> plugin.getServer().getOnlinePlayers().forEach(this::refreshPlayer),
                20L,
                40L
        );
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
    }

    private void refreshPlayer(Player player) {
        if (player == null || !player.isOnline()) return;

        // Highest across all armor (for ANY_ARMOR effects and quick lookup)
        Map<RaffleEffectId, Integer> highest = new HashMap<>();
        mergeArmor(highest, player.getInventory().getHelmet());
        mergeArmor(highest, player.getInventory().getChestplate());
        mergeArmor(highest, player.getInventory().getLeggings());
        mergeArmor(highest, player.getInventory().getBoots());

        for (RafflePotionTable.Entry entry : RafflePotionTable.entries()) {
            if (entry == null || entry.id == null || entry.potion == null) continue;

            int level = resolveLevelForSlotRule(player, entry, highest);
            if (level <= 0) continue;

            // Enforce non-leveling effects
            if (!entry.canLevel) {
                level = 1;
            }

            applyIfBetter(player, entry.potion, level, entry.durationTicks, entry.canLevel);
        }
    }

    private int resolveLevelForSlotRule(Player player, RafflePotionTable.Entry entry, Map<RaffleEffectId, Integer> highest) {
        return switch (entry.slotRule) {
            case ANY_ARMOR -> highest.getOrDefault(entry.id, 0);

            case HELMET_ONLY -> readLevelFromPiece(player.getInventory().getHelmet(), entry.id);

            case CHESTPLATE_ONLY -> readLevelFromPiece(player.getInventory().getChestplate(), entry.id);

            case LEGGINGS_ONLY -> readLevelFromPiece(player.getInventory().getLeggings(), entry.id);

            case BOOTS_ONLY -> readLevelFromPiece(player.getInventory().getBoots(), entry.id);
        };
    }

    private int readLevelFromPiece(ItemStack armor, RaffleEffectId id) {
        if (armor == null) return 0;
        Map<RaffleEffectId, Integer> map = RaffleEffectReader.readFromItem(armor);
        return map.getOrDefault(id, 0);
    }

    private void mergeArmor(Map<RaffleEffectId, Integer> into, ItemStack armor) {
        if (armor == null) return;
        Map<RaffleEffectId, Integer> map = RaffleEffectReader.readFromItem(armor);
        RaffleEffectReader.mergeHighest(into, map);
    }

    private void applyIfBetter(Player player, PotionEffectType type, int level, int duration, boolean canLevel) {
        if (type == null || level <= 0) return;

        int amplifier = canLevel ? Math.max(0, level - 1) : 0;

        PotionEffect current = player.getPotionEffect(type);
        if (current != null) {
            if (current.getAmplifier() > amplifier) return;
            if (current.getAmplifier() == amplifier && current.getDuration() > duration) return;
        }

        // ambient=true, particles=false, icon=true
        player.addPotionEffect(new PotionEffect(type, duration, amplifier, true, false, true));
    }
}
