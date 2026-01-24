package com.orangeslices.bossencounters;

import com.orangeslices.bossencounters.token.TokenDefinition;
import com.orangeslices.bossencounters.token.TokenRegistry;
import com.orangeslices.bossencounters.token.TokenType;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;
import java.util.Random;

public final class BossDropListener implements Listener {

    private final BossEncountersPlugin plugin;
    private final Random random = new Random();

    // Unified token source-of-truth
    private final TokenRegistry registry = new TokenRegistry();

    public BossDropListener(BossEncountersPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBossDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!isBoss(entity)) return;

        String rank = getRank(entity);
        World world = entity.getWorld();

        // Token pool (Unified)
        rollTokenPool(world, entity, rank);

        // Bonus loot (unchanged)
        rollBonusLoot(world, entity, mapRankToTier(rank));
    }

    /* =========================
       TOKEN POOL (Unified)
       ========================= */

    private void rollTokenPool(World world, LivingEntity entity, String rankUpper) {
        int tokenCount = rollTokenCount(rankUpper);
        if (tokenCount <= 0) return;

        TokenType firstType = rollTokenType(rankUpper);
        int firstLevel = rollTokenLevel(firstType, rankUpper);

        world.dropItemNaturally(entity.getLocation(), createToken(firstType, firstLevel));

        if (tokenCount == 1) return;

        // Second token — force different type (no duplicates)
        TokenType secondType = rollDifferentTokenType(rankUpper, firstType);
        int secondLevel = rollTokenLevel(secondType, rankUpper);

        world.dropItemNaturally(entity.getLocation(), createToken(secondType, secondLevel));
    }

    private ItemStack createToken(TokenType type, int level) {
    TokenDefinition def = registry.get(type);
    int clamped = (def == null) ? level : def.clampLevel(level);

    return switch (type) {
        case SHARPEN -> SharpeningKits.makeSharpeningKit(plugin, clamped);
        case MARK -> MarkKits.makeMarkKit(plugin, clamped);

        case HASTE -> PotionKits.makePotionKit(plugin, PotionKits.PotionTokenType.HASTE, clamped);
        case STRENGTH -> PotionKits.makePotionKit(plugin, PotionKits.PotionTokenType.STRENGTH, clamped);

        case FIRE_RESIST -> PotionKits.makePotionKit(plugin, PotionKits.PotionTokenType.FIRE_RESISTANCE, clamped);
        case HEALTH_BOOST -> PotionKits.makePotionKit(plugin, PotionKits.PotionTokenType.HEALTH_BOOST, clamped);

        case NIGHT_VISION -> PotionKits.makePotionKit(plugin, PotionKits.PotionTokenType.NIGHT_VISION, clamped);
        case WATER_BREATHING -> PotionKits.makePotionKit(plugin, PotionKits.PotionTokenType.WATER_BREATHING, clamped);

        // SPEED is intentionally not dropped by bosses (yet)
        default -> throw new IllegalStateException("Unhandled TokenType in BossDropListener: " + type);
    };
} 
    
    private int rollTokenCount(String rankUpper) {
        double r = random.nextDouble();

        return switch (rankUpper) {
            case "GRAY"   -> (r < 0.60) ? 0 : (r < 0.95) ? 1 : 2;
            case "GREEN"  -> (r < 0.50) ? 0 : (r < 0.92) ? 1 : 2;
            case "RED"    -> (r < 0.25) ? 0 : (r < 0.80) ? 1 : 2;
            case "PURPLE" -> (r < 0.10) ? 0 : (r < 0.65) ? 1 : 2;
            case "GOLD"   -> (r < 0.05) ? 0 : (r < 0.55) ? 1 : 2;
            default       -> (r < 0.60) ? 0 : (r < 0.95) ? 1 : 2;
        };
    }

    /**
     * Weighted selection per-rank.
     * Early ranks: mostly Sharpen/Mark.
     * Higher ranks: potion kits show up more often.
     */
    private TokenType rollTokenType(String rankUpper) {
        double r = random.nextDouble();

        return switch (rankUpper) {
            case "GRAY" -> pickWeighted(r,
                    0.55, TokenType.SHARPEN,
                    0.45, TokenType.MARK
            );

            case "GREEN" -> pickWeighted(r,
                    0.50, TokenType.SHARPEN,
                    0.35, TokenType.MARK,
                    0.15, TokenType.HASTE
            );

            case "RED" -> pickWeighted(r,
                    0.40, TokenType.SHARPEN,
                    0.30, TokenType.MARK,
                    0.12, TokenType.HASTE,
                    0.10, TokenType.STRENGTH,
                    0.08, TokenType.FIRE_RESIST
            );

            case "PURPLE" -> pickWeighted(r,
                    0.30, TokenType.SHARPEN,
                    0.22, TokenType.MARK,
                    0.12, TokenType.HASTE,
                    0.12, TokenType.STRENGTH,
                    0.10, TokenType.FIRE_RESIST,
                    0.08, TokenType.HEALTH_BOOST,
                    0.06, TokenType.NIGHT_VISION
            );

            case "GOLD" -> pickWeighted(r,
                    0.22, TokenType.SHARPEN,
                    0.18, TokenType.MARK,
                    0.14, TokenType.HASTE,
                    0.14, TokenType.STRENGTH,
                    0.10, TokenType.FIRE_RESIST,
                    0.10, TokenType.HEALTH_BOOST,
                    0.06, TokenType.NIGHT_VISION,
                    0.06, TokenType.WATER_BREATHING
            );

            default -> pickWeighted(r,
                    0.50, TokenType.SHARPEN,
                    0.50, TokenType.MARK
            );
        };
    }

    /**
     * Rank-scaled Level II chance, with hard clamps:
     * - FIRE_RESIST / WATER_BREATHING / NIGHT_VISION are always Level I (no Level II).
     * (Registry also clamps these to max 1, so this is doubly safe.)
     */
    private int rollTokenLevel(TokenType type, String rankUpper) {
        if (type == TokenType.FIRE_RESIST || type == TokenType.WATER_BREATHING || type == TokenType.NIGHT_VISION) {
            return 1;
        }

        double r = random.nextDouble();

        return switch (rankUpper) {
            case "RED"    -> (r < 0.25) ? 2 : 1;
            case "PURPLE" -> (r < 0.40) ? 2 : 1;
            case "GOLD"   -> (r < 0.60) ? 2 : 1;
            default       -> 1;
        };
    }

    private TokenType rollDifferentTokenType(String rankUpper, TokenType notThis) {
        for (int i = 0; i < 12; i++) {
            TokenType t = rollTokenType(rankUpper);
            if (t != notThis) return t;
        }
        return (notThis == TokenType.SHARPEN) ? TokenType.MARK : TokenType.SHARPEN;
    }

    /**
     * Helper to pick from weight pairs that sum to 1.0.
     * Format: w1,t1,w2,t2,... (weights must be positive).
     */
    private TokenType pickWeighted(double r, Object... pairs) {
        double cumulative = 0.0;
        for (int i = 0; i < pairs.length; i += 2) {
            double w = (double) pairs[i];
            TokenType t = (TokenType) pairs[i + 1];
            cumulative += w;
            if (r < cumulative) return t;
        }
        return (TokenType) pairs[pairs.length - 1];
    }

    /* =========================
       BONUS LOOT (Unchanged)
       ========================= */

    private void rollBonusLoot(World world, LivingEntity entity, Tier tier) {
        double roll = random.nextDouble();

        switch (tier) {
            case LOW -> {
                if (roll < 0.70) dropCommon(world, entity);
                else dropUncommon(world, entity);
            }
            case MID -> {
                if (roll < 0.50) dropCommon(world, entity);
                else if (roll < 0.90) dropUncommon(world, entity);
                else dropRare(world, entity, false);
            }
            case HIGH -> {
                if (roll < 0.30) dropCommon(world, entity);
                else if (roll < 0.80) dropUncommon(world, entity);
                else dropRare(world, entity, true);
            }
        }
    }

    private void dropCommon(World world, LivingEntity entity) {
        switch (random.nextInt(4)) {
            case 0 -> world.dropItemNaturally(entity.getLocation(), new ItemStack(Material.IRON_INGOT, rand(2, 6)));
            case 1 -> world.dropItemNaturally(entity.getLocation(), new ItemStack(Material.GOLD_INGOT, rand(2, 6)));
            case 2 -> world.dropItemNaturally(entity.getLocation(), new ItemStack(Material.REDSTONE, rand(8, 16)));
            default -> world.dropItemNaturally(entity.getLocation(), new ItemStack(Material.LAPIS_LAZULI, rand(8, 16)));
        }
    }

    private void dropUncommon(World world, LivingEntity entity) {
        switch (random.nextInt(4)) {
            case 0 -> world.dropItemNaturally(entity.getLocation(), new ItemStack(Material.GOLD_BLOCK, 1));
            case 1 -> world.dropItemNaturally(entity.getLocation(), new ItemStack(Material.DIAMOND, rand(1, 2)));
            case 2 -> world.dropItemNaturally(entity.getLocation(), new ItemStack(Material.GOLDEN_APPLE, 1));
            default -> world.dropItemNaturally(entity.getLocation(), new ItemStack(Material.COAL, rand(16, 32)));
        }
    }

    private void dropRare(World world, LivingEntity entity, boolean allowNetheriteScrap) {
        if (random.nextDouble() < 0.20) {
            world.dropItemNaturally(entity.getLocation(), new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1));
            return;
        }

        if (allowNetheriteScrap) {
            world.dropItemNaturally(entity.getLocation(), new ItemStack(Material.NETHERITE_SCRAP, 1));
        } else {
            world.dropItemNaturally(entity.getLocation(), new ItemStack(Material.DIAMOND, 1));
        }
    }

    /* =========================
       Helpers
       ========================= */

    private boolean isBoss(LivingEntity entity) {
        Byte flag = entity.getPersistentDataContainer()
                .get(plugin.bossKey(), PersistentDataType.BYTE);
        return flag != null && flag == (byte) 1;
    }

    private String getRank(LivingEntity entity) {
        NamespacedKey rankKey = new NamespacedKey(plugin, "rank");
        String rank = entity.getPersistentDataContainer()
                .get(rankKey, PersistentDataType.STRING);
        return rank == null ? "GRAY" : rank.toUpperCase(Locale.ROOT);
    }

    private Tier mapRankToTier(String rankUpper) {
        return switch (rankUpper) {
            case "RED" -> Tier.MID;
            case "PURPLE", "GOLD" -> Tier.HIGH;
            default -> Tier.LOW;
        };
    }

    private int rand(int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    private enum Tier {
        LOW, MID, HIGH
    }
}
