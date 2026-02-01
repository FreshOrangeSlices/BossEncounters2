package com.orangeslices.bossencounters.raffle.effects.custom;

import com.orangeslices.bossencounters.raffle.RaffleEffectId;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zoglin;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MatadorEffect implements RaffleCustomEffect {

    private static final int DESPAWN_TICKS = 20 * 12; // ~12 seconds
    private static final int KNOCKBACK_LEVEL = 3;

    private final Map<UUID, Entity> spawned = new HashMap<>();
    private final Map<UUID, BukkitTask> despawnTasks = new HashMap<>();

    @Override
    public RaffleEffectId getId() {
        return RaffleEffectId.MATADOR;
    }

    @Override
    public void apply(Player player, int level) {
        if (player == null || !player.isOnline()) return;

        UUID id = player.getUniqueId();

        // Ensure one-time trigger while armor is worn
        if (spawned.containsKey(id)) return;

        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(getClass());

        Zoglin bull = player.getWorld().spawn(
                player.getLocation().add(2, 0, 2),
                Zoglin.class,
                z -> {
                    z.setAdult();
                    z.setRemoveWhenFarAway(true);
                    z.setCanPickupItems(false);
                }
        );

        // Aggro immediately
        bull.setTarget(player);

        // Speed I for the lifetime (no particles / icon)
        bull.addPotionEffect(new PotionEffect(
                PotionEffectType.SPEED,
                DESPAWN_TICKS,
                0,      // Speed I
                false,  // ambient
                false,  // particles
                false   // icon
        ));

        // Equip Knockback III stick
        equipKnockbackStick(bull);

        // Audio cue
        player.getWorld().playSound(
                player.getLocation(),
                Sound.ENTITY_ZOGLIN_ANGRY,
                0.9f,
                0.9f
        );

        spawned.put(id, bull);

        BukkitTask despawn = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Entity e = spawned.remove(id);
            if (e != null && e.isValid()) e.remove();

            BukkitTask t = despawnTasks.remove(id);
            if (t != null) t.cancel();
        }, DESPAWN_TICKS);

        despawnTasks.put(id, despawn);
    }

    @Override
    public void clear(Player player) {
        if (player == null) return;

        UUID id = player.getUniqueId();

        BukkitTask t = despawnTasks.remove(id);
        if (t != null) t.cancel();

        Entity e = spawned.remove(id);
        if (e != null && e.isValid()) e.remove();
    }

    private static void equipKnockbackStick(Zoglin bull) {
        ItemStack stick = new ItemStack(Material.STICK);
        ItemMeta meta = stick.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "Matador's Baton");
            meta.addEnchant(Enchantment.KNOCKBACK, KNOCKBACK_LEVEL, true);
            stick.setItemMeta(meta);
        } else {
            stick.addUnsafeEnchantment(Enchantment.KNOCKBACK, KNOCKBACK_LEVEL);
        }

        EntityEquipment eq = bull.getEquipment();
        if (eq != null) {
            eq.setItemInMainHand(stick);
            eq.setItemInMainHandDropChance(0.0f);

            // Safety: no armor drops either
            eq.setHelmetDropChance(0.0f);
            eq.setChestplateDropChance(0.0f);
            eq.setLeggingsDropChance(0.0f);
            eq.setBootsDropChance(0.0f);
        }
    }
}
