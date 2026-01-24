package com.orangeslices.bossencounters.token;

import java.util.Objects;

/**
 * Metadata describing a token:
 * - where it can be applied (target)
 * - level clamping rules
 * - display/lore helpers (kept lightweight for now)
 */
public final class TokenDefinition {

    public enum Target {
        WEAPON,         // swords/axes etc
        TOOL,           // pick/shovel/hoe etc
        ARMOR_HELMET,
        ARMOR_CHESTPLATE,
        ARMOR_LEGGINGS,
        ARMOR_BOOTS,
        ARMOR_ANY,      // any armor piece
        ANY             // no restrictions (rarely used)
    }

    private final TokenType type;
    private final Target target;
    private final int minLevel;
    private final int maxLevel;

    // For UI/lore (optional but useful)
    private final String displayName;
    private final String loreLineFormat; // ex: "&7Haste: &f{level}"

    public TokenDefinition(
            TokenType type,
            Target target,
            int minLevel,
            int maxLevel,
            String displayName,
            String loreLineFormat
    ) {
        this.type = Objects.requireNonNull(type, "type");
        this.target = Objects.requireNonNull(target, "target");
        this.minLevel = minLevel;
        this.maxLevel = maxLevel;
        this.displayName = displayName == null ? type.name() : displayName;
        this.loreLineFormat = loreLineFormat;
    }

    public TokenType type() {
        return type;
    }

    public Target target() {
        return target;
    }

    public int minLevel() {
        return minLevel;
    }

    public int maxLevel() {
        return maxLevel;
    }

    public String displayName() {
        return displayName;
    }

    public String loreLineFormat() {
        return loreLineFormat;
    }

    public int clampLevel(int level) {
        if (level < minLevel) return minLevel;
        if (level > maxLevel) return maxLevel;
        return level;
    }

    public String formatLoreLine(int level) {
        if (loreLineFormat == null || loreLineFormat.isBlank()) return null;
        return loreLineFormat.replace("{level}", String.valueOf(level));
    }
}
