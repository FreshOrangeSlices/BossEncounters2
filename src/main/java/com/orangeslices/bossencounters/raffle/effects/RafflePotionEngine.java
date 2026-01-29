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
 * This mirrors PotionAddOnListener logic:
 * - periodic refresh
 * - applyIfBetter()
 * - highest level across armor
 *
 * BUT:
 * - effect mappings live in RafflePotionTable (expandable, no rerouting)
 */
public final class RafflePotionEngine {

    private final BossEncountersPlugin plugin;
    private BukkitTask task;

    public RafflePotionEngine(BossEncountersPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();

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
        if (player == null) return;

        // 1) Collect highest raffle levels across armor
        Map<RaffleEffectId, Integer> highest = new HashMap<>();
        mergeArmor(highest, player.getInventory().getHelmet());
        mergeArmor(highest, player.getInventory().getChestplate());
        mergeArmor(highest, player.getInventory().getLeggings());
        mergeArmor(highest, player.getInventory().getBoots());

        // 2) Apply potion-style raffle effects from table
        for (RafflePotionTable.Entry entry : RafflePotionTable.entries()) {
            if (entry == null) continue;

            int level = highest.getOrDefault(entry.id, 0);
            if (level <= 0) continue;

            // Helmet-only rule
            if (entry.slotRule == RafflePotionTable.SlotRule.HELMET_ONLY) {
                ItemStack helmet = player.getInventory().getHelmet();
                Map<RaffleEffectId, Integer> helmetMap =
                        RaffleEffectReader.readFromItem(helmet);
                level = helmetMap.getOrDefault(entry.id, 0);
                if (level <= 0) continue;
            }

            applyIfBetter(player, entry.potion, level, entry.durationTicks);
        }
    }

    private void mergeArmor(Map<RaffleEffectId, Integer> into, ItemStack armor) {
        if (armor == null) return;
        Map<RaffleEffectId, Integer> map = RaffleEffectReader.readFromItem(armor);
        RaffleEffectReader.mergeHighest(into, map);
    }

    private void applyIfBetter(Player player, PotionEffectType type, int level, int duration) {
        if (type == null || level <= 0) return;

        int amplifier = Math.max(0, level - 1);
        PotionEffect current = player.getPotionEffect(type);

        if (current != null) {
            if (current.getAmplifier() > amplifier) return;
            if (current.getAmplifier() == amplifier && current.getDuration() > duration) return;
        }

        // Same flags as your stable system:
        // ambient=true, particles=false, icon=true
        player.addPotionEffect(
                new PotionEffect(type, duration, amplifier, true, false, true)
        );
    }
}
