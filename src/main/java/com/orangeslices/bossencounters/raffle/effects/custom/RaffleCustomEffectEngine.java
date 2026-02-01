package com.orangeslices.bossencounters.raffle.effects.custom;

import com.orangeslices.bossencounters.BossEncountersPlugin;
import com.orangeslices.bossencounters.raffle.RaffleEffectId;
import com.orangeslices.bossencounters.raffle.effects.RaffleEffectReader;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Engine for NON-potion raffle effects (curses & custom mechanics).
 *
 * Key behavior:
 * - GOOD custom effects (future) may apply repeatedly if desired
 * - CURSES trigger ONCE when they become active
 * - clear() is called when the effect disappears
 */
public final class RaffleCustomEffectEngine {

    private final BossEncountersPlugin plugin;
    private BukkitTask task;

    // Registered custom effects (permanent registry)
    private final Map<RaffleEffectId, RaffleCustomEffect> registry = new HashMap<>();

    // Tracks which effects are currently active per player
    private final Map<UUID, Set<RaffleEffectId>> activeByPlayer = new HashMap<>();

    public RaffleCustomEffectEngine(BossEncountersPlugin plugin) {
        this.plugin = plugin;
        registerDefaults();
    }

    private void registerDefaults() {
        register(new TerrorEffect());
        register(new DreadEffect());

        // BENCHED:
        // register(new MisstepEffect());
        // register(new UneaseEffect());

        register(new EchoesEffect());
        register(new DisarrayEffect());
        register(new OnAllFoursEffect());
        register(new MatadorEffect());
        register(new MotherHenEffect());
        register(new ReductionEffect());
    }

    private void register(RaffleCustomEffect effect) {
        if (effect == null) return;
        registry.put(effect.getId(), effect);
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

        // Cleanup all active effects
        for (Map.Entry<UUID, Set<RaffleEffectId>> entry : activeByPlayer.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null) continue;

            for (RaffleEffectId id : entry.getValue()) {
                RaffleCustomEffect effect = registry.get(id);
                if (effect != null) {
                    effect.clear(player);
                }
            }
        }

        activeByPlayer.clear();
    }

    private void refreshPlayer(Player player) {
        if (player == null || !player.isOnline()) return;

        UUID uuid = player.getUniqueId();

        // 1) Read highest levels across armor
        Map<RaffleEffectId, Integer> highest = new HashMap<>();
        mergeArmor(highest, player.getInventory().getHelmet());
        mergeArmor(highest, player.getInventory().getChestplate());
        mergeArmor(highest, player.getInventory().getLeggings());
        mergeArmor(highest, player.getInventory().getBoots());

        // 2) Determine which custom effects should be active now
        Set<RaffleEffectId> nowActive = new HashSet<>();
        for (Map.Entry<RaffleEffectId, Integer> e : highest.entrySet()) {
            RaffleEffectId id = e.getKey();
            int level = e.getValue();

            if (level <= 0) continue;
            if (!registry.containsKey(id)) continue;

            nowActive.add(id);
        }

        Set<RaffleEffectId> prev = activeByPlayer.getOrDefault(uuid, Collections.emptySet());

        // Newly activated effects
        for (RaffleEffectId id : nowActive) {
            if (prev.contains(id)) continue;

            RaffleCustomEffect effect = registry.get(id);
            if (effect == null) continue;

            int level = highest.getOrDefault(id, 1);

            // CURSES trigger once
            effect.apply(player, level);
        }

        // Removed effects
        for (RaffleEffectId id : prev) {
            if (nowActive.contains(id)) continue;

            RaffleCustomEffect effect = registry.get(id);
            if (effect != null) {
                effect.clear(player);
            }
        }

        activeByPlayer.put(uuid, nowActive);
    }

    private void mergeArmor(Map<RaffleEffectId, Integer> into, ItemStack armor) {
        if (armor == null) return;
        Map<RaffleEffectId, Integer> map = RaffleEffectReader.readFromItem(armor);
        RaffleEffectReader.mergeHighest(into, map);
    }
}
