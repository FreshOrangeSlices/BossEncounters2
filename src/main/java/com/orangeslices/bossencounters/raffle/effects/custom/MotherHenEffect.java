package com.orangeslices.bossencounters.raffle.effects.custom;

import com.orangeslices.bossencounters.raffle.RaffleEffectId;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

public final class MotherHenEffect implements RaffleCustomEffect {

    // Requested: 10 chicks, staggered spawn (Option B)
    private static final int COUNT = 10;
    private static final int SPAWN_INTERVAL_TICKS = 5;     // 1 chick every 5 ticks
    private static final int DESPAWN_TICKS = 20 * 12;      // ~12s

    // Follow tuning
    private static final int FOLLOW_PERIOD_TICKS = 10;     // run follow logic 2x/sec
    private static final double FOLLOW_SPEED = 0.22;       // gentle nudge per tick-run
    private static final double TELEPORT_IF_FAR = 10.0;    // if farther than this, snap them closer
    private static final double STOP_DISTANCE = 1.6;       // if close enough, stop nudging

    private final Map<UUID, List<Entity>> spawned = new HashMap<>();
    private final Map<UUID, BukkitTask> despawnTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> followTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> spawnTasks = new HashMap<>();

    @Override
    public RaffleEffectId getId() {
        return RaffleEffectId.MOTHER_HEN;
    }

    @Override
    public void apply(Player player, int level) {
        if (player == null || !player.isOnline()) return;

        UUID id = player.getUniqueId();
        if (spawned.containsKey(id)) return; // one-time trigger while worn

        JavaPlugin plugin = JavaPlugin.
