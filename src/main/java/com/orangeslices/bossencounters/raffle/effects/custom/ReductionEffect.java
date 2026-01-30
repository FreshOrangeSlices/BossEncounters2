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
 *
 * Behavior:
 * - On equip: shrink the player using Attribute.GENERIC_SCALE
 * - While worn: remains shrunk (no ticking, no timer)
 * - On unequip: restores original scale
 *
 * Hard constraint:
 * - No fallback effects (no potions, no substitutes).
 * - If GENERIC_SCALE is unavailable, this curse does nothing.
 */
public final class ReductionEffect implements RaffleCustomEffect {

    // Tune this. 1.0 = normal size. 0.6 = noticeably smaller.
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
        if (scale == null) {
            // No fallback by design.
            return;
        }

        UUID id = player.getUniqueId();

        // Only capture original once per active session
        originalScale.putIfAbsent(id, scale.getBaseValue());

        // Apply shrink
        scale.setBaseValue(REDUCED_SCALE);
    }

    @Override
    public void clear(Player player) {
        if (player == null) return;

        AttributeInstance scale = player.getAttribute(Attribute.GENERIC_SCALE);
        if (scale == null) return;

        UUID id = player.getUniqueId();
        Double original = originalScale.remove(id);
        if (original != null) {
            scale.setBaseValue(original);
        }
    }
}
