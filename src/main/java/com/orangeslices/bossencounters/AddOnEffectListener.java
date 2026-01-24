package com.orangeslices.bossencounters;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class AddOnEffectListener implements Listener {

    private final BossEncountersPlugin plugin;
    private final Random random = new Random();

    // Weapon upgrade keys
    private final NamespacedKey sharpenLevelKey;
    private final NamespacedKey markLevelKey;

    // Entity mark keys (stored on target entity)
    private final NamespacedKey markedByKey;
    private final NamespacedKey markedUntilKey;

    // One marked target per player (player UUID -> entity UUID)
    private final Map<UUID, UUID> currentMarkedTarget = new HashMap<>();

    // Spec constants
    private static final int MARK_SECONDS = 6;
    private static final int MARK_TICKS = MARK_SECONDS * 20;

    public AddOnEffectListener(BossEncountersPlugin plugin) {
        this.plugin = plugin;

        this.sharpenLevelKey = new NamespacedKey(plugin, "sharpen_level");
        this.markLevelKey = new NamespacedKey(plugin, "mark_level");

        this.markedByKey = new NamespacedKey(plugin, "marked_by");
        this.markedUntilKey = new NamespacedKey(plugin, "marked_until");
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        // Only player melee hits (damager must be Player)
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (weapon == null) return;

        ItemMeta meta = weapon.getItemMeta();
        if (meta == null) return;

        // 1) Apply Sharpening bonus (additive)
        applySharpeningBonus(event, meta);

        // 2) Mark system:
        //    - If target already marked by this player and active => multiply damage
        //    - Otherwise, if player has mark_level => chance to mark target (6s + glowing)
        applyMarkLogic(event, player, target, meta);
    }

    /* =========================
       Sharpening
       ========================= */

    private void applySharpeningBonus(EntityDamageByEntityEvent event, ItemMeta weaponMeta) {
        int level = weaponMeta.getPersistentDataContainer()
                .getOrDefault(sharpenLevelKey, PersistentDataType.INTEGER, 0);

        if (level <= 0) return;

        double bonus = switch (Math.min(level, 2)) {
            case 1 -> 1.0;
            case 2 -> 2.0;
            default -> 0.0;
        };

        if (bonus > 0) {
            event.setDamage(event.getDamage() + bonus);
        }
    }

    /* =========================
       Mark + Glowing
       ========================= */

    private void applyMarkLogic(EntityDamageByEntityEvent event, Player player, LivingEntity target, ItemMeta weaponMeta) {
        UUID playerId = player.getUniqueId();

        // If target is actively marked by this player -> multiply damage
        if (isActivelyMarkedBy(target, playerId)) {
            int markLevel = weaponMeta.getPersistentDataContainer()
                    .getOrDefault(markLevelKey, PersistentDataType.INTEGER, 0);

            // If weapon has no mark level anymore, just don't multiply
            if (markLevel <= 0) return;

            double mult = (markLevel == 1) ? 1.5 : 2.0;
            event.setDamage(event.getDamage() * mult);
            return;
        }

        // Otherwise attempt to apply a mark (proc chance based on weapon)
        int markLevel = weaponMeta.getPersistentDataContainer()
                .getOrDefault(markLevelKey, PersistentDataType.INTEGER, 0);
        if (markLevel <= 0) return;

        double chance = (markLevel == 1) ? 0.10 : 0.25;
        if (random.nextDouble() > chance) return;

        // Enforce: one marked target per player
        clearPreviousMark(playerId);

        // Apply mark to this target
        long untilTick = Bukkit.getCurrentTick() + MARK_TICKS;
        var pdc = target.getPersistentDataContainer();
        pdc.set(markedByKey, PersistentDataType.STRING, playerId.toString());
        pdc.set(markedUntilKey, PersistentDataType.LONG, untilTick);

        // Glowing for full duration
        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, MARK_TICKS, 0, true, false, true));

        // Track current target
        currentMarkedTarget.put(playerId, target.getUniqueId());
    }

    private boolean isActivelyMarkedBy(LivingEntity target, UUID playerId) {
        var pdc = target.getPersistentDataContainer();
        String markedBy = pdc.get(markedByKey, PersistentDataType.STRING);
        Long markedUntil = pdc.get(markedUntilKey, PersistentDataType.LONG);

        if (markedBy == null || markedUntil == null) return false;
        if (!markedBy.equals(playerId.toString())) return false;

        long now = Bukkit.getCurrentTick();
        return markedUntil > now;
    }

    private void clearPreviousMark(UUID playerId) {
        UUID prevTargetId = currentMarkedTarget.remove(playerId);
        if (prevTargetId == null) return;

        // Try to find the entity in any loaded world and clear its mark if it belongs to this player
        Bukkit.getWorlds().forEach(world -> {
            var entity = world.getEntity(prevTargetId);
            if (!(entity instanceof LivingEntity living)) return;

            var pdc = living.getPersistentDataContainer();
            String markedBy = pdc.get(markedByKey, PersistentDataType.STRING);
            if (markedBy == null || !markedBy.equals(playerId.toString())) return;

            pdc.remove(markedByKey);
            pdc.remove(markedUntilKey);

            // remove glowing if it's ours (safe to remove regardless)
            living.removePotionEffect(PotionEffectType.GLOWING);
        });
    }
}
