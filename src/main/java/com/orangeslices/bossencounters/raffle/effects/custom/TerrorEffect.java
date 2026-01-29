package com.orangeslices.bossencounters.raffle.effects.custom;

import com.orangeslices.bossencounters.raffle.RaffleEffectId;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * TERROR (curse)
 *
 * Simple baseline curse:
 * - Applies Warden Darkness effect
 * - Occasionally plays Warden roar sound
 *
 * Notes:
 * - Curse level is always 1 (enforced elsewhere)
 * - Behavior is intentionally light to avoid griefing
 * - This validates the custom-effect pipeline without complexity
 */
public final class TerrorEffect implements RaffleCustomEffect {

    // How long darkness lasts (ticks)
    private static final int DARKNESS_DURATION = 60; // 3 seconds

    // Sound cooldown (ms) to avoid spam
    private static final long ROAR_COOLDOWN_MS = 8000;

    private long lastRoarMs = 0;

    @Override
    public RaffleEffectId getId() {
        return RaffleEffectId.TERROR;
    }

    @Override
    public void apply(Player player, int level) {
        if (player == null || !player.isOnline()) return;

        // Apply Darkness (no particles, icon visible)
        PotionEffect current = player.getPotionEffect(PotionEffectType.DARKNESS);
        if (current == null || current.getDuration() < 20) {
            player.addPotionEffect(
                    new PotionEffect(
                            PotionEffectType.DARKNESS,
                            DARKNESS_DURATION,
                            0,
                            true,   // ambient
                            false,  // particles
                            true    // icon
                    )
            );
        }

        // Occasionally play Warden roar (guarded)
        long now = System.currentTimeMillis();
        if (now - lastRoarMs >= ROAR_COOLDOWN_MS) {
            lastRoarMs = now;
            player.playSound(
                    player.getLocation(),
                    Sound.ENTITY_WARDEN_ROAR,
                    0.6f,
                    0.8f
            );
        }
    }

    @Override
    public void clear(Player player) {
        if (player == null) return;
        player.removePotionEffect(PotionEffectType.DARKNESS);
    }
}
