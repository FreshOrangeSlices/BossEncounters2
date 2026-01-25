
package com.orangeslices.bossencounters.boss.combat;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

public final class BossCombatListener implements Listener {

    private final BossEncountersPlugin plugin;

    public BossCombatListener(BossEncountersPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBossDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof LivingEntity boss)) return;
        if (!plugin.bossApplier().isBoss(boss)) return;

        // cancel despawn task
        plugin.cancelBossDespawn(boss.getUniqueId());

        FileConfiguration cfg = plugin.getConfig();

        // XP wiring (your current setup)
        int baseExp = event.getDroppedExp();
        double globalMult = cfg.getDouble("rewards.xp_multiplier", 1.0);

        String rank = plugin.bossApplier().getRank(boss);
        double rankMult = 1.0;
        if (rank != null && !rank.isBlank()) {
            rankMult = cfg.getDouble("ranks." + rank + ".xp_multiplier", 1.0);
        }

        double raw = baseExp * globalMult * rankMult;
        int finalExp;
        if (raw <= 0) finalExp = 0;
        else if (raw > Integer.MAX_VALUE) finalExp = Integer.MAX_VALUE;
        else finalExp = (int) Math.round(raw);

        event.setDroppedExp(finalExp);

        // Death message (local)
        if (cfg.getBoolean("messages.enabled", true) && cfg.getBoolean("messages.death.enabled", true)) {
            double radius = cfg.getDouble("messages.radius", 40.0);
            String format = cfg.getString("messages.death.format",
                    "&aDefeated: {rank_color}[{rank_label}] &r{title}{mob} &e(+{rank_xp}x XP)");

            String msg = formatDeathMessage(boss, format);
            plugin.broadcastLocal(boss.getLocation(), radius, msg);
        }
    }

    private String formatDeathMessage(LivingEntity boss, String format) {
        String rank = plugin.bossApplier().getRank(boss);
        String rankLabel = (rank != null) ? plugin.getConfig().getString("ranks." + rank + ".label", rank) : "Boss";
        String rankColor = (rank != null) ? plugin.getConfig().getString("ranks." + rank + ".color", "&c") : "&c";
        String title = plugin.bossApplier().getTitle(boss);
        String titlePart = (title != null && !title.isBlank()) ? "[" + title + "] " : "";

        String mobName = boss.getType().name().toLowerCase().replace('_', ' ');
        mobName = Character.toUpperCase(mobName.charAt(0)) + mobName.substring(1);

        double rankXp = (rank != null) ? plugin.getConfig().getDouble("ranks." + rank + ".xp_multiplier", 1.0) : 1.0;

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
}
