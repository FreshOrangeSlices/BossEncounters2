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
 * Pattern:
 * - Periodic refresh (same philosophy as potion engine)
 * - Reads raffle effects from armor
 * - Calls apply() while active
 * - Calls clear() when effect disappears
 *
 * This engine should NOT be modified when adding new effects.
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
        // Register custom effects here (additive, never reroute)
        register(new TerrorEffect());
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

        // 2) Determine which custom effects should be active
        Set<RaffleEffectId> nowActive = new HashSet<>();
        for (Map.Entry<RaffleEffectId, Integer> e : highest.entrySet()) {
            RaffleEffectId id = e.getKey();
            int level = e.getValue();

            if (level <= 0) continue;
            if (!registry.containsKey(id)) continue;

            RaffleCustomEffect effect = registry.get(id);
            if (effect == null) continue;

            nowActive.add(id);
            effect.apply(player, level);
        }

        // 3) Clear effects that are no longer present
        Set<RaffleEffectId> previouslyActive =
                activeByPlayer.getOrDefault(uuid, Collections.emptySet());

        for (RaffleEffectId id : previouslyActive) {
            if (!nowActive.contains(id)) {
                RaffleCustomEffect effect = registry.get(id);
                if (effect != null) {
                    effect.clear(player);
                }
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
