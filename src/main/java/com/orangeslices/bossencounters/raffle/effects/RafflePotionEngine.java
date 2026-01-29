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
 * Modeled after PotionAddOnListener (stable approach): refresh every N ticks and apply
 * potion effects using applyIfBetter (no particles, icon shown).
 *
 * IMPORTANT:
 * - This only handles "simple potion effects" (the fast baseline).
 * - Custom effects will live in the same raffle/effects directory later.
 */
public final class RafflePotionEngine {

    private final BossEncountersPlugin plugin;
    private BukkitTask task;

    public RafflePotionEngine(BossEncountersPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();

        // Same rhythm as your stable add-on loop: start after 1s, refresh every 2s
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

        // Collect highest-level raffle effects across armor (helmet/chest/legs/boots)
        Map<RaffleEffectId, Integer> highest = new HashMap<>();

        mergeArmor(highest, player.getInventory().getHelmet());
        mergeArmor(highest, player.getInventory().getChestplate());
        mergeArmor(highest, player.getInventory().getLeggings());
        mergeArmor(highest, player.getInventory().getBoots());

        // Apply "simple potion effects" mapping here.
        // Keep this list small for now; we expand later via a table class (RafflePotionTable).
        //
        // Your current raffle pool: VITALITY, EMBER_WARD, DREAD, MISSTEP
        //
        // Suggested baseline mappings (safe + easy):
        // - VITALITY -> HEALTH_BOOST (lvl -> amp)
        // - EMBER_WARD -> FIRE_RESISTANCE (always amp 0)
        //
        // Curses (DREAD, MISSTEP) we intentionally do NOT apply as potion effects here
        // yet, since they may become custom mechanics.

        int vitality = highest.getOrDefault(RaffleEffectId.VITALITY, 0);
        int emberWard = highest.getOrDefault(RaffleEffectId.EMBER_WARD, 0);

        // Durations: mirror your stable add-on approach
        applyIfBetter(player, PotionEffectType.HEALTH_BOOST, vitality, 120);
        applyIfBetter(player, PotionEffectType.FIRE_RESISTANCE, emberWard, 120);
    }

    private void mergeArmor(Map<RaffleEffectId, Integer> into, ItemStack armor) {
        if (armor == null) return;
        Map<RaffleEffectId, Integer> map = RaffleEffectReader.readFromItem(armor);
        RaffleEffectReader.mergeHighest(into, map);
    }

    private void applyIfBetter(Player player, PotionEffectType type, int level, int duration) {
        if (type == null || level <= 0) return;

        // Level 1 -> amplifier 0, level 2 -> amplifier 1, etc.
        int amplifier = Math.max(0, level - 1);

        PotionEffect current = player.getPotionEffect(type);
        if (current != null) {
            if (current.getAmplifier() > amplifier) return;
            if (current.getAmplifier() == amplifier && current.getDuration() > duration) return;
        }

        // ambient=true (less spammy), particles=false, icon=true
        player.addPotionEffect(new PotionEffect(type, duration, amplifier, true, false, true));
    }
}
