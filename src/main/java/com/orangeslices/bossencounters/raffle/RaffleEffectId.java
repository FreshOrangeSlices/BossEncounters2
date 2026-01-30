package com.orangeslices.bossencounters.raffle;

import java.util.Locale;

/**
 * Raffle effect IDs stored in PDC as "ID:level,ID:level".
 *
 * IMPORTANT:
 * - IDs are intentionally THEMATIC/VAGUE.
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

    public boolean canLevel() {
        return !curse && canLevel;
    }

    /**
     * Case-insensitive parse with backwards-compatible aliases.
     */
    public static RaffleEffectId fromString(String raw) {
        if (raw == null) return null;
        String key = raw.trim().toUpperCase(Locale.ROOT);
        if (key.isEmpty()) return null;

        // ---- Backwards compatibility aliases ----
        // Old names you mentioned:
        // warmth, vigor, unease, disarray
        //
        // Adjust here later if your old design meant something different.
        switch (key) {
            case "WARMTH" -> key = "EMBER_WARD";       // fire resistance vibe
            case "VIGOR" -> key = "VITALITY";          // health boost vibe
            case "UNEASE" -> key = "TERROR";           // spooky curse vibe
            case "DISARRAY" -> key = "MISSTEP";        // chaos/misstep vibe
        }

        try {
            return RaffleEffectId.valueOf(key);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
