package com.orangeslices.bossencounters.raffle;

import java.util.Locale;

/**
 * Raffle effect IDs stored in PDC as "ID:level,ID:level".
 *
 * IMPORTANT:
 * - IDs are intentionally THEMATIC / VAGUE.
 * - fromString() includes aliases so older saved items still work.
 */
public enum RaffleEffectId {

    // -------------------------
    // GOOD (levelable)
    // -------------------------
    VITALITY(false, true),        // Health Boost
    IRON_WILL(false, true),       // Resistance (Chest only)
    BLOOD_MENDING(false, true),   // Regeneration (Leggings only)
    SKYBOUND(false, true),        // Jump Boost (Boots only)

    // -------------------------
    // GOOD (flat / non-leveling)
    // -------------------------
    EMBER_WARD(false, false),     // Fire Resistance
    FORTUNE(false, false),        // Luck
    TIDEBOUND(false, false),      // Conduit Power (Helmet only)
    OCEAN_GRACE(false, false),    // Dolphin's Grace (Boots only)
    VILLAGER_FAVOR(false, false), // Hero of the Village

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
     * Case-insensitive parse with backwards-compatible aliases.
     * Returns null if unknown.
     */
    public static RaffleEffectId fromString(String raw) {
        if (raw == null) return null;

        String key = raw.trim().toUpperCase(Locale.ROOT);
        if (key.isEmpty()) return null;

        // ---- Backwards compatibility aliases (old system names) ----
        // These are intentionally mapped to the closest NEW thematic ID.
        switch (key) {
            case "WARMTH" -> key = "EMBER_WARD";
            case "VIGOR" -> key = "VITALITY";
            case "UNEASE" -> key = "TERROR";
            case "DISARRAY" -> key = "MISSTEP";
        }

        try {
            return RaffleEffectId.valueOf(key);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
