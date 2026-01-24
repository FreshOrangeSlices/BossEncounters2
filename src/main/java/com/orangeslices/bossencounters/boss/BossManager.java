package com.orangeslices.bossencounters.boss;

import com.orangeslices.bossencounters.BossEncountersPlugin;
import com.orangeslices.bossencounters.SpawnBossListener;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns boss lifecycle state:
 * - Active boss counts per world
 * - Despawn task tracking/canceling
 *
 * IMPORTANT: This is a refactor-only extraction:
 * behavior should remain identical to the previous BossEncountersPlugin logic.
 */
public final class BossManager {

    private final BossEncountersPlugin plugin;
    private final SpawnBossListener spawnBossListener;

    // Track active bosses per world (UUID key = world UUID)
    private final Map<UUID, Integer> activeBossesByWorld = new ConcurrentHashMap<>();

    // Track despawn tasks per boss entity
    private final Map<UUID, BukkitTask> despawnTasks = new ConcurrentHashMap<>();

    public BossManager(BossEncountersPlugin plugin, SpawnBossListener spawnBossListener) {
        this.plugin = plugin;
        this.spawnBossListener = spawnBossListener;
    }

    /* -------------------------
       Boss cap + tracking (for BecCommand + sanity)
       ------------------------- */

    public boolean canCreateBossIn(World world) {
        if (world == null) return false;

        int cap = getMaxBossesPerWorld();
        if (cap <= 0) return true; // 0 = unlimited

        return getActiveBossCount(world) < cap;
    }

    public int getMaxBossesPerWorld() {
        return plugin.getConfig().getInt("boss.cap.max_per_world", 0);
    }

    public int getActiveBossCount(World world) {
        if (world == null) return 0;
        return activeBossesByWorld.getOrDefault(world.getUID(), 0);
    }

    /* -------------------------
       Boss lifecycle hooks (called by SpawnBossListener + BecCommand)
       ------------------------- */

    public void onBossCreated(LivingEntity boss) {
        if (boss == null || boss.getWorld() == null) return;

        // increment active boss count
        UUID w = boss.getWorld().getUID();
        activeBossesByWorld.merge(w, 1, Integer::sum);

        // forward to listener to apply messages/despawn scheduling
        if (spawnBossListener != null) {
            spawnBossListener.onBossCreated(boss);
        }
    }

    public void onBossRemoved(LivingEntity boss) {
        if (boss == null || boss.getWorld() == null) return;

        // decrement active boss count (never below 0)
        UUID w = boss.getWorld().getUID();
        activeBossesByWorld.compute(w, (k, v) -> {
            int cur = (v == null) ? 0 : v;
            return Math.max(0, cur - 1);
        });

        cancelBossDespawn(boss.getUniqueId());
    }

    /* -------------------------
       Despawn task management
       ------------------------- */

    public void scheduleBossDespawn(UUID bossId, BukkitTask task) {
        cancelBossDespawn(bossId);
        despawnTasks.put(bossId, task);
    }

    public void cancelBossDespawn(UUID bossId) {
        BukkitTask old = despawnTasks.remove(bossId);
        if (old != null) old.cancel();
    }

    /* -------------------------
       Shutdown / cleanup
       ------------------------- */

    public void shutdown() {
        // Cancel any scheduled boss despawns
        for (UUID id : despawnTasks.keySet()) {
            cancelBossDespawn(id);
        }
        despawnTasks.clear();

        // Reset counts (not required, but keeps state clean)
        activeBossesByWorld.clear();
    }
}
