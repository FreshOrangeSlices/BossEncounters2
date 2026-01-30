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
 * DREAD curse (trigger-once on equip; cleared on unequip by engine).
 *
 * Design intent:
 * - psychological "oh no" moment
 * - short, non-lethal disorientation
 */
public final class DreadEffect implements RaffleCustomEffect {

    private static final int DURATION_TICKS = 20 * 12; // ~12s
    private static final long SOUND_COOLDOWN_MS = 1500;

    private final Map<UUID, Long> lastSoundAt = new ConcurrentHashMap<>();

    @Override
    public RaffleEffectId getId() {
        return RaffleEffectId.DREAD;
    }

    @Override
    public void apply(Player player, int level) {
        if (player == null || !player.isOnline()) return;

        // Use modern effect names (Paper 1.20+)
        player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, DURATION_TICKS, 0, true, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20 * 3, 0, true, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 6, 0, true, false, false));

        long now = System.currentTimeMillis();
        long last = lastSoundAt.getOrDefault(player.getUniqueId(), 0L);
        if (now - last >= SOUND_COOLDOWN_MS) {
            lastSoundAt.put(player.getUniqueId(), now);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_STARE, 0.6f, 0.6f);
        }
    }

    @Override
    public void clear(Player player) {
        if (player == null) return;

        player.removePotionEffect(PotionEffectType.NAUSEA);
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.SLOWNESS);

        lastSoundAt.remove(player.getUniqueId());
    }
}
