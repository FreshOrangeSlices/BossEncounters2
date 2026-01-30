package com.orangeslices.bossencounters.raffle.effects.custom;

import com.orangeslices.bossencounters.raffle.RaffleEffectId;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * TERROR curse
 *
 * Behavior:
 * - Triggers ONCE when activated (handled by engine)
 * - Applies Darkness for ~10 seconds
 * - Plays Warden roar with a cooldown (no spam)
 * - Clears Darkness immediately when removed
 *
 * Visuals:
 * - No particles
 * - No inventory icon (hidden)
 */
public final class TerrorEffect implements RaffleCustomEffect {

    private static final int DARKNESS_DURATION_TICKS = 200; // 10 seconds
    private static final long ROAR_COOLDOWN_MS = 8000;

    private final Map<UUID, Long> lastRoar = new HashMap<>();

    @Override
    public RaffleEffectId getId() {
        return RaffleEffectId.TERROR;
    }

    @Override
    public void apply(Player player, int level) {
        if (player == null || !player.isOnline()) return;

        // Apply darkness (ambient, no particles, icon hidden)
        player.addPotionEffect(
                new PotionEffect(
                        PotionEffectType.DARKNESS,
                        DARKNESS_DURATION_TICKS,
                        0,
                        true,
                        false,
                        false
                )
        );

        // Play roar with cooldown
        long now = System.currentTimeMillis();
        long last = lastRoar.getOrDefault(player.getUniqueId(), 0L);

        if (now - last >= ROAR_COOLDOWN_MS) {
            lastRoar.put(player.getUniqueId(), now);

            player.getWorld().playSound(
                    player.getLocation(),
                    Sound.ENTITY_WARDEN_ROAR,
                    0.6f,
                    1.0f
            );
        }
    }

    @Override
    public void clear(Player player) {
        if (player == null) return;

        player.removePotionEffect(PotionEffectType.DARKNESS);
        lastRoar.remove(player.getUniqueId());
    }
}
