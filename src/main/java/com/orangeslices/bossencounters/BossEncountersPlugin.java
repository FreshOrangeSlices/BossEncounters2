package com.orangeslices.bossencounters;

import com.orangeslices.bossencounters.command.BecCommand;
import com.orangeslices.bossencounters.boss.BossManager;
import com.orangeslices.bossencounters.boss.apply.BossApplier;
import com.orangeslices.bossencounters.boss.spawn.SpawnBossListener;
import com.orangeslices.bossencounters.boss.combat.BossCombatListener;
import com.orangeslices.bossencounters.boss.drop.BossDropListener;
import com.orangeslices.bossencounters.addon.potion.PotionAddOnListener;
import com.orangeslices.bossencounters.addon.AddOnListener;
import com.orangeslices.bossencounters.addon.effect.AddOnEffectListener;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

public final class BossEncountersPlugin extends JavaPlugin {

    private NamespacedKey bossKey;
    private BossApplier bossApplier;

    // Extracted manager (removes “god class” state)
    private BossManager bossManager;

    // Keep a reference to spawn listener so BossManager can forward onBossCreated
    private SpawnBossListener spawnBossListener;

    // Keep reference so we can stop the scheduled task cleanly
    private PotionAddOnListener potionAddOnListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        bossKey = new NamespacedKey(this, "is_boss");
        bossApplier = new BossApplier(this);

        // Register listeners (store spawn listener reference)
        spawnBossListener = new SpawnBossListener(this);

        // Boss lifecycle manager (tracks counts + despawn tasks)
        bossManager = new BossManager(this, spawnBossListener);

        getServer().getPluginManager().registerEvents(spawnBossListener, this);
        getServer().getPluginManager().registerEvents(new BossCombatListener(this), this);
        getServer().getPluginManager().registerEvents(new AffixListener(this), this);
        getServer().getPluginManager().registerEvents(new BossDropListener(this), this);
        getServer().getPluginManager().registerEvents(new AddOnListener(this), this);
        getServer().getPluginManager().registerEvents(new AddOnEffectListener(this), this);

        // Potion AddOn path (refresh loop)
        potionAddOnListener = new PotionAddOnListener(this);
        getServer().getPluginManager().registerEvents(potionAddOnListener, this);
        potionAddOnListener.start();

        // Register command
        if (getCommand("bec") != null) {
            getCommand("bec").setExecutor(new BecCommand(this, bossApplier));
        }

        getLogger().info("BossEncounters enabled.");
    }

    @Override
    public void onDisable() {
        // Stop potion refresh task cleanly
        if (potionAddOnListener != null) {
            potionAddOnListener.stop();
            potionAddOnListener = null;
        }

        // Cleanup boss lifecycle state/tasks
        if (bossManager != null) {
            bossManager.shutdown();
            bossManager = null;
        }

        getLogger().info("BossEncounters disabled.");
    }

    public NamespacedKey bossKey() {
        return bossKey;
    }

    public BossApplier bossApplier() {
        return bossApplier;
    }

    /* -------------------------
       Local broadcast (radius) with mode
       ------------------------- */

    public void broadcastLocal(Location at, double radius, String msgColored) {
        if (at == null || at.getWorld() == null) return;

        World w = at.getWorld();
        double r2 = radius * radius;

        String mode = getConfig().getString("messages.mode", "CHAT");
        if (mode == null) mode = "CHAT";
        mode = mode.trim().toUpperCase();

        // Ensure colors are translated once
        String colored = ChatColor.translateAlternateColorCodes('&', msgColored);

        for (Player p : w.getPlayers()) {
            if (p.getLocation().distanceSquared(at) > r2) continue;

            switch (mode) {
                case "ACTIONBAR" -> sendActionBar(p, colored);
                case "TITLE" -> {
                    // Optional: first line only (you can expand later)
                    p.sendTitle(colored, "", 5, 40, 10);
                }
                case "CHAT" -> p.sendMessage(colored);
                default -> p.sendMessage(colored);
            }
        }
    }

    private void sendActionBar(Player player, String coloredMessage) {
        try {
            // Spigot/Paper compatible action bar (bungee component)
            player.spigot().sendMessage(
                    net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    net.md_5.bungee.api.chat.TextComponent.fromLegacyText(coloredMessage)
            );
        } catch (Throwable t) {
            // Fallback if server jar is weird
            player.sendMessage(coloredMessage);
        }
    }

    /* -------------------------
       Boss cap + tracking (delegated)
       ------------------------- */

    public boolean canCreateBossIn(World world) {
        return bossManager != null && bossManager.canCreateBossIn(world);
    }

    public int getMaxBossesPerWorld() {
        return bossManager == null ? 0 : bossManager.getMaxBossesPerWorld();
    }

    public int getActiveBossCount(World world) {
        return bossManager == null ? 0 : bossManager.getActiveBossCount(world);
    }

    /* -------------------------
       Boss lifecycle hooks (delegated)
       ------------------------- */

    public void onBossCreated(LivingEntity boss) {
        if (bossManager != null) bossManager.onBossCreated(boss);
    }

    public void onBossRemoved(LivingEntity boss) {
        if (bossManager != null) bossManager.onBossRemoved(boss);
    }

    /* -------------------------
       Despawn task management (delegated)
       ------------------------- */

    public void scheduleBossDespawn(UUID bossId, BukkitTask task) {
        if (bossManager != null) bossManager.scheduleBossDespawn(bossId, task);
    }

    public void cancelBossDespawn(UUID bossId) {
        if (bossManager != null) bossManager.cancelBossDespawn(bossId);
    }
}
