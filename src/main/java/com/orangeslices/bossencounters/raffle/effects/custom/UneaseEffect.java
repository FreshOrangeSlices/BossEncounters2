package com.orangeslices.bossencounters.raffle.effects.custom;

import com.orangeslices.bossencounters.raffle.RaffleEffectId;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class UneaseEffect implements RaffleCustomEffect {

    private static final int DURATION_TICKS = 20 * 20; // ~20s
    private static final long SOUND_COOLDOWN_MS = 4000;

    private final Map<UUID, Long> lastSound = new HashMap<>();

    @Override
    public RaffleEffectId getId() {
        return RaffleEffectId.UNEASE;
    }

    @Override
    public void apply(Player player, int level) {
        if (player == null || !player.isOnline()) return;

        player.addPotionEffect(new PotionEffect(
                PotionEffectType.NAUSEA,
                DURATION_TICKS,
                0,
                true,
                false,
                false
        ));

        long now = System.currentTimeMillis();
        long last = lastSound.getOrDefault(player.getUniqueId(), 0L);
        if (now - last >= SOUND_COOLDOWN_MS) {
            lastSound.put(player.getUniqueId(), now);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_AMBIENT, 0.35f, 0.7f);
        }
    }

    @Override
    public void clear(Player player) {
        if (player == null) return;
        player.removePotionEffect(PotionEffectType.NAUSEA);
        lastSound.remove(player.getUniqueId());
    }
}
