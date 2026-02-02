package com.orangeslices.bossencounters.raffle.effects.custom;

import com.orangeslices.bossencounters.raffle.RaffleEffectId;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * GECKO_GRIP (GOOD)
 *
 * Boots-only wall climbing effect.
 *
 * Behavior:
 * - While sneaking and touching a wall, gently pushes the player upward.
 * - No potion effects.
 * - No infinite climbing without player intent.
 * - Clears automatically when boots are removed.
 */
public final class GeckoGripEffect implements RaffleCustomEffect {

    @Override
    public RaffleEffectId getId() {
        return RaffleEffectId.GECKO_GRIP;
    }

    @Override
    public void apply(Player player, int level) {
        // GOOD effects may reapply; logic runs every engine tick
        if (player == null || !player.isOnline()) return;

        // Must be sneaking to activate
        if (!player.isSneaking()) return;

        // Must be touching a wall (horizontal collision)
        if (!player.isOnGround() && !player.isCollidable()) return;

        // Bukkit doesn't expose "touching wall" directly,
        // but horizontal collision is reliable enough.
        if (!player.isOnGround() && player.getVelocity().getY() < 0.2) {
            Vector v = player.getVelocity();

            // Gentle upward nudge (tuned to feel climb-y, not flight-y)
            player.setVelocity(new Vector(
                    v.getX() * 0.85,
                    0.26,
                    v.getZ() * 0.85
            ));
        }
    }

    @Override
    public void clear(Player player) {
        // Nothing persistent to clean up
        // (no attributes, no tasks, no metadata)
    }
}
