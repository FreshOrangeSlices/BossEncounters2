package com.orangeslices.bossencounters;

import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class AffixListener implements Listener {

    private final BossEncountersPlugin plugin;

    // cooldown tracking: key = bossUUID + ":" + affixId
    private final ConcurrentHashMap<String, Long> lastProc = new ConcurrentHashMap<>();

    // mark storage on targets
    private final NamespacedKey markUntilKey;

    public AffixListener(BossEncountersPlugin plugin) {
        this.plugin = plugin;
        this.markUntilKey = new NamespacedKey(plugin, "mark_until");
    }

    /* -------------------------
       Utility
       ------------------------- */

    private static boolean isAlive(LivingEntity e) {
        return e != null && e.isValid() && !e.isDead() && e.getHealth() > 0.0;
    }

    private boolean hasAffix(String csv, String id) {
        String[] parts = csv.split(",");
        for (String p : parts) {
            if (p != null && p.trim().equalsIgnoreCase(id)) return true;
        }
        return false;
    }

    private boolean isAffixEnabled(FileConfiguration cfg, String id) {
        // default true if not specified
        return cfg.getBoolean("affixes.pool." + id + ".enabled", true);
    }

    private double rankScale(LivingEntity boss) {
        // Reads: affixes.rank_scaling.<RANK>
        // If missing, defaults to 1.0
        String rank = plugin.bossApplier().getRank(boss);
        if (rank == null || rank.isBlank()) return 1.0;
        return plugin.getConfig().getDouble("affixes.rank_scaling." + rank, 1.0);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private boolean shouldProc(LivingEntity boss, String affixId, double chance, long cooldownMs) {
        if (chance < 1.0 && ThreadLocalRandom.current().nextDouble() > chance) return false;
        if (!cooldownOk(boss, affixId, cooldownMs)) return false;
        setLastProc(boss, affixId);
        return true;
    }

    private boolean cooldownOk(LivingEntity boss, String affixId, long cooldownMs) {
        if (cooldownMs <= 0) return true;
        long now = System.currentTimeMillis();
        String key = boss.getUniqueId() + ":" + affixId;
        long last = lastProc.getOrDefault(key, 0L);
        return (now - last) >= cooldownMs;
    }

    private void setLastProc(LivingEntity boss, String affixId) {
        String key = boss.getUniqueId() + ":" + affixId;
        lastProc.put(key, System.currentTimeMillis());
    }

    /* -------------------------
       ON ATTACK (boss hits)
       ------------------------- */

    @EventHandler
    public void onBossAttack(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getDamager() instanceof LivingEntity boss)) return;
        if (!plugin.bossApplier().isBoss(boss)) return;

        String affixes = plugin.bossApplier().getAffixesString(boss);
        if (affixes == null || affixes.isBlank()) return;

        LivingEntity target = (event.getEntity() instanceof LivingEntity le) ? le : null;
        if (target == null || !isAlive(target)) return;

        FileConfiguration cfg = plugin.getConfig();
        double scale = rankScale(boss);
        long now = System.currentTimeMillis();

        // MARK bonus consumption (if target is marked)
        {
            PersistentDataContainer tpdc = target.getPersistentDataContainer();
            Long until = tpdc.get(markUntilKey, PersistentDataType.LONG);
            if (until != null && until > now) {
                // Scale the *extra* part, not the whole multiplier:
                // base bonusMult 1.5 -> extra 0.5 -> scales with rank
                double bonusMult = cfg.getDouble("affixes.pool.mark.bonus_damage_multiplier", 1.5);
                double extra = Math.max(0.0, bonusMult - 1.0);
                double scaledMult = 1.0 + (extra * scale);
                scaledMult = clamp(scaledMult, 1.0, 3.0);

                event.setDamage(event.getDamage() * scaledMult);
                tpdc.remove(markUntilKey);

                target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1.0, 0),
                        10, 0.3, 0.4, 0.3, 0.0);
                target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.5f, 1.2f);
            }
        }

        // LIFESTEAL
        if (hasAffix(affixes, "lifesteal") && isAffixEnabled(cfg, "lifesteal")) {
            double chance = cfg.getDouble("affixes.pool.lifesteal.chance", 0.25);
            long cooldownMs = cfg.getLong("affixes.pool.lifesteal.cooldown_ms", 1200L);
            double healPct = cfg.getDouble("affixes.pool.lifesteal.heal_percent_of_damage", 0.20);

            // scale + clamp
            healPct = clamp(healPct * scale, 0.0, 0.55);

            if (shouldProc(boss, "lifesteal", chance, cooldownMs)) {
                double damage = event.getFinalDamage();
                double heal = damage * healPct;

                if (heal > 0 && isAlive(boss)) {
                    AttributeInstance maxHp = boss.getAttribute(Attribute.MAX_HEALTH);
                    double max = (maxHp != null) ? maxHp.getValue() : boss.getHealth();
                    double newHealth = Math.min(max, boss.getHealth() + heal);

                    if (newHealth > boss.getHealth()) {
                        boss.setHealth(newHealth);
                        boss.getWorld().spawnParticle(Particle.HEART, boss.getLocation().add(0, 1.2, 0),
                                6, 0.3, 0.4, 0.3, 0.0);
                        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.35f, 1.8f);
                    }
                }
            }
        }

        // KNOCKBACK
        if (hasAffix(affixes, "knockback") && isAffixEnabled(cfg, "knockback")) {
            double chance = cfg.getDouble("affixes.pool.knockback.chance", 0.30);
            long cooldownMs = cfg.getLong("affixes.pool.knockback.cooldown_ms", 1000L);
            double strength = cfg.getDouble("affixes.pool.knockback.strength", 1.2);
            double upward = cfg.getDouble("affixes.pool.knockback.upward", 0.35);

            // scale + clamp (avoid yeeting into orbit)
            strength = clamp(strength * (0.85 + 0.15 * scale), 0.2, 2.2);
            upward = clamp(upward * (0.90 + 0.10 * scale), 0.05, 0.65);

            if (shouldProc(boss, "knockback", chance, cooldownMs) && isAlive(target)) {
                Vector away = target.getLocation().toVector()
                        .subtract(boss.getLocation().toVector())
                        .normalize()
                        .multiply(strength);
                away.setY(upward);

                target.setVelocity(away);
                target.getWorld().spawnParticle(Particle.CLOUD, target.getLocation().add(0, 1.0, 0),
                        10, 0.3, 0.2, 0.3, 0.02);
                target.getWorld().playSound(target.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.5f, 1.4f);
            }
        }

        // BLEED (WITHER)
        if (hasAffix(affixes, "bleed") && isAffixEnabled(cfg, "bleed")) {
            double chance = cfg.getDouble("affixes.pool.bleed.chance", 0.25);
            long cooldownMs = cfg.getLong("affixes.pool.bleed.cooldown_ms", 1200L);
            int duration = cfg.getInt("affixes.pool.bleed.duration_ticks", 60);
            int amplifier = cfg.getInt("affixes.pool.bleed.amplifier", 0);

            // scale duration; optionally bump amplifier at high ranks
            int scaledDuration = (int) Math.round(duration * scale);
            scaledDuration = Math.max(1, Math.min(240, scaledDuration)); // cap at 12s

            int amp = Math.max(0, amplifier);
            if (scale >= 1.45) amp += 1;          // PURPLE+
            if (scale >= 1.70) amp += 1;          // GOLD+ (another bump)
            amp = Math.min(3, amp);               // keep sane

            if (shouldProc(boss, "bleed", chance, cooldownMs) && isAlive(target)) {
                target.addPotionEffect(new PotionEffect(
                        PotionEffectType.WITHER,
                        scaledDuration,
                        amp,
                        true, true, true
                ));

                target.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, target.getLocation().add(0, 1.0, 0),
                        12, 0.3, 0.4, 0.3, 0.0);
                target.getWorld().playSound(target.getLocation(), Sound.ENTITY_WITHER_HURT, 0.3f, 1.7f);
            }
        }

        // PULL
        if (hasAffix(affixes, "pull") && isAffixEnabled(cfg, "pull")) {
            double chance = cfg.getDouble("affixes.pool.pull.chance", 0.20);
            long cooldownMs = cfg.getLong("affixes.pool.pull.cooldown_ms", 1400L);
            double force = cfg.getDouble("affixes.pool.pull.force", 0.8);

            // scale + clamp
            force = clamp(force * (0.85 + 0.15 * scale), 0.1, 1.35);

            if (shouldProc(boss, "pull", chance, cooldownMs) && isAlive(target)) {
                Vector toward = boss.getLocation().toVector()
                        .subtract(target.getLocation().toVector())
                        .normalize()
                        .multiply(force);

                toward.setY(Math.min(0.25, toward.getY() + 0.15));
                target.setVelocity(toward);

                target.getWorld().spawnParticle(Particle.PORTAL, target.getLocation().add(0, 1.0, 0),
                        18, 0.4, 0.4, 0.4, 0.05);
                target.getWorld().playSound(target.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.25f, 2.0f);
            }
        }

        // MARK apply
        if (hasAffix(affixes, "mark") && isAffixEnabled(cfg, "mark")) {
            double chance = cfg.getDouble("affixes.pool.mark.chance", 0.25);
            long cooldownMs = cfg.getLong("affixes.pool.mark.cooldown_ms", 1200L);
            long durationMs = cfg.getLong("affixes.pool.mark.mark_duration_ms", 3000L);

            // scale duration a bit, but cap it so it doesn't feel unfair
            long scaledDurationMs = (long) Math.round(durationMs * (0.85 + 0.15 * scale));
            scaledDurationMs = Math.max(500L, Math.min(6000L, scaledDurationMs));

            if (shouldProc(boss, "mark", chance, cooldownMs) && isAlive(target)) {
                long until = now + Math.max(250L, scaledDurationMs);
                target.getPersistentDataContainer().set(markUntilKey, PersistentDataType.LONG, until);

                target.getWorld().spawnParticle(Particle.GLOW, target.getLocation().add(0, 1.0, 0),
                        10, 0.3, 0.4, 0.3, 0.0);
                target.getWorld().playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.35f, 1.4f);
            }
        }
    }

    /* -------------------------
       ON HURT (boss gets hit)
       ------------------------- */

    @EventHandler
    public void onBossHurt(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof LivingEntity boss)) return;
        if (!plugin.bossApplier().isBoss(boss)) return;

        String affixes = plugin.bossApplier().getAffixesString(boss);
        if (affixes == null || affixes.isBlank()) return;

        FileConfiguration cfg = plugin.getConfig();
        double scale = rankScale(boss);

        // THORNS
        if (hasAffix(affixes, "thorns") && isAffixEnabled(cfg, "thorns")
                && event.getDamager() instanceof LivingEntity attacker && isAlive(attacker)) {

            double chance = cfg.getDouble("affixes.pool.thorns.chance", 0.35);
            long cooldownMs = cfg.getLong("affixes.pool.thorns.cooldown_ms", 1000L);
            double reflectPct = cfg.getDouble("affixes.pool.thorns.reflect_percent", 0.25);

            // scale + clamp (reflect can get toxic fast)
            reflectPct = clamp(reflectPct * (0.85 + 0.15 * scale), 0.0, 0.55);

            if (shouldProc(boss, "thorns", chance, cooldownMs) && isAlive(boss)) {
                double reflect = event.getFinalDamage() * reflectPct;
                if (reflect > 0.0 && isAlive(attacker)) {
                    attacker.damage(reflect, boss);
                    attacker.getWorld().spawnParticle(Particle.SWEEP_ATTACK, attacker.getLocation().add(0, 1.0, 0),
                            1, 0, 0, 0, 0);
                    attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.45f, 1.8f);
                }
            }
        }

        // LETHAL SAFETY: finish death next tick if needed (prevents "invincible corpse" edge cases)
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (boss.isValid() && !boss.isDead() && boss.getHealth() <= 0.0) {
                boss.damage(1000.0);
            }
        });
    }

    /* -------------------------
       SHOCKWAVE (on any damage)
       ------------------------- */

    @EventHandler
    public void onBossDamagedAny(EntityDamageEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof LivingEntity boss)) return;
        if (!plugin.bossApplier().isBoss(boss)) return;
        if (!isAlive(boss)) return;

        String affixes = plugin.bossApplier().getAffixesString(boss);
        if (affixes == null || affixes.isBlank() || !hasAffix(affixes, "shockwave")) return;

        FileConfiguration cfg = plugin.getConfig();
        if (!isAffixEnabled(cfg, "shockwave")) return;

        double scale = rankScale(boss);

        double chance = cfg.getDouble("affixes.pool.shockwave.chance", 0.15);
        long cooldownMs = cfg.getLong("affixes.pool.shockwave.cooldown_ms", 2500L);
        double radius = cfg.getDouble("affixes.pool.shockwave.radius", 4.0);
        int maxTargets = cfg.getInt("affixes.pool.shockwave.max_targets", 4);
        double damage = cfg.getDouble("affixes.pool.shockwave.damage", 4.0);
        double knockback = cfg.getDouble("affixes.pool.shockwave.knockback", 1.1);

        // scale + clamp
        radius = clamp(radius * (0.90 + 0.10 * scale), 2.0, 7.0);
        damage = clamp(damage * scale, 0.0, 14.0);
        knockback = clamp(knockback * (0.85 + 0.15 * scale), 0.2, 2.1);

        if (!shouldProc(boss, "shockwave", chance, cooldownMs)) return;

        List<LivingEntity> targets = new ArrayList<>();
        for (Entity e : boss.getNearbyEntities(radius, radius, radius)) {
            if (e == boss) continue;
            if (!(e instanceof LivingEntity le)) continue;
            if (!isAlive(le)) continue;
            targets.add(le);
        }

        targets.sort(Comparator.comparingDouble(le -> le.getLocation().distanceSquared(boss.getLocation())));

        int hit = 0;
        for (LivingEntity t : targets) {
            if (hit >= maxTargets) break;
            if (!isAlive(t)) continue;

            Vector away = t.getLocation().toVector()
                    .subtract(boss.getLocation().toVector())
                    .normalize()
                    .multiply(knockback);
            away.setY(Math.max(0.25, away.getY() + 0.25));

            t.setVelocity(away);
            if (damage > 0) t.damage(damage, boss);
            hit++;
        }

        boss.getWorld().spawnParticle(Particle.EXPLOSION, boss.getLocation().add(0, 0.5, 0),
                1, 0, 0, 0, 0);
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.35f, 1.8f);
    }

    /* -------------------------
       ON TARGET CHANGE
       ------------------------- */

    @EventHandler
    public void onBossTarget(EntityTargetLivingEntityEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof LivingEntity boss)) return;
        if (!plugin.bossApplier().isBoss(boss)) return;

        LivingEntity target = event.getTarget();
        if (target == null || !isAlive(target)) return;

        String affixes = plugin.bossApplier().getAffixesString(boss);
        if (affixes == null || affixes.isBlank() || !hasAffix(affixes, "intimidate")) return;

        FileConfiguration cfg = plugin.getConfig();
        if (!isAffixEnabled(cfg, "intimidate")) return;

        double scale = rankScale(boss);

        long cooldownMs = cfg.getLong("affixes.pool.intimidate.cooldown_ms", 3000L);
        int duration = cfg.getInt("affixes.pool.intimidate.duration_ticks", 40);
        int amplifier = cfg.getInt("affixes.pool.intimidate.amplifier", 0);
        String effectName = cfg.getString("affixes.pool.intimidate.effect", "SLOWNESS");

        if (!cooldownOk(boss, "intimidate", cooldownMs)) return;

        // scale duration; optionally bump amplifier at higher ranks
        int scaledDuration = (int) Math.round(duration * (0.85 + 0.15 * scale));
        scaledDuration = Math.max(1, Math.min(240, scaledDuration));

        int amp = Math.max(0, amplifier);
        if (scale >= 1.45) amp += 1;
        if (scale >= 1.70) amp += 1;
        amp = Math.min(3, amp);

        PotionEffectType type = PotionEffectType.SLOWNESS;
        PotionEffectType parsed = PotionEffectType.getByName(effectName == null ? "" : effectName.toUpperCase());
        if (parsed != null) type = parsed;

        target.addPotionEffect(new PotionEffect(type, scaledDuration, amp, true, true, true));
        target.getWorld().spawnParticle(Particle.SMOKE, target.getLocation().add(0, 1.0, 0),
                12, 0.35, 0.45, 0.35, 0.01);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_ENDERMAN_STARE, 0.25f, 1.2f);

        setLastProc(boss, "intimidate");
    }
}
