package com.orangeslices.bossencounters.boss.spawn;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class SpawnBossListener implements Listener {

    private final BossEncountersPlugin plugin;

    public SpawnBossListener(BossEncountersPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMobSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof LivingEntity mob)) return;

        // Don’t double-apply
        if (plugin.bossApplier().isBoss(mob)) return;

        // Only natural spawns (optional guard — uncomment if you want NATURAL only)
        // if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) return;

        if (shouldBecomeBoss(mob)) {
            onBossCreated(mob); // ✅ SINGLE entry point
        }
    }

    /**
     * Single source of truth: turning a mob into a boss triggers all boss setup here.
     */
    public void onBossCreated(LivingEntity boss) {
        if (boss == null) return;
        if (!boss.isValid() || boss.isDead()) return;

        // Apply boss modifiers (rank + affixes + title + name)
        plugin.bossApplier().applyBossStats(boss);

        // Spawn FX (lightweight)
        maybePlaySpawnFx(boss);

        // Spawn message (local radius)
        maybeBroadcastSpawn(boss);

        // Despawn scheduling
        maybeScheduleDespawn(boss);
    }

    /* -------------------------
       Spawn roll logic
       ------------------------- */

    private boolean shouldBecomeBoss(LivingEntity mob) {
        FileConfiguration cfg = plugin.getConfig();

        // Chance: 1 in N
        int oneIn = Math.max(1, cfg.getInt("spawn.one_in", 250));
        if (ThreadLocalRandom.current().nextInt(oneIn) != 0) return false;

        // Whitelist check
        Set<String> allowed = getWhitelist(cfg);
        if (!allowed.isEmpty()) {
            String type = mob.getType().name().toUpperCase(Locale.ROOT);
            if (!allowed.contains(type)) return false;
        }

        return true;
    }

    private Set<String> getWhitelist(FileConfiguration cfg) {
        Set<String> set = new HashSet<>();
        for (String s : cfg.getStringList("mobs.whitelist")) {
            if (s != null && !s.isBlank()) {
                set.add(s.trim().toUpperCase(Locale.ROOT));
            }
        }
        return set;
    }

    /* -------------------------
       Spawn FX
       ------------------------- */

    private void maybePlaySpawnFx(LivingEntity boss) {
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean("fx.spawn.enabled", true)) return;

        String rank = plugin.bossApplier().getRank(boss);

        int count = 10; // default
        if (rank != null && !rank.isBlank()) {
            count = cfg.getInt("fx.spawn.particles." + rank, count);
        }

        double radius = cfg.getDouble("fx.spawn.radius", 24.0);

        // only play if a player is nearby (prevents “FX in unloaded nowhere”)
        boolean playerNear = boss.getWorld().getPlayers().stream()
                .anyMatch(p -> p.getLocation().distanceSquared(boss.getLocation()) <= (radius * radius));
        if (!playerNear) return;

        boss.getWorld().spawnParticle(
                org.bukkit.Particle.SMOKE,
                boss.getLocation().add(0, 1.0, 0),
                Math.max(1, count),
                0.35, 0.45, 0.35,
                0.01
        );

        String soundName = cfg.getString("fx.spawn.sound", "ENTITY_WITHER_SPAWN");
        float volume = (float) cfg.getDouble("fx.spawn.volume", 0.45);
        float pitch = (float) cfg.getDouble("fx.spawn.pitch", 1.1);

        try {
            org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundName.toUpperCase(Locale.ROOT));
            boss.getWorld().playSound(boss.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException ignored) {
            // invalid config sound name -> do nothing
        }
    }

    /* -------------------------
       Spawn / Despawn messages
       ------------------------- */

    private void maybeBroadcastSpawn(LivingEntity boss) {
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean("messages.enabled", true)) return;
        if (!cfg.getBoolean("messages.spawn.enabled", true)) return;

        double radius = cfg.getDouble("messages.radius", 40.0);
        String format = cfg.getString(
                "messages.spawn.format",
                "&6A {rank_color}[{rank_label}] &r{title}{mob} &6has appeared!"
        );

        String msg = formatMessage(boss, format);
        plugin.broadcastLocal(boss.getLocation(), radius, msg);
    }

    private void maybeBroadcastDespawn(LivingEntity boss) {
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean("messages.enabled", true)) return;
        if (!cfg.getBoolean("messages.despawn.enabled", true)) return;

        double radius = cfg.getDouble("messages.radius", 40.0);
        String format = cfg.getString(
                "messages.despawn.format",
                "&7Faded: {rank_color}[{rank_label}] &r{title}{mob}"
        );

        String msg = formatMessage(boss, format);
        plugin.broadcastLocal(boss.getLocation(), radius, msg);
    }

    private String formatMessage(LivingEntity boss, String format) {
        FileConfiguration cfg = plugin.getConfig();

        String rank = plugin.bossApplier().getRank(boss);
        String rankLabel = (rank != null)
                ? cfg.getString("ranks." + rank + ".label", rank)
                : "Boss";
        String rankColor = (rank != null)
                ? cfg.getString("ranks." + rank + ".color", "&c")
                : "&c";

        String title = plugin.bossApplier().getTitle(boss);
        String titlePart = (title != null && !title.isBlank()) ? "[" + title + "] " : "";

        String mobName = prettyMobName(boss);

        double rankXp = (rank != null) ? cfg.getDouble("ranks." + rank + ".xp_multiplier", 1.0) : 1.0;

        return ChatColor.translateAlternateColorCodes('&',
                format
                        .replace("{rank}", rank == null ? "" : rank)
                        .replace("{rank_label}", rankLabel == null ? "" : rankLabel)
                        .replace("{rank_color}", rankColor == null ? "" : rankColor)
                        .replace("{title}", titlePart)
                        .replace("{mob}", mobName)
                        .replace("{rank_xp}", String.valueOf((int) Math.round(rankXp)))
        );
    }

    private String prettyMobName(LivingEntity boss) {
        String raw = boss.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        if (raw.isBlank()) return "Mob";
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    /* -------------------------
       Despawn scheduling
       ------------------------- */

    private void maybeScheduleDespawn(LivingEntity boss) {
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean("despawn.enabled", true)) return;

        int afterSeconds = Math.max(10, cfg.getInt("despawn.after_seconds", 300));
        long delayTicks = afterSeconds * 20L;

        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // Boss might already be dead/removed
            if (!boss.isValid() || boss.isDead()) {
                plugin.cancelBossDespawn(boss.getUniqueId());
                return;
            }

            double noPlayersWithin = cfg.getDouble("despawn.only_if_no_players_within", 48.0);
            boolean requireNoTarget = cfg.getBoolean("despawn.require_no_target", true);

            Location loc = boss.getLocation();

            boolean playerNear = loc.getWorld() != null && loc.getWorld().getPlayers().stream()
                    .anyMatch(p -> p.getLocation().distanceSquared(loc) <= (noPlayersWithin * noPlayersWithin));

            boolean hasTarget = (boss instanceof org.bukkit.entity.Mob m) && m.getTarget() != null;

            // If conditions aren’t met, reschedule instead of despawning
            if (playerNear || (requireNoTarget && hasTarget)) {
                maybeScheduleDespawn(boss);
                return;
            }

            // Despawn message (local radius)
            maybeBroadcastDespawn(boss);

            // Remove entity
            boss.remove();
            plugin.cancelBossDespawn(boss.getUniqueId());

        }, delayTicks);

        plugin.scheduleBossDespawn(boss.getUniqueId(), task);
    }
}
