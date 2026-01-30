package com.orangeslices.bossencounters.raffle.effects.custom;

import com.orangeslices.bossencounters.raffle.RaffleEffectId;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * REDUCTION curse
 * - Applies player scale shrink while the cursed armor is worn.
 * - Restores original scale when removed.
 * - NO fallback (per your requirement).
 */
public final class ReductionEffect implements RaffleCustomEffect {

    private static final double REDUCED_SCALE = 0.60;

    private final Map<UUID, Double> originalScale = new HashMap<>();

    @Override
    public RaffleEffectId getId() {
        return RaffleEffectId.REDUCTION;
    }

    @Override
    public void apply(Player player, int level) {
        if (player == null || !player.isOnline()) return;

        AttributeInstance scale = player.getAttribute(Attribute.GENERIC_SCALE);
        if (scale == null) return; // no fallback

        UUID id = player.getUniqueId();
        originalScale.putIfAbsent(id, scale.getBaseValue());
        scale.setBaseValue(REDUCED_SCALE);
    }

    @Override
    public void clear(Player player) {
        if (player == null) return;

        AttributeInstance scale = player.getAttribute(Attribute.GENERIC_SCALE);
        if (scale == null) return;

        Double original = originalScale.remove(player.getUniqueId());
        if (original != null) {
            scale.setBaseValue(original);
        }
    }
}
