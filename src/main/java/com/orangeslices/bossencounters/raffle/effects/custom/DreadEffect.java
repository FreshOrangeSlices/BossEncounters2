package com.orangeslices.bossencounters.raffle.effects.custom;

import com.orangeslices.bossencounters.raffle.RaffleEffectId;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class DreadEffect implements RaffleCustomEffect {

    private static final int DURATION_TICKS = 20 * 20; // 20 seconds
    private static final int LIGHTNING_COUNT_MIN = 1;
    private static final int LIGHTNING_COUNT_MAX = 2;

    private final Map<UUID, WeatherSnapshot> previousWeather = new HashMap<>();
    private final Map<UUID, BukkitTask> revertTasks = new HashMap<>();

    @Override
    public RaffleEffectId getId() {
        return RaffleEffectId.DREAD;
    }

    @Override
    public void apply(Player player, int level) {
        if (player == null || !player.isOnline()) return;

        UUID id = player.getUniqueId();
        if (previousWeather.containsKey(id)) return; // one-time trigger

        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(getClass());
        World world = player.getWorld();

        // Save previous weather state
        previousWeather.put(id, new WeatherSnapshot(
                world.hasStorm(),
                world.isThundering(),
                world.getWeatherDuration()
        ));

        // Force storm + thunder
        world.setStorm(true);
        world.setThundering(true);
        world.setWeatherDuration(DURATION_TICKS);

        // Schedule visual-only lightning near the player
        int strikes = ThreadLocalRandom.current()
                .nextInt(LIGHTNING_COUNT_MIN, LIGHTNING_COUNT_MAX + 1);

        for (int i = 0; i < strikes; i++) {
            int delay = ThreadLocalRandom.current().nextInt(20, DURATION_TICKS - 20);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;

                Location base = player.getLocation();
                double offsetX = ThreadLocalRandom.current().nextDouble(2.5, 5.0) *
                        (ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
                double offsetZ = ThreadLocalRandom.current().nextDouble(2.5, 5.0) *
                        (ThreadLocalRandom.current().nextBoolean() ? 1 : -1);

                Location strikeLoc = base.clone().add(offsetX, 0, offsetZ);
                world.strikeLightningEffect(strikeLoc); // VISUAL ONLY
            }, delay);
        }

        // Revert weather after duration
        BukkitTask revert = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            WeatherSnapshot snap = previousWeather.remove(id);
            if (snap != null) {
                world.setStorm(snap.storm);
                world.setThundering(snap.thundering);
                world.setWeatherDuration(snap.duration);
            }

            BukkitTask t = revertTasks.remove(id);
            if (t != null) t.cancel();
        }, DURATION_TICKS);

        revertTasks.put(id, revert);
    }

    @Override
    public void clear(Player player) {
        if (player == null) return;

        UUID id = player.getUniqueId();
        World world = player.getWorld();

        WeatherSnapshot snap = previousWeather.remove(id);
        if (snap != null) {
            world.setStorm(snap.storm);
            world.setThundering(snap.thundering);
            world.setWeatherDuration(snap.duration);
        }

        BukkitTask t = revertTasks.remove(id);
        if (t != null) t.cancel();
    }

    private record WeatherSnapshot(boolean storm, boolean thundering, int duration) {}
}
