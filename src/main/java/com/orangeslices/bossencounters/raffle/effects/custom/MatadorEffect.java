package com.orangeslices.bossencounters.raffle.effects.custom;

import com.orangeslices.bossencounters.raffle.RaffleEffectId;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MatadorEffect implements RaffleCustomEffect {

    private static final int DESPAWN_TICKS = 20 * 12; // ~12s

    private final Map<UUID, Entity> spawned = new HashMap<>();
    private final Map<UUID, BukkitTask> despawnTasks = new HashMap<>();

    @Override
    public RaffleEffectId getId() {
        return RaffleEffectId.MATADOR;
    }

    @Override
    public void apply(Player player, int level) {
        if (player == null || !player.isOnline()) return;

        UUID id = player.getUniqueId();
        if (spawned.containsKey(id)) return;

        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(getClass());

        Cow cow = player.getWorld().spawn(player.getLocation().add(2, 0, 2), Cow.class, c -> {
            c.setAdult();
            c.setRemoveWhenFarAway(true);
        });

        cow.setTarget(player);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_COW_HURT, 0.7f, 0.8f);

        spawned.put(id, cow);

        BukkitTask despawn = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Entity e = spawned.remove(id);
            if (e != null && e.isValid()) e.remove();
            despawnTasks.remove(id);
        }, DESPAWN_TICKS);

        despawnTasks.put(id, despawn);
    }

    @Override
    public void clear(Player player) {
        if (player == null) return;

        UUID id = player.getUniqueId();
        BukkitTask t = despawnTasks.remove(id);
        if (t != null) t.cancel();

        Entity e = spawned.remove(id);
        if (e != null && e.isValid()) e.remove();
    }
}
