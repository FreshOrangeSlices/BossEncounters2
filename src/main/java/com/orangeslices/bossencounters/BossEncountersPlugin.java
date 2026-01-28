package com.orangeslices.bossencounters;

import com.orangeslices.bossencounters.raffle.RaffleTokenFactory;
import com.orangeslices.bossencounters.raffle.RaffleKeys;
import com.orangeslices.bossencounters.raffle.RafflePool;
import com.orangeslices.bossencounters.raffle.RaffleService;
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

    // Raffle system core (new)
    private RafflePool rafflePool;
    private RaffleService raffleService;

    // Track active bosses per world (UUID key = world UUID)
    private final Map<UUID, Integer> activeBossesByWorld = new ConcurrentHashMap<>();

    // Track despawn tasks per boss entity
    private final Map<UUID, BukkitTask> despawnTasks = new ConcurrentHashMap<>();

    // Keep a reference to spawn listener so BecCommand can call onBossCreated/onBossRemoved
    private SpawnBossListener spawnBossListener;

    // Keep reference so we can stop the scheduled task cleanly
    private PotionAddOnListener potionAddOnListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();

        bossKey = new NamespacedKey(this, "is_boss");
        bossApplier = new BossApplier(this);

        // -------------------------
        // Raffle init (NEW)
        // -------------------------
        RaffleKeys.init(this);
        RaffleTokenFactory.init(this);
        rafflePool = new RafflePool(this);
        rafflePool.reloadFromConfig();
        raffleService = new RaffleService(rafflePool);

        // Register listeners (store spawn listener reference)
        spawnBossListener = new SpawnBossListener(this);

        getServer().getPluginManager().registerEvents(new com.orangeslices.bossencounters.raffle.RaffleApplyListener(this), this);
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

        // Cancel any scheduled boss despawns
        for (UUID id : despawnTasks.keySet()) {
            cancelBossDespawn(id);
        }
        despawnTasks.clear();

        // Reset counts (not required, but keeps state clean)
        activeBossesByWorld.clear();

        getLogger().info("BossEncounters disabled.");
    }

    public NamespacedKey bossKey() {
        return bossKey;
    }

    public BossApplier bossApplier() {
        return bossApplier;
    }

    // -------------------------
    // Raffle getters (NEW)
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
       Boss cap + tracking (for BecCommand + sanity)
       ------------------------- */

    public boolean canCreateBossIn(World world) {
        if (world == null) return false;

        int cap = getMaxBossesPerWorld();
        if (cap <= 0) return true; // 0 = unlimited

        return getActiveBossCount(world) < cap;
    }

    public int getMaxBossesPerWorld() {
        return getConfig().getInt("boss.cap.max_per_world", 0);
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
}
