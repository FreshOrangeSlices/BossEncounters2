package com.orangeslices.bossencounters.raffle;

import java.util.EnumMap;
import java.util.Map;

public final class RaffleDisplay {

    private static final Map<RaffleEffectId, String> VAGUE_NAME = new EnumMap<>(RaffleEffectId.class);

    static {
        // Keep these vague on purpose (player-facing)
        VAGUE_NAME.put(RaffleEffectId.VITALITY, "Vigor");
        VAGUE_NAME.put(RaffleEffectId.EMBER_WARD, "Warmth");

        VAGUE_NAME.put(RaffleEffectId.DREAD, "Unease");
        VAGUE_NAME.put(RaffleEffectId.MISSTEP, "Disarray");
    }

    private RaffleDisplay() {}

    public static String vagueName(RaffleEffectId id) {
        if (id == null) return "Unknown";
        return VAGUE_NAME.getOrDefault(id, "Unknown");
    }
}
