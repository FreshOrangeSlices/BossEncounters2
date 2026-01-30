package com.orangeslices.bossencounters.raffle.effects.custom;

import com.orangeslices.bossencounters.raffle.RaffleEffectId;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public final class MotherHenEffect implements RaffleCustomEffect {

    private static final int COUNT = 5;
    private static final int DESPAWN_TICKS = 20 * 12; // ~12s

    private final Map<UUID, List<Entity>> spawned = new HashMap<>();
    private final Map<UUID, BukkitTask> despawnTasks = new HashMap<>();

    @Override
    public RaffleEffectId getId() {
        return RaffleEffectId.MOTHER_HEN;
    }

    @Override
    public void apply(Player player, int level) {
        if (player == null || !player.isOnline()) return;

        UUID id = player.getUniqueId();
        if (spawned.containsKey(id)) return;

        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(getClass());

        List<Entity> list = new ArrayList<>();
        for (int i = 0; i < COUNT; i++) {
            Chicken chick = player.getWorld().spawn(player.getLocation().add((i - 2) * 0.4, 0, 0.6), Chicken.class, c -> {
                c.setBaby();
                c.setRemoveWhenFarAway(true);
            });
            chick.setTarget(player);
            list.add(chick);
        }

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_CHICKEN_AMBIENT, 0.6f, 1.2f);
        spawned.put(id, list);

        BukkitTask despawn = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            List<Entity> ents = spawned.remove(id);
            if (ents != null) ents.forEach(e -> { if (e != null && e.isValid()) e.remove(); });
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

        List<Entity> ents = spawned.remove(id);
        if (ents != null) ents.forEach(e -> { if (e != null && e.isValid()) e.remove(); });
    }
}
