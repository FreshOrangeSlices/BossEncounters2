package com.orangeslices.bossencounters.raffle.effects.custom;

import com.orangeslices.bossencounters.raffle.RaffleEffectId;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MISSTEP curse (trigger-once on equip; cleared on unequip by engine).
 *
 * Design intent:
 * - "my feet betrayed me"
 * - brief movement sabotage without being a permanent grief
 *
 * Implementation:
 * - Slowness + Jump reduction (feels like stumbling)
 * - quick sound cue
 */
public final class MisstepEffect implements RaffleCustomEffect {

    private static final int DURATION_TICKS = 20 * 10; // ~10s
    private static final long SOUND_COOLDOWN_MS = 1500;

    private final Map<UUID, Long> lastSoundAt = new ConcurrentHashMap<>();

    @Override
    public RaffleEffectId getId() {
        return RaffleEffectId.MISSTEP;
    }

    @Override
    public void apply(Player player, int level) {
        if (player == null || !player.isOnline()) return;

        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, DURATION_TICKS, 1, true, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, DURATION_TICKS, 250, true, false, false)); 
        // amplifier 250 is the "anti-jump" trick: makes jumping basically fail

        long now = System.currentTimeMillis();
        long last = lastSoundAt.getOrDefault(player.getUniqueId(), 0L);
        if (now - last >= SOUND_COOLDOWN_MS) {
            lastSoundAt.put(player.getUniqueId(), now);

            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH, 0.6f, 0.9f);
        }
    }

    @Override
    public void clear(Player player) {
        if (player == null) return;

        player.removePotionEffect(PotionEffectType.SLOW);
        player.removePotionEffect(PotionEffectType.JUMP);

        lastSoundAt.remove(player.getUniqueId());
    }
}
