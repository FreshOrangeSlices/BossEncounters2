package com.orangeslices.bossencounters.raffle.effects;

import com.orangeslices.bossencounters.raffle.RaffleEffectId;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Expandable mapping of raffle effect IDs -> vanilla potion effects.
 *
 * This is intentionally a "pool table":
 * - To add a new potion-style raffle effect later, add one entry here.
 * - The engine stays stable and never needs rerouting.
 */
public final class RafflePotionTable {

    private RafflePotionTable() {}

    public enum SlotRule {
        ANY_ARMOR,      // helmet/chest/legs/boots all count (highest level wins across armor)
        HELMET_ONLY     // only helmet counts (future-proofing)
    }

    public static final class Entry {
        public final RaffleEffectId id;
        public final PotionEffectType potion;
        public final SlotRule slotRule;
        public final int durationTicks;

        public Entry(RaffleEffectId id, PotionEffectType potion, SlotRule slotRule, int durationTicks) {
            this.id = id;
            this.potion = potion;
            this.slotRule = slotRule;
            this.durationTicks = durationTicks;
        }
    }

    // Keep this list small for now. Expand freely later.
    private static final List<Entry> ENTRIES;
    static {
        List<Entry> list = new ArrayList<>();

        // Baseline safe mappings:
        // - VITALITY -> Health Boost
        // - EMBER_WARD -> Fire Resistance
        list.add(new Entry(RaffleEffectId.VITALITY, PotionEffectType.HEALTH_BOOST, SlotRule.ANY_ARMOR, 120));
        list.add(new Entry(RaffleEffectId.EMBER_WARD, PotionEffectType.FIRE_RESISTANCE, SlotRule.ANY_ARMOR, 120));

        // Examples for later (do NOT enable until you want them):
        // list.add(new Entry(RaffleEffectId.SEA_LUNG, PotionEffectType.WATER_BREATHING, SlotRule.HELMET_ONLY, 120));
        // list.add(new Entry(RaffleEffectId.SIGHTBEYOND, PotionEffectType.NIGHT_VISION, SlotRule.HELMET_ONLY, 350));

        ENTRIES = Collections.unmodifiableList(list);
    }

    public static List<Entry> entries() {
        return ENTRIES;
    }
}
