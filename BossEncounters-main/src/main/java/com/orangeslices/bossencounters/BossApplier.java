package com.orangeslices.bossencounters;

import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class BossApplier {

    private final JavaPlugin plugin;

    // PDC keys
    private final NamespacedKey bossKey;
    private final NamespacedKey rankKey;
    private final NamespacedKey affixesKey;
    private final NamespacedKey titleKey;

    public BossApplier(JavaPlugin plugin) {
        this.plugin = plugin;

        this.bossKey = new NamespacedKey(plugin, "is_boss");
        this.rankKey = new NamespacedKey(plugin, "rank");
        this.affixesKey = new NamespacedKey(plugin, "affixes");
        this.titleKey = new NamespacedKey(plugin, "title");
    }

    /* -------------------------
       Boss flags / getters
       ------------------------- */

    public boolean isBoss(LivingEntity entity) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        Byte val = pdc.get(bossKey, PersistentDataType.BYTE);
        return val != null && val == (byte) 1;
    }

    public void markBoss(LivingEntity entity) {
        entity.getPersistentDataContainer().set(bossKey, PersistentDataType.BYTE, (byte) 1);
    }

    public void unmarkBoss(LivingEntity entity) {
        entity.getPersistentDataContainer().remove(bossKey);
    }

    public String getRank(LivingEntity entity) {
        return entity.getPersistentDataContainer().get(rankKey, PersistentDataType.STRING);
    }

    public String getAffixesString(LivingEntity entity) {
        return entity.getPersistentDataContainer().get(affixesKey, PersistentDataType.STRING);
    }

    public String getTitle(LivingEntity entity) {
        return entity.getPersistentDataContainer().get(titleKey, PersistentDataType.STRING);
    }

    public void setRank(LivingEntity entity, String rank) {
        if (rank == null || rank.isBlank()) {
            entity.getPersistentDataContainer().remove(rankKey);
            return;
        }
        entity.getPersistentDataContainer().set(
                rankKey,
                PersistentDataType.STRING,
                rank.trim().toUpperCase(Locale.ROOT)
        );
    }

    public void setAffixes(LivingEntity entity, List<String> affixes) {
        if (affixes == null || affixes.isEmpty()) {
            entity.getPersistentDataContainer().remove(affixesKey);
            entity.getPersistentDataContainer().remove(titleKey);
            return;
        }

        List<String> cleaned = new ArrayList<>();
        for (String a : affixes) {
            if (a == null) continue;
            String id = a.trim().toLowerCase(Locale.ROOT);
            if (!id.isEmpty()) cleaned.add(id);
        }

        if (cleaned.isEmpty()) {
            entity.getPersistentDataContainer().remove(affixesKey);
            entity.getPersistentDataContainer().remove(titleKey);
            return;
        }

        entity.getPersistentDataContainer().set(
                affixesKey,
                PersistentDataType.STRING,
                String.join(",", cleaned)
        );

        // force title rebuild from new affixes
        entity.getPersistentDataContainer().remove(titleKey);
    }

    public boolean isValidAffix(String id) {
        if (id == null || id.isBlank()) return false;
        String key = "affixes.pool." + id.trim().toLowerCase(Locale.ROOT);
        return plugin.getConfig().isConfigurationSection(key);
    }

    public int getMaxAffixesForRank(String rankId) {
        int fallback = Math.max(0, plugin.getConfig().getInt("affixes.max_per_boss", 0));
        if (rankId == null || rankId.isBlank()) return fallback;

        ConfigurationSection rankSec = plugin.getConfig().getConfigurationSection("ranks." + rankId);
        if (rankSec == null) return fallback;

        return Math.max(0, rankSec.getInt("max_affixes", fallback));
    }

    /* -------------------------
       Main apply pipeline
       ------------------------- */

    public void applyBossStats(LivingEntity entity) {
        // Prevent double applying (important for performance & consistency)
        if (isBoss(entity)) return;

        // 1) Get rank (use forced rank if already set)
        String rankId = getRank(entity);
        if (rankId == null || rankId.isBlank()) {
            rankId = rollRankId();
            if (rankId != null && !rankId.isBlank()) {
                entity.getPersistentDataContainer().set(rankKey, PersistentDataType.STRING, rankId);
            }
        }

        // 2) Resolve multipliers (rank overrides fallback stats.*)
        double hpMult = getRankOrDefaultDouble(rankId, "stats.health_multiplier",
                plugin.getConfig().getDouble("stats.health_multiplier", 4.0));

        double dmgMult = getRankOrDefaultDouble(rankId, "stats.damage_multiplier",
                plugin.getConfig().getDouble("stats.damage_multiplier", 1.5));

        double spdMult = getRankOrDefaultDouble(rankId, "stats.speed_multiplier",
                plugin.getConfig().getDouble("stats.speed_multiplier", 1.0));

        // 3) Max affixes (rank overrides)
        int maxAffixes = getMaxAffixesForRank(rankId);

        // Mark as boss now (so listeners know)
        markBoss(entity);

        // Apply health
        AttributeInstance maxHp = entity.getAttribute(Attribute.MAX_HEALTH);
        if (maxHp != null) {
            double newMax = Math.max(1.0, maxHp.getBaseValue() * hpMult);
            maxHp.setBaseValue(newMax);
            entity.setHealth(newMax);
        }

        // Apply damage
        AttributeInstance dmg = entity.getAttribute(Attribute.ATTACK_DAMAGE);
        if (dmg != null) {
            dmg.setBaseValue(Math.max(0.0, dmg.getBaseValue() * dmgMult));
        }

        // Apply speed
        AttributeInstance spd = entity.getAttribute(Attribute.MOVEMENT_SPEED);
        if (spd != null) {
            spd.setBaseValue(Math.max(0.0, spd.getBaseValue() * spdMult));
        }

        // Affixes (don’t overwrite if already forced)
        applyAffixSelection(entity, maxAffixes);

        // Title derived from first 2 affixes (don’t overwrite if already set)
        applyTitleFromAffixes(entity);

        // Nameplate: rank COLOR + (titles) + base name — NO rank text, NO brackets
        applyNameplate(entity, rankId);
    }

    /* -------------------------
       Nameplate (NO rank text, NO brackets)
       Example: "&6Vampiric Huntsman Boss"
       ------------------------- */

    private void applyNameplate(LivingEntity entity, String rankId) {
        if (!plugin.getConfig().getBoolean("boss.name.enabled", true)) return;

        // Rank color (visual tier)
        String rankColor = plugin.getConfig().getString("boss.name.color", "&c");
        ConfigurationSection rankSec = getRankSection(rankId);
        if (rankSec != null) {
            rankColor = rankSec.getString("color", rankColor);
        }

        // Title from affixes (already first-2 only)
        String title = getTitle(entity);
        String titlePart = "";
        if (title != null && !title.isBlank()) {
            titlePart = title.trim() + " ";
        }

        // Base text (Boss, unless blank -> mob name)
        String baseText = plugin.getConfig().getString("boss.name.text", "Boss");
        if (baseText == null || baseText.isBlank()) {
            baseText = prettyMobName(entity.getType());
        }

        String finalName = ChatColor.translateAlternateColorCodes('&',
                rankColor + titlePart + baseText
        ).trim();

        entity.setCustomName(finalName);
        entity.setCustomNameVisible(true);
    }

    private String prettyMobName(EntityType type) {
        String raw = type.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        String[] parts = raw.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isBlank()) continue;
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }

    /* -------------------------
       Title from affixes (FIRST 2 ONLY)
       ------------------------- */

    private void applyTitleFromAffixes(LivingEntity entity) {
        // don’t overwrite an existing title (important for forced titles later)
        if (getTitle(entity) != null) return;

        String affixesCsv = getAffixesString(entity);
        if (affixesCsv == null || affixesCsv.isBlank()) return;

        String[] affixes = affixesCsv.split(",");
        List<String> words = new ArrayList<>(2);

        for (String a : affixes) {
            if (a == null) continue;
            String id = a.trim().toLowerCase(Locale.ROOT);
            if (id.isEmpty()) continue;

            String word = plugin.getConfig().getString("affix_titles." + id, "");
            if (word != null && !word.isBlank()) {
                words.add(word.trim());
            }

            if (words.size() >= 2) break;
        }

        if (!words.isEmpty()) {
            entity.getPersistentDataContainer().set(
                    titleKey,
                    PersistentDataType.STRING,
                    String.join(" ", words)
            );
        }
    }

    /* -------------------------
       Rank helpers
       ------------------------- */

    private ConfigurationSection getRankSection(String rankId) {
        if (rankId == null || rankId.isBlank()) return null;
        return plugin.getConfig().getConfigurationSection("ranks." + rankId);
    }

    private double getRankOrDefaultDouble(String rankId, String relPath, double fallback) {
        ConfigurationSection rankSec = getRankSection(rankId);
        if (rankSec == null) return fallback;
        return rankSec.getDouble(relPath, fallback);
    }

    private String rollRankId() {
        ConfigurationSection ranks = plugin.getConfig().getConfigurationSection("ranks");
        if (ranks == null) return null;

        List<Map.Entry<String, Integer>> entries = new ArrayList<>();
        for (String id : ranks.getKeys(false)) {
            int w = Math.max(0, ranks.getInt(id + ".weight", 0));
            if (w > 0) entries.add(Map.entry(id, w));
        }
        if (entries.isEmpty()) return null;

        int total = 0;
        for (var e : entries) total += e.getValue();
        if (total <= 0) return null;

        int roll = ThreadLocalRandom.current().nextInt(total);
        int running = 0;
        for (var e : entries) {
            running += e.getValue();
            if (roll < running) return e.getKey();
        }
        return entries.get(entries.size() - 1).getKey();
    }

    /* -------------------------
       Affix selection (unique, weighted, respects forced affixes)
       ------------------------- */

    private void applyAffixSelection(LivingEntity entity, int max) {
        if (!plugin.getConfig().getBoolean("affixes.enabled", true)) return;
        if (max <= 0) return;

        // Respect forced affixes (do not overwrite)
        String existing = getAffixesString(entity);
        if (existing != null && !existing.isBlank()) return;

        ConfigurationSection pool = plugin.getConfig().getConfigurationSection("affixes.pool");
        if (pool == null) return;

        List<Map.Entry<String, Integer>> entries = new ArrayList<>();
        for (String id : pool.getKeys(false)) {
            int w = Math.max(0, plugin.getConfig().getInt("affixes.pool." + id + ".weight", 1));
            if (w > 0) entries.add(Map.entry(id.toLowerCase(Locale.ROOT), w));
        }
        if (entries.isEmpty()) return;

        List<String> chosen = new ArrayList<>();
        for (int i = 0; i < max && !entries.isEmpty(); i++) {
            int total = 0;
            for (var e : entries) total += e.getValue();
            if (total <= 0) break;

            int roll = ThreadLocalRandom.current().nextInt(total);
            int running = 0;
            int pickedIndex = -1;

            for (int idx = 0; idx < entries.size(); idx++) {
                running += entries.get(idx).getValue();
                if (roll < running) {
                    pickedIndex = idx;
                    break;
                }
            }

            if (pickedIndex >= 0) {
                chosen.add(entries.get(pickedIndex).getKey());
                entries.remove(pickedIndex); // unique picks
            }
        }

        if (!chosen.isEmpty()) {
            entity.getPersistentDataContainer().set(
                    affixesKey,
                    PersistentDataType.STRING,
                    String.join(",", chosen)
            );
        }
    }
}
