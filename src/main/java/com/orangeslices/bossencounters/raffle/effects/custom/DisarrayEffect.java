package com.orangeslices.bossencounters.raffle.effects.custom;

import com.orangeslices.bossencounters.raffle.RaffleEffectId;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class DisarrayEffect implements RaffleCustomEffect {

    private static final int DURATION_TICKS = 20 * 8; // ~8s

    @Override
    public RaffleEffectId getId() {
        return RaffleEffectId.DISARRAY;
    }

    @Override
    public void apply(Player player, int level) {
        if (player == null || !player.isOnline()) return;

        player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, DURATION_TICKS, 0, true, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, DURATION_TICKS, 0, true, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, DURATION_TICKS, 0, true, false, false));

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.25f, 1.2f);
    }

    @Override
    public void clear(Player player) {
        if (player == null) return;
        player.removePotionEffect(PotionEffectType.NAUSEA);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.WEAKNESS);
    }
}
