package com.orangeslices.bossencounters.raffle.listeners;

import com.orangeslices.bossencounters.raffle.RaffleKeys;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseArmorEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bukkit-safe armor equip detection.
 *
 * We DO NOT rely on Paper events.
 * We detect changes by snapshotting armor contents per-player and comparing after
 * common actions that change armor: clicks, drags, right-click equip, join, respawn, etc.
 */
public final class ArmorEquipListener implements Listener {

    private final Plugin plugin;

    // uuid -> last known armor snapshot (helmet, chest, legs, boots)
    private final Map<UUID, ItemStack[]> lastArmor = new ConcurrentHashMap<>();

    // uuid -> (effectId -> lastTriggerMillis)
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();

    // Mandator cow: only 1 active per player
    private final Map<UUID, UUID> activeMandatorCow = new ConcurrentHashMap<>();

    public ArmorEquipListener(Plugin plugin) {
        this.plugin = plugin;
        RaffleKeys.init(plugin);
    }

    // -------------------------
    // Event triggers (schedule compare)
    // -------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent e) {
        snapshot(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent e) {
        lastArmor.remove(e.getPlayer().getUniqueId());
        cooldowns.remove(e.getPlayer().getUniqueId());
        activeMandatorCow.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRespawn(PlayerRespawnEvent e) {
        scheduleCompare(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        scheduleCompare(p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        scheduleCompare(p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        // Right-click equipping armor from hand can happen here
        scheduleCompare(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent e) {
        // Some clients equip via hotbar swapping + click sequences; cheap to check
        scheduleCompare(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent e) {
        scheduleCompare(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDispenseArmor(BlockDispenseArmorEvent e) {
        if (e.getTargetEntity() instanceof Player p) {
            scheduleCompare(p);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent e) {
        // armor changes to empty; snapshot so we don't “trigger” on forced removal
        snapshot(e.getEntity());
    }

    private void scheduleCompare(Player p) {
        // Compare 1 tick later so inventory state is finalized
        Bukkit.getScheduler().runTask(plugin, () -> compareAndTrigger(p));
    }

    // -------------------------
    // Snapshot + compare
    // -------------------------

    private void snapshot(Player p) {
        lastArmor.put(p.getUniqueId(), getArmorSnapshot(p));
    }

    private void compareAndTrigger(Player p) {
        UUID uuid = p.getUniqueId();
        ItemStack[] prev = lastArmor.get(uuid);
        ItemStack[] curr = getArmorSnapshot(p);

        if (prev == null) {
            lastArmor.put(uuid, curr);
            return;
        }

        // For each slot, if it changed and now has armor, treat as “equipped”
        for (int i = 0; i < 4; i++) {
            if (!isSame(prev[i], curr[i]) && isArmor(curr[i])) {
                onArmorEquipped(p, curr[i]);
            }
        }

        lastArmor.put(uuid, curr);
    }

    private ItemStack[] getArmorSnapshot(Player p) {
        ItemStack[] a = p.getInventory().getArmorContents();
        // Bukkit returns array [boots, leggings, chestplate, helmet] in many versions.
        // To make it consistent for our compare, normalize to [helmet, chest, legs, boots].
        ItemStack boots = safeClone(a, 0);
        ItemStack legs = safeClone(a, 1);
        ItemStack chest = safeClone(a, 2);
        ItemStack helm = safeClone(a, 3);

        return new ItemStack[]{helm, chest, legs, boots};
    }

    private ItemStack safeClone(ItemStack[] arr, int idx) {
        if (arr == null || idx < 0 || idx >= arr.length || arr[idx] == null) return null;
        return arr[idx].clone();
    }

    private boolean isSame(ItemStack a, ItemStack b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        // isSimilar ignores stack size; perfect for our purpose
        return a.isSimilar(b);
    }

    private boolean isArmor(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        String name = item.getType().name();
        return name.endsWith("_HELMET")
                || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS");
    }

    // -------------------------
    // Equip action: read PDC + (currently) minimal triggers
    // -------------------------

    private void onArmorEquipped(Player player, ItemStack armorPiece) {
        ItemMeta meta = armorPiece.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String encoded = pdc.get(RaffleKeys.ARMOR_EFFECTS, PersistentDataType.STRING);
        if (encoded == null || encoded.trim().isEmpty()) return;

        Map<String, Integer> effects = decodeEffects(encoded);
        if (effects.isEmpty()) return;

        // IMPORTANT: This is still scaffolding.
        // We can later switch this to "lore-only verification" if you want zero gameplay impact.
        for (Map.Entry<String, Integer> e : effects.entrySet()) {
            triggerEffect(player, e.getKey(), Math.max(1, e.getValue()));
        }
    }

    /**
     * Encoded format: id:lvl|id:lvl|id:lvl
     */
    private Map<String, Integer> decodeEffects(String encoded) {
        Map<String, Integer> out = new HashMap<>();
        String[] parts = encoded.split("\\|");
        for (String part : parts) {
            String p = part.trim();
            if (p.isEmpty()) continue;

            String[] kv = p.split(":");
            String id = kv[0].trim().toLowerCase();
            if (id.isEmpty()) continue;

            int lvl = 1;
            if (kv.length >= 2) {
                try { lvl = Integer.parseInt(kv[1].trim()); } catch (NumberFormatException ignored) {}
            }
            out.put(id, Math.max(1, lvl));
        }
        return out;
    }

    private void triggerEffect(Player player, String idRaw, int level) {
        String id = (idRaw == null ? "" : idRaw.trim().toLowerCase());
        if (id.isEmpty()) return;

        long now = System.currentTimeMillis();
        long cdMs = getCooldownMs(id);

        Map<String, Long> map = cooldowns.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>());
        long last = map.getOrDefault(id, 0L);
        if (cdMs > 0 && (now - last) < cdMs) return;
        map.put(id, now);

        switch (id) {
            // GOOD (simple placeholders)
            case "health_boost" -> {
                int amp = Math.min(3, Math.max(0, level - 1));
                player.addPotionEffect(new PotionEffect(PotionEffectType.HEALTH_BOOST, 20 * 60, amp, true, false, true));
                double max = player.getAttribute(Attribute.MAX_HEALTH) != null
                        ? Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH)).getValue()
                        : 20.0;
                player.setHealth(Math.min(max, player.getHealth() + 2.0));
                player.sendMessage("§a[Raffle] Health Boost triggered (lvl " + level + ").");
            }
            case "fire_res" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 20 * 60, 0, true, false, true));
                player.sendMessage("§a[Raffle] Fire Resistance triggered.");
            }
            case "water_breathing" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 20 * 60, 0, true, false, true));
                player.sendMessage("§a[Raffle] Water Breathing triggered.");
            }

            // CURSES (simple placeholders)
            case "terror" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 20 * 20, 0, true, false, true));
                player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_ROAR, 0.8f, 1.0f);
                player.sendMessage("§c[Raffle] Terror...");
            }
            case "echoes" -> {
                player.sendMessage("§c[Raffle] Echoes whisper nearby...");
                playEchoBurst(player);
            }
            case "broken_compass" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 20 * 4, 0, true, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 2, 0, true, false, true));
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
                player.sendMessage("§c[Raffle] You feel watched... (Shadow Twin)");
            }
            default -> player.sendMessage("§7[Raffle] (Unimplemented effect: " + id + ")");
        }
    }

    private long getCooldownMs(String id) {
        FileConfiguration cfg = plugin.getConfig();

        long v = cfg.getLong("raffle.cooldowns." + id + "_ms", -1L);
        if (v >= 0) return v;

        v = cfg.getLong("raffle.cooldowns." + id, -1L);
        if (v >= 0) return v;

        return switch (id) {
            case "terror" -> 120_000L;
            case "echoes" -> 45_000L;
            case "broken_compass" -> 45_000L;
            case "mandators_curse" -> 120_000L;
            case "ogre" -> 90_000L;
            case "shadow_twin" -> 120_000L;
            default -> 10_000L;
        };
    }

    private void playEchoBurst(Player player) {
        List<Sound> sounds = List.of(
                Sound.AMBIENT_CAVE,
                Sound.ENTITY_ENDERMAN_STARE,
                Sound.ENTITY_PHANTOM_AMBIENT,
                Sound.ENTITY_WARDEN_AMBIENT,
                Sound.BLOCK_SCULK_SHRIEKER_SHRIEK
        );

        new BukkitRunnable() {
            int played = 0;
            final Random r = new Random();

            @Override public void run() {
                if (!player.isOnline()) { cancel(); return; }
                Sound s = sounds.get(r.nextInt(sounds.size()));
                player.playSound(player.getLocation(), s, 0.7f, 0.9f + (r.nextFloat() * 0.2f));
                played++;
                if (played >= 6) cancel();
            }
        }.runTaskTimer(plugin, 0L, 40L);
    }

    private void spawnMandatorCow(Player player) {
        UUID uuid = player.getUniqueId();
        UUID existing = activeMandatorCow.get(uuid);
        if (existing != null) {
            Entity ent = Bukkit.getEntity(existing);
            if (ent != null && !ent.isDead()) return;
        }

        Location spawnAt = player.getLocation().clone().add(player.getLocation().getDirection().normalize().multiply(-2));
        Cow cow = (Cow) player.getWorld().spawnEntity(spawnAt, EntityType.COW);
        activeMandatorCow.put(uuid, cow.getUniqueId());

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
                    cow.setVelocity(dir.multiply(0.35));
                }

                if (dist <= 1.3) {
                    Vector kb = pLoc.toVector().subtract(cLoc.toVector()).normalize().multiply(1.2);
                    kb.setY(0.35);
                    player.setVelocity(kb);
                    player.damage(1.0);
                }
            }

            private void cleanup() {
                activeMandatorCow.remove(uuid);
                cancel();
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private void ogreRush(Player player) {
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
                    a.setVelocity(dir.normalize().multiply(0.25));
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }
}
