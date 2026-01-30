package com.orangeslices.bossencounters;

import com.orangeslices.bossencounters.raffle.RaffleDebug;
import com.orangeslices.bossencounters.raffle.RaffleKeys;
import com.orangeslices.bossencounters.raffle.RafflePool;
import com.orangeslices.bossencounters.raffle.RaffleService;
import com.orangeslices.bossencounters.raffle.RaffleTokenFactory;
import com.orangeslices.bossencounters.raffle.effects.RafflePotionEngine;
import com.orangeslices.bossencounters.raffle.effects.custom.RaffleCustomEffectEngine;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BossEncountersPlugin extends JavaPlugin {

    private NamespacedKey bossKey;
    private BossApplier bossApplier;

    // Raffle system core
    private RafflePool rafflePool;
    private RaffleService raffleService;

    // Raffle effect engines
    private RafflePotionEngine rafflePotionEngine;
    private RaffleCustomEffectEngine raffleCustomEffectEngine;

    // Track active bosses per world
    private final Map<UUID, Integer> activeBossesByWorld = new ConcurrentHashMap<>();

    // Track despawn tasks per boss entity
    private final Map<UUID, BukkitTask> despawnTasks = new ConcurrentHashMap<>();

    private SpawnBossListener spawnBossListener;
    private PotionAddOnListener potionAddOnListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();

        bossKey = new NamespacedKey(this, "is_boss");
        bossApplier = new BossApplier(this);

        // -------------------------
        // Raffle init
        // -------------------------
        RaffleKeys.init(this);
        RaffleTokenFactory.init(this);

        RaffleDebug.init(this);
        RaffleDebug.setEnabled(getConfig().getBoolean("raffle.debug", false));

        rafflePool = new RafflePool(this);
        rafflePool.reloadFromConfig();
        raffleService = new RaffleService(rafflePool);

        // -------------------------
        // Raffle effect engines
        // -------------------------
        rafflePotionEngine = new RafflePotionEngine(this);
        rafflePotionEngine.start();

        raffleCustomEffectEngine = new RaffleCustomEffectEngine(this);
        raffleCustomEffectEngine.start();

        // -------------------------
        // Listeners
        // -------------------------
        spawnBossListener = new SpawnBossListener(this);

        getServer().getPluginManager().registerEvents(
                new com.orangeslices.bossencounters.raffle.RaffleApplyListener(this), this);
        getServer().getPluginManager().registerEvents(spawnBossListener, this);
        getServer().getPluginManager().registerEvents(new BossCombatListener(this), this);
        getServer().getPluginManager().registerEvents(new AffixListener(this), this);
        getServer().getPluginManager().registerEvents(new BossDropListener(this), this);
        getServer().getPluginManager().registerEvents(new AddOnListener(this), this);
        getServer().getPluginManager().registerEvents(new AddOnEffectListener(this), this);

        // Existing potion add-on system
        potionAddOnListener = new PotionAddOnListener(this);
        getServer().getPluginManager().registerEvents(potionAddOnListener, this);
        potionAddOnListener.start();

        // Command
        if (getCommand("bec") != null) {
            getCommand("bec").setExecutor(new BecCommand(this, bossApplier));
        }

        getLogger().info("BossEncounters enabled.");
    }

    @Override
    public void onDisable() {

        if (raffleCustomEffectEngine != null) {
            raffleCustomEffectEngine.stop();
            raffleCustomEffectEngine = null;
        }

        if (rafflePotionEngine != null) {
            rafflePotionEngine.stop();
            rafflePotionEngine = null;
        }

        if (potionAddOnListener != null) {
            potionAddOnListener.stop();
            potionAddOnListener = null;
        }

        for (UUID id : despawnTasks.keySet()) {
            cancelBossDespawn(id);
        }
        despawnTasks.clear();
        activeBossesByWorld.clear();

        getLogger().info("BossEncounters disabled.");
    }

    // -------------------------
    // REQUIRED GETTERS (fixes your compile errors)
    // -------------------------

    public NamespacedKey bossKey() {
        return bossKey;
    }

    public BossApplier bossApplier() {
        return bossApplier;
    }

    // -------------------------
    // Raffle accessors
    // -------------------------

    public RafflePool rafflePool() {
        return rafflePool;
    }

    public RaffleService raffleService() {
        return raffleService;
    }

    public int raffleMaxSlotsPerArmor() {
        return getConfig().getInt("raffle.max_slots_per_armor", RaffleService.DEFAULT_MAX_SLOTS);
    }

    /* -------------------------
       Broadcast helpers
       ------------------------- */

    public void broadcastLocal(Location at, double radius, String msgColored) {
        if (at == null || at.getWorld() == null) return;

        World w = at.getWorld();
        double r2 = radius * radius;

        String mode = getConfig().getString("messages.mode", "CHAT");
        if (mode == null) mode = "CHAT";
        mode = mode.trim().toUpperCase();

        String colored = ChatColor.translateAlternateColorCodes('&', msgColored);

        for (Player p : w.getPlayers()) {
            if (p.getLocation().distanceSquared(at) > r2) continue;

            switch (mode) {
                case "ACTIONBAR" -> sendActionBar(p, colored);
                case "TITLE" -> p.sendTitle(colored, "", 5, 40, 10);
                default -> p.sendMessage(colored);
            }
        }
    }

    private void sendActionBar(Player player, String coloredMessage) {
        try {
            player.spigot().sendMessage(
                    net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    net.md_5.bungee.api.chat.TextComponent.fromLegacyText(coloredMessage)
            );
        } catch (Throwable t) {
            player.sendMessage(coloredMessage);
        }
    }

    /* -------------------------
       Boss lifecycle helpers
       ------------------------- */

    public void onBossCreated(LivingEntity boss) {
        if (boss == null || boss.getWorld() == null) return;

        UUID w = boss.getWorld().getUID();
        activeBossesByWorld.merge(w, 1, Integer::sum);

        if (spawnBossListener != null) {
            spawnBossListener.onBossCreated(boss);
        }
    }

    public void onBossRemoved(LivingEntity boss) {
        if (boss == null || boss.getWorld() == null) return;

        UUID w = boss.getWorld().getUID();
        activeBossesByWorld.compute(w, (k, v) -> Math.max(0, (v == null ? 0 : v) - 1));
        cancelBossDespawn(boss.getUniqueId());
    }

    public void scheduleBossDespawn(UUID bossId, BukkitTask task) {
        cancelBossDespawn(bossId);
        despawnTasks.put(bossId, task);
    }

    public void cancelBossDespawn(UUID bossId) {
        BukkitTask old = despawnTasks.remove(bossId);
        if (old != null) old.cancel();
    }
}
