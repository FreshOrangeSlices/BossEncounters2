package com.orangeslices.bossencounters.raffle.listener;

import com.orangeslices.bossencounters.raffle.RaffleKeys;
import io.papermc.paper.event.player.PlayerArmorChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ArmorEquipListener implements Listener {

    private final Plugin plugin;

    // uuid -> (effectId -> lastTriggerMillis)
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();

    // Mandator Cow: only 1 active per player
    private final Map<UUID, UUID> activeMandatorCow = new ConcurrentHashMap<>();

    public ArmorEquipListener(Plugin plugin) {
        this.plugin = plugin;
        RaffleKeys.init(plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onArmorChange(PlayerArmorChangeEvent event) {
        Player player = event.getPlayer();
        ItemStack newItem = event.getNewItem();

        if (newItem == null || newItem.getType() == Material.AIR) return;

        // Only trigger when actually equipping armor
        if (!isArmor(newItem)) return;

        ItemMeta meta = newItem.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String encoded = pdc.get(RaffleKeys.ARMOR_EFFECTS, PersistentDataType.STRING);
        if (encoded == null || encoded.trim().isEmpty()) return;

        Map<String, Integer> effects = decodeEffects(encoded);
        if (effects.isEmpty()) return;

        // Fire ALL effects stored on that piece, once, on equip
        for (Map.Entry<String, Integer> e : effects.entrySet()) {
            String effectId = e.getKey();
            int level = Math.max(1, e.getValue());
            triggerEffect(player, effectId, level);
        }
    }

    // ----------------------------
    // Effect execution + cooldown
    // ----------------------------

    private void triggerEffect(Player player, String idRaw, int level) {
        String id = (idRaw == null ? "" : idRaw.trim().toLowerCase());
        if (id.isEmpty()) return;

        long now = System.currentTimeMillis();
        long cdMs = getCooldownMs(id);

        // cooldown check
        Map<String, Long> map = cooldowns.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>());
        long last = map.getOrDefault(id, 0L);
        if (cdMs > 0 && (now - last) < cdMs) return;

        map.put(id, now);

        switch (id) {
            // ----------------
            // GOOD effects
            // ----------------
            case "health_boost" -> {
                // Trigger-only: gives a temporary buff; re-equip can refresh (cooldown prevents spam)
                // Level scales amplifier (lvl1=0, lvl2=1, etc), clamped.
                int amp = Math.min(3, Math.max(0, level - 1));
                int duration = 20 * 60; // 60s
                player.addPotionEffect(new PotionEffect(PotionEffectType.HEALTH_BOOST, duration, amp, true, false, true));
                // Heal up to new max a little (nice UX)
                double max = player.getAttribute(Attribute.MAX_HEALTH) != null
                        ? Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH)).getValue()
                        : 20.0;
                player.setHealth(Math.min(max, player.getHealth() + 2.0));
                player.sendMessage("§a[Raffle] Health Boost triggered (lvl " + level + ").");
            }
            case "fire_res" -> {
                int duration = 20 * 60; // 60s
                player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, duration, 0, true, false, true));
                player.sendMessage("§a[Raffle] Fire Resistance triggered.");
            }
            case "water_breathing" -> {
                int duration = 20 * 60; // 60s
                player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, duration, 0, true, false, true));
                player.sendMessage("§a[Raffle] Water Breathing triggered.");
            }

            // ----------------
            // CURSES (trigger on equip only)
            // ----------------
            case "terror" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 20 * 20, 0, true, false, true)); // 20s
                player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_ROAR, 0.8f, 1.0f);
                player.sendMessage("§c[Raffle] Terror...");
            }
            case "echoes" -> {
                player.sendMessage("§c[Raffle] Echoes whisper nearby...");
                playEchoBurst(player);
            }
            case "broken_compass" -> {
                // Short disorientation burst
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 20 * 4, 0, true, false, true)); // 4s
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 2, 0, true, false, true)); // 2s
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_AMBIENT, 0.6f, 0.8f);
                player.sendMessage("§c[Raffle] Your senses twist...");
            }
            case "mandators_curse" -> {
                player.sendMessage("§c[Raffle] Mandator’s Curse!");
                spawnMandatorCow(player);
            }
            case "ogre" -> {
                player.sendMessage("§c[Raffle] An ogre’s malice stirs the nearby wildlife...");
                ogreRush(player);
            }
            case "shadow_twin" -> {
                // Placeholder-lite implementation (safe for no-deps)
                player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, 0.7f, 0.7f);
                player.sendMessage("§c[Raffle] You feel watched... (Shadow Twin)");
            }

            default -> {
                // Unknown IDs won’t break the server
                player.sendMessage("§7[Raffle] (Unimplemented effect: " + id + ")");
            }
        }
    }

    private long getCooldownMs(String id) {
        FileConfiguration cfg = plugin.getConfig();

        // Allow either style:
        // raffle.cooldowns.terror_ms: 120000
        // raffle.cooldowns.terror: 120000
        long v = cfg.getLong("raffle.cooldowns." + id + "_ms", -1L);
        if (v >= 0) return v;

        v = cfg.getLong("raffle.cooldowns." + id, -1L);
        if (v >= 0) return v;

        // sane defaults (anti-spam)
        return switch (id) {
            case "terror" -> 120_000L;          // 2 min
            case "echoes" -> 45_000L;           // 45s
            case "broken_compass" -> 45_000L;   // 45s
            case "mandators_curse" -> 120_000L; // 2 min
            case "ogre" -> 90_000L;             // 90s
            case "shadow_twin" -> 120_000L;     // 2 min
            case "health_boost", "fire_res", "water_breathing" -> 10_000L; // 10s
            default -> 10_000L;
        };
    }

    // ----------------------------
    // Curse implementations
    // ----------------------------

    private void playEchoBurst(Player player) {
        // 6 quick “creepy” sounds over ~12 seconds
        List<Sound> sounds = List.of(
                Sound.AMBIENT_CAVE,
                Sound.ENTITY_ENDERMAN_STARE,
                Sound.ENTITY_PHANTOM_AMBIENT,
                Sound.ENTITY_WARDEN_AMBIENT,
                Sound.BLOCK_SCULK_SHRIEKER_SHRIEK
        );

        new BukkitRunnable() {
            int ticks = 0;
            int played = 0;

            @Override public void run() {
                if (!player.isOnline()) { cancel(); return; }
                ticks += 20;

                Sound s = sounds.get(new Random().nextInt(sounds.size()));
                player.playSound(player.getLocation(), s, 0.7f, 0.9f + (new Random().nextFloat() * 0.2f));
                played++;

                if (played >= 6) cancel();
            }
        }.runTaskTimer(plugin, 0L, 40L); // every 2s
    }

    private void spawnMandatorCow(Player player) {
        // Only one active at a time
        UUID uuid = player.getUniqueId();
        UUID existing = activeMandatorCow.get(uuid);
        if (existing != null) {
            Entity ent = Bukkit.getEntity(existing);
            if (ent != null && !ent.isDead()) return;
        }

        Location spawnAt = player.getLocation().clone().add(player.getLocation().getDirection().normalize().multiply(-2));
        Cow cow = (Cow) player.getWorld().spawnEntity(spawnAt, EntityType.COW);
        cow.setCustomNameVisible(false);

        activeMandatorCow.put(uuid, cow.getUniqueId());

        // Chase for 20s, knockback on touch
        new BukkitRunnable() {
            int lifeTicks = 0;

            @Override public void run() {
                if (!player.isOnline() || cow.isDead()) { cleanup(); return; }

                lifeTicks += 5;
                if (lifeTicks >= 20 * 20) { // 20s
                    cow.remove();
                    cleanup();
                    return;
                }

                Location pLoc = player.getLocation();
                Location cLoc = cow.getLocation();

                Vector dir = pLoc.toVector().subtract(cLoc.toVector());
                double dist = dir.length();

                if (dist > 0.1) {
                    dir.normalize();
                    // “Speed 2-ish” feel without pathfinding spam
                    cow.setVelocity(dir.multiply(0.35));
                }

                if (dist <= 1.3) {
                    Vector kb = pLoc.toVector().subtract(cLoc.toVector()).normalize().multiply(1.2);
                    kb.setY(0.35);
                    player.setVelocity(kb);
                    player.damage(1.0); // tiny sting
                    player.playSound(player.getLocation(), Sound.ENTITY_COW_HURT, 0.9f, 0.8f);
                }
            }

            private void cleanup() {
                activeMandatorCow.remove(uuid);
                cancel();
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private void ogreRush(Player player) {
        // For 15s, push up to 10 nearby passive mobs toward the player (lightweight, capped)
        int durationTicks = 15 * 20;
        int radius = 24;
        int maxMobs = 10;

        new BukkitRunnable() {
            int t = 0;

            @Override public void run() {
                if (!player.isOnline()) { cancel(); return; }
                t += 10;
                if (t >= durationTicks) { cancel(); return; }

                List<Animals> targets = new ArrayList<>();
                for (Entity e : player.getNearbyEntities(radius, radius, radius)) {
                    if (e instanceof Animals a) {
                        targets.add(a);
                        if (targets.size() >= maxMobs) break;
                    }
                }

                Location pLoc = player.getLocation();
                for (Animals a : targets) {
                    if (a.isDead()) continue;
                    Vector dir = pLoc.toVector().subtract(a.getLocation().toVector());
                    if (dir.length() < 0.1) continue;
                    dir.normalize();
                    a.setVelocity(dir.multiply(0.25));
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    // ----------------------------
    // Utilities
    // ----------------------------

    private boolean isArmor(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        String name = item.getType().name();
        return name.endsWith("_HELMET")
                || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS");
    }

    /**
     * Encoded format: id:lvl|id:lvl|id:lvl
     */
    private Map<String, Integer> decodeEffects(String encoded) {
        Map<String, Integer> out = new HashMap<>();
        if (encoded == null || encoded.trim().isEmpty()) return out;

        String[] parts = encoded.split("\\|");
        for (String part : parts) {
            String p = part.trim();
            if (p.isEmpty()) continue;

            String[] kv = p.split(":");
            String id = kv[0].trim().toLowerCase();
            if (id.isEmpty()) continue;

            int lvl = 1;
            if (kv.length >= 2) {
                try { lvl = Integer.parseInt(kv[1].trim()); }
                catch (NumberFormatException ignored) {}
            }
            out.put(id, Math.max(1, lvl));
        }
        return out;
    }
}
