package com.orangeslices.bossencounters.raffle;

public enum RaffleEffectId {
    // GOOD
    VITALITY(false),
    EMBER_WARD(false),

    // CURSE (BAD)
    DREAD(true),
    MISSTEP(true);

    private final boolean curse;

    RaffleEffectId(boolean curse) {
        this.curse = curse;
    }

    public boolean isCurse() {
        return curse;
    }

    public boolean isGood() {
        return !curse;
    }

    /**
     * Safe parser for config strings (case-insensitive).
     * Returns null if the id is unknown.
     */
    public static RaffleEffectId fromString(String raw) {
        if (raw == null) return null;
        String cleaned = raw.trim();
        if (cleaned.isEmpty()) return null;

        try {
            return RaffleEffectId.valueOf(cleaned.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
