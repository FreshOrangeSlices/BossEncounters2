package com.orangeslices.bossencounters.raffle.effects.custom;

import com.orangeslices.bossencounters.raffle.RaffleEffectId;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class EchoesEffect implements RaffleCustomEffect {

    private static final int RUN_TICKS = 20 * 10; // ~10s
    private static final int PERIOD_TICKS = 20;   // 1s

    private final Random rng = new Random();
    private final Map<UUID, BukkitTask> tasks = new HashMap<>();

    @Override
    public RaffleEffectId getId() {
        return RaffleEffectId.ECHOES;
    }

    @Override
    public void apply(Player player, int level) {
        if (player == null || !player.isOnline()) return;

        // already running for this equip session
        if (tasks.containsKey(player.getUniqueId())) return;

        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(getClass());
        UUID id = player.getUniqueId();

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) {
                clear(player);
                return;
            }

            Location loc = p.getLocation().clone();
            loc.add(rng.nextInt(11) - 5, 0, rng.nextInt(11) - 5);

            Sound s = switch (rng.nextInt(5)) {
                case 0 -> Sound.ENTITY_CAVE_SPIDER_AMBIENT;
                case 1 -> Sound.ENTITY_ZOMBIE_AMBIENT;
                case 2 -> Sound.ENTITY_ENDERMAN_STARE;
                case 3 -> Sound.ENTITY_SKELETON_AMBIENT;
                default -> Sound.ENTITY_WITCH_AMBIENT;
            };

            p.getWorld().playSound(loc, s, 0.35f, 0.9f);
        }, 0L, PERIOD_TICKS);

        tasks.put(id, task);

        // auto-stop after duration
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player p = Bukkit.getPlayer(id);
            if (p != null) clear(p);
        }, RUN_TICKS);
    }

    @Override
    public void clear(Player player) {
        if (player == null) return;
        BukkitTask t = tasks.remove(player.getUniqueId());
        if (t != null) t.cancel();
    }
}
