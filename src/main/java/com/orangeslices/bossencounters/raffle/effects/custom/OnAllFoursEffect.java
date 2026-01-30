package com.orangeslices.bossencounters.raffle.effects.custom;

import com.orangeslices.bossencounters.raffle.RaffleEffectId;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class OnAllFoursEffect implements RaffleCustomEffect {

    private static final int DURATION_TICKS = 20 * 6; // ~6s

    private final Map<UUID, BukkitTask> revertTasks = new HashMap<>();

    @Override
    public RaffleEffectId getId() {
        return RaffleEffectId.ON_ALL_FOURS;
    }

    @Override
    public void apply(Player player, int level) {
        if (player == null || !player.isOnline()) return;

        UUID id = player.getUniqueId();
        if (revertTasks.containsKey(id)) return;

        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(getClass());

        player.setSwimming(true);

        BukkitTask revert = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                p.setSwimming(false);
            }
            revertTasks.remove(id);
        }, DURATION_TICKS);

        revertTasks.put(id, revert);
    }

    @Override
    public void clear(Player player) {
        if (player == null) return;

        UUID id = player.getUniqueId();
        BukkitTask t = revertTasks.remove(id);
        if (t != null) t.cancel();

        player.setSwimming(false);
    }
}
