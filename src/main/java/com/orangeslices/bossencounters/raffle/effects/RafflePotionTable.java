package com.orangeslices.bossencounters.raffle;

import java.util.Locale;

/**
 * Raffle effect IDs stored in PDC as "ID:level,ID:level".
 *
 * IMPORTANT:
 * - These IDs are intentionally VAGUE / THEMATIC.
 * - The actual mechanics (potion effects, custom logic) are mapped elsewhere.
 *
 * Leveling rules:
 * - Some GOOD effects can level (duplicates level up)
 * - Some GOOD effects are flat (never level)
 * - Curses never level
 */
public enum RaffleEffectId {

    // -------------------------
    // GOOD (levelable)
    // -------------------------
    VITALITY(false, true),        // mapped: Health Boost
    IRON_WILL(false, true),       // mapped: Resistance (CHESTPLATE only)
    BLOOD_MENDING(false, true),   // mapped: Regeneration (LEGGINGS only)
    SKYBOUND(false, true),        // mapped: Jump Boost (BOOTS only)

    // -------------------------
    // GOOD (flat / non-leveling)
    // -------------------------
    EMBER_WARD(false, false),     // mapped: Fire Resistance
    FORTUNE(false, false),        // mapped: Luck
    TIDEBOUND(false, false),      // mapped: Conduit Power (HELMET only)
    OCEAN_GRACE(false, false),    // mapped: Dolphin's Grace (BOOTS only)
    VILLAGER_FAVOR(false, false), // mapped: Hero of the Village

    // -------------------------
    // CURSES (non-leveling)
    // -------------------------
    DREAD(true, false),
    MISSTEP(true, false),
    TERROR(true, false);

    private final boolean curse;
    private final boolean canLevel;

    RaffleEffectId(boolean curse, boolean canLevel) {
        this.curse = curse;
        this.canLevel = canLevel;
    }

    public boolean isCurse() {
        return curse;
    }

    public boolean isGood() {
        return !curse;
    }

    /**
     * True only for GOOD effects that are allowed to level up.
     * (Curses always return false here.)
     */
    public boolean canLevel() {
        return !curse && canLevel;
    }

    /**
     * Case-insensitive parse. Returns null if unknown.
     */
    public static RaffleEffectId fromString(String raw) {
        if (raw == null) return null;
        String key = raw.trim().toUpperCase(Locale.ROOT);
        if (key.isEmpty()) return null;

        try {
            return RaffleEffectId.valueOf(key);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
