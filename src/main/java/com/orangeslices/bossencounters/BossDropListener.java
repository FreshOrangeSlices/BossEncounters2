package com.orangeslices.bossencounters;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Locale;
import java.util.Random;

public final class BossDropListener implements Listener {

    private final BossEncountersPlugin plugin;
    private final Random random = new Random();

    private final NamespacedKey essenceKey;

    public BossDropListener(BossEncountersPlugin plugin) {
        this.plugin = plugin;
        this.essenceKey = new NamespacedKey(plugin, "essence_tier");
    }

    @EventHandler
    public void onBossDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();

        // Only handle bosses
        if (!isBoss(entity)) return;

        String rank = getRank(entity);
        Tier tier = mapRankToTier(rank);

        // 1) Essence drops (your current test values)
        int tier1 = 0;
        int tier2 = 0;
        int tier3 = 0;

        switch (tier) {
            case MID -> {
                tier1 = rand(4, 6);
                tier2 = rand(1, 2);
            }
            case HIGH -> {
                tier1 = rand(7, 9);
                tier2 = rand(2, 4);
                tier3 = 1; // guaranteed for HIGH (as per your test plan)
            }
            default -> { // LOW
                tier1 = rand(1, 2);
            }
        }

        World world = entity.getWorld();

        if (tier1 > 0) world.dropItemNaturally(entity.getLocation(), createEssence(1, tier1));
        if (tier2 > 0) world.dropItemNaturally(entity.getLocation(), createEssence(2, tier2));
        if (tier3 > 0) world.dropItemNaturally(entity.getLocation(), createEssence(3, tier3));

        // 2) Bonus loot roll (hardcoded for now)
        rollBonusLoot(world, entity, tier);
    }

    /* =========================
       Bonus Loot Pool (Hardcoded)
       ========================= */

    private void rollBonusLoot(World world, LivingEntity entity, Tier tier) {
        // One roll per boss for now
        double roll = random.nextDouble();

        switch (tier) {
            case LOW -> {
                // 70% common, 30% uncommon, 0% rare
                if (roll < 0.70) dropCommon(world, entity);
                else dropUncommon(world, entity);
            }
            case MID -> {
                // 50% common, 40% uncommon, 10% rare
                if (roll < 0.50) dropCommon(world, entity);
                else if (roll < 0.90) dropUncommon(world, entity);
                else dropRare(world, entity, false);
            }
            case HIGH -> {
                // 30% common, 50% uncommon, 20% rare
                if (roll < 0.30) dropCommon(world, entity);
                else if (roll < 0.80) dropUncommon(world, entity);
                else dropRare(world, entity, true);
            }
        }
    }

    private void dropCommon(World world, LivingEntity entity) {
        int pick = random.nextInt(4);
        switch (pick) {
            case 0 -> world.dropItemNaturally(entity.getLocation(), new ItemStack(Material.IRON_INGOT, rand(2, 6)));
            case 1 -> world.dropItemNaturally(entity.getLocation(), new ItemStack(Material.GOLD_INGOT, rand(2, 6)));
            case 2 -> world.dropItemNaturally(entity.getLocation(), new ItemStack(Material.REDSTONE, rand(8, 16)));
            default -> world.dropItemNaturally(entity.getLocation(), new ItemStack(Material.LAPIS_LAZULI, rand(8, 16)));
        }
    }

    private void dropUncommon(World world, LivingEntity entity) {
        int pick = random.nextInt(4);
        switch (pick) {
            case 0 -> world.dropItemNaturally(entity.getLocation(), new ItemStack(Material.GOLD_BLOCK, 1));
            case 1 -> world.dropItemNaturally(entity.getLocation(), new ItemStack(Material.DIAMOND, rand(1, 2)));
            case 2 -> world.dropItemNaturally(entity.getLocation(), new ItemStack(Material.GOLDEN_APPLE, 1));
            default -> world.dropItemNaturally(entity.getLocation(), new ItemStack(Material.COAL, rand(16, 32)));
        }
    }

    private void dropRare(World world, LivingEntity entity, boolean allowNetheriteScrap) {
        // Rare pool:
        //  - Enchanted golden apple (very rare)
        //  - Netherite scrap (only for HIGH by default)
        double r = random.nextDouble();

        // 20% of rare rolls: enchanted golden apple
        if (r < 0.20) {
            world.dropItemNaturally(entity.getLocation(), new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1));
            return;
        }

        // Otherwise: scrap for HIGH, diamond fallback for MID
        if (allowNetheriteScrap) {
            world.dropItemNaturally(entity.getLocation(), new ItemStack(Material.NETHERITE_SCRAP, 1));
        } else {
            world.dropItemNaturally(entity.getLocation(), new ItemStack(Material.DIAMOND, 1));
        }
    }

    /* =========================
       Essence Creation (Pattern A)
       ========================= */

    private ItemStack createEssence(int tier, int amount) {
        Material material = switch (tier) {
            case 1 -> Material.CHARCOAL;        // Essence I
            case 2 -> Material.ECHO_SHARD;      // Essence II
            case 3 -> Material.AMETHYST_SHARD;  // Essence III
            default -> throw new IllegalArgumentException("Invalid essence tier");
        };

        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(switch (tier) {
            case 1 -> "§7Essence I";
            case 2 -> "§bEssence II";
            case 3 -> "§dEssence III";
            default -> "";
        });

        meta.setLore(List.of(
                "§7Boss drop currency",
                "§8Condense 9 → 1 in crafting"
        ));

        meta.getPersistentDataContainer().set(
                essenceKey,
                PersistentDataType.INTEGER,
                tier
        );

        item.setItemMeta(meta);
        return item;
    }

    /* =========================
       Tier Mapping
       ========================= */

    private Tier mapRankToTier(String rankUpper) {
        // LOW: GRAY, GREEN
        // MID: RED
        // HIGH: PURPLE, GOLD
        return switch (rankUpper) {
            case "RED" -> Tier.MID;
            case "PURPLE", "GOLD" -> Tier.HIGH;
            default -> Tier.LOW;
        };
    }

    /* =========================
       Helpers
       ========================= */

    private boolean isBoss(LivingEntity entity) {
        Byte flag = entity.getPersistentDataContainer().get(
                plugin.bossKey(),
                PersistentDataType.BYTE
        );
        return flag != null && flag == (byte) 1;
    }

    private String getRank(LivingEntity entity) {
        NamespacedKey rankKey = new NamespacedKey(plugin, "rank");
        String rank = entity.getPersistentDataContainer().get(rankKey, PersistentDataType.STRING);
        return rank == null ? "GRAY" : rank.toUpperCase(Locale.ROOT);
    }

    private int rand(int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    private enum Tier { LOW, MID, HIGH }
}
