
package com.orangeslices.bossencounters;

import com.orangeslices.bossencounters.token.TokenDefinition;
import com.orangeslices.bossencounters.token.TokenKeys;
import com.orangeslices.bossencounters.token.TokenRegistry;
import com.orangeslices.bossencounters.token.TokenType;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AddOnListener implements Listener {

    private final BossEncountersPlugin plugin;

    // New centralized helpers
    private final TokenKeys keys;
    private final TokenRegistry registry;

    // Token item keys
    private final NamespacedKey tokenTypeKey;
    private final NamespacedKey tokenLevelKey;

    // Legacy token keys (older kits)
    private final NamespacedKey legacySharpenKitKey;
    private final NamespacedKey legacyMarkKitKey;

    // Legacy / extra token still supported even if not in TokenType (keeps old behavior)
    private final NamespacedKey waterBreathingKey;

    public AddOnListener(BossEncountersPlugin plugin) {
        this.plugin = plugin;

        this.keys = new TokenKeys(plugin);
        this.registry = new TokenRegistry();

        // Current token item storage
        this.tokenTypeKey = keys.tokenType();
        this.tokenLevelKey = keys.custom("token_level");

        // Legacy support
        this.legacySharpenKitKey = keys.custom("sharpen_kit_level");
        this.legacyMarkKitKey = keys.custom("mark_kit_level");

        // Older addon still supported (helmet-only)
        this.waterBreathingKey = keys.custom("water_breathing_level");
    }

    @EventHandler
    public void onApplyAddon(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        // Bedrock-safe: sneak + right-click
        if (!player.isSneaking()) return;
        if (!event.getAction().isRightClick()) return;

        // Prevent double-firing
        if (event.getHand() != EquipmentSlot.HAND) return;

        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();

        HandTokenTarget tt = resolveTokenAndTarget(main, off);
        if (tt == null) return;

        boolean applied = applyTokenToTarget(tt.tokenTypeRaw, tt.tokenLevel, tt.target);
        if (!applied) return;

        consumeOneFromHand(player, tt.tokenHand);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7f, 1.4f);
        event.setCancelled(true);
    }

    /* =========================
       Apply token -> write PDC on target item
       ========================= */

    private boolean applyTokenToTarget(String tokenTypeRaw, int tokenLevel, ItemStack target) {
        if (target == null || target.getType() == Material.AIR) return false;

        String normalized = normalizeTokenType(tokenTypeRaw);

        // Special legacy token that we still support even if not in registry
        if (normalized.equals("WATER_BREATHING")) {
            if (!isHelmet(target)) return false;
            int next = clampGeneric(tokenLevel); // historical behavior: 1..2
            return applyLevelToItem(target, next, waterBreathingKey, "§7Water Breathing: ");
        }

        // Registry-backed token types
        TokenType type = parseTokenType(normalized);
        if (type == null) return false;

        TokenDefinition def = registry.get(type);
        if (def == null) return false;

        // Clamp via registry definition
        int clamped = def.clampLevel(tokenLevel);

        // Validate target compatibility
        if (!isValidTarget(def.target(), type, target)) return false;

        // Apply to the appropriate PDC key
        NamespacedKey appliedKey = keyForAppliedLevel(type);
        if (appliedKey == null) return false;

        // Lore line: use registry format if present, otherwise fall back to a stable prefix
        String lorePrefix = "§7" + def.displayName() + ": ";
        return applyLevelToItem(target, clamped, appliedKey, lorePrefix);
    }

    /**
     * Writes PDC level, updates/overwrites a single lore line (does not nuke other lore).
     * Lore formatting keeps your old “I/II” style for 1–2; otherwise uses numeric.
     */
    private boolean applyLevelToItem(ItemStack item, int level, NamespacedKey itemKey, String lorePrefix) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        int current = meta.getPersistentDataContainer().getOrDefault(itemKey, PersistentDataType.INTEGER, 0);
        int next = Math.max(current, level);

        if (next == current) return false;

        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.INTEGER, next);

        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.removeIf(line -> line != null && line.startsWith(lorePrefix));
        lore.add(lorePrefix + romanOrNumber(next));
        meta.setLore(lore);

        item.setItemMeta(meta);
        return true;
    }

    private String romanOrNumber(int level) {
        // Preserve the original vibe: I / II for the common case
        if (level == 1) return "I";
        if (level == 2) return "II";
        return String.valueOf(level);
    }

    private int clampGeneric(int lvl) {
        if (lvl < 1) return 1;
        if (lvl > 2) return 2;
        return lvl;
    }

    /* =========================
       Resolve token + target
       (token in one hand, target item in the other)
       ========================= */

    private HandTokenTarget resolveTokenAndTarget(ItemStack main, ItemStack off) {
        TokenInfo mainToken = getTokenInfo(main);
        TokenInfo offToken = getTokenInfo(off);

        if (mainToken != null && off != null && off.getType() != Material.AIR) {
            return new HandTokenTarget(EquipmentSlot.HAND, mainToken.typeRaw, mainToken.level, off);
        }
        if (offToken != null && main != null && main.getType() != Material.AIR) {
            return new HandTokenTarget(EquipmentSlot.OFF_HAND, offToken.typeRaw, offToken.level, main);
        }
        return null;
    }

    /**
     * Recognize tokens:
     * - New path: token_type + token_level
     * - Legacy: sharpen_kit_level / mark_kit_level
     */
    private TokenInfo getTokenInfo(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        var pdc = meta.getPersistentDataContainer();

        // New token path
        String type = pdc.get(tokenTypeKey, PersistentDataType.STRING);
        Integer lvl = pdc.get(tokenLevelKey, PersistentDataType.INTEGER);
        if (type != null && lvl != null && lvl > 0) {
            return new TokenInfo(type, lvl);
        }

        // Legacy sharpening kit
        Integer s = pdc.get(legacySharpenKitKey, PersistentDataType.INTEGER);
        if (s != null && s > 0) {
            return new TokenInfo("SHARPENING", s);
        }

        // Legacy mark kit
        Integer m = pdc.get(legacyMarkKitKey, PersistentDataType.INTEGER);
        if (m != null && m > 0) {
            return new TokenInfo("MARK", m);
        }

        return null;
    }

    /* =========================
       Token parsing + mapping
       ========================= */

    private String normalizeTokenType(String raw) {
        if (raw == null) return "";
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Maps legacy strings + aliases into the unified TokenType enum names.
     * We keep old spellings so nothing breaks.
     */
    private TokenType parseTokenType(String normalized) {
        if (normalized.isBlank()) return null;

        // Legacy / aliases
        normalized = switch (normalized) {
            case "SHARPENING", "SHARPEN", "SHARPENED" -> "SHARPEN";
            case "FIRE_RES", "FIRE_RESIST", "FIRE_RESISTANCE" -> "FIRE_RESIST";
            default -> normalized;
        };

        // Enum is strict — return null if not supported
        try {
            return TokenType.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private NamespacedKey keyForAppliedLevel(TokenType type) {
        return switch (type) {
            case SHARPEN -> keys.sharpenLevel();
            case MARK -> keys.markLevel();

            case HASTE -> keys.hasteLevel();
            case STRENGTH -> keys.strengthLevel();
            case SPEED -> keys.speedLevel();

            case FIRE_RESIST -> keys.fireResLevel();
            case HEALTH_BOOST -> keys.healthBoostLevel();
            case NIGHT_VISION -> keys.nightVisionLevel();
           
            case WATER_BREATHING -> keys.custom("water_breathing_level");

        };
    }

    private boolean isValidTarget(TokenDefinition.Target targetRule, TokenType type, ItemStack target) {
        // Keep behavior consistent with the current plugin:
        // - HASTE allowed on tools OR weapons (original logic)
        if (type == TokenType.HASTE) {
            return isToolOrWeapon(target);
        }

        return switch (targetRule) {
            case WEAPON -> isWeapon(target);
            case TOOL -> isToolOrWeapon(target);
            case ARMOR_HELMET -> isHelmet(target);
            case ARMOR_CHESTPLATE -> isChestplate(target);
            case ARMOR_LEGGINGS -> isLeggings(target);
            case ARMOR_BOOTS -> isBoots(target);
            case ARMOR_ANY -> isArmor(target);
            case ANY -> true;
        };
    }

    /* =========================
       Target type checks
       ========================= */

    private boolean isWeapon(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        Material m = item.getType();
        String name = m.name();
        return name.endsWith("_SWORD")
                || name.endsWith("_AXE")
                || name.endsWith("_SPEAR")
                || m == Material.TRIDENT;
    }

    private boolean isToolOrWeapon(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        Material m = item.getType();
        String name = m.name();

        boolean tool = name.endsWith("_PICKAXE")
                || name.endsWith("_AXE")
                || name.endsWith("_SHOVEL")
                || name.endsWith("_HOE");

        return tool || isWeapon(item);
    }

    private boolean isArmor(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        String name = item.getType().name();
        return name.endsWith("_HELMET")
                || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS");
    }

    private boolean isHelmet(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        return item.getType().name().endsWith("_HELMET");
    }

    private boolean isChestplate(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        return item.getType().name().endsWith("_CHESTPLATE");
    }

    private boolean isLeggings(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        return item.getType().name().endsWith("_LEGGINGS");
    }

    private boolean isBoots(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        return item.getType().name().endsWith("_BOOTS");
    }

    /* =========================
       Consume token
       ========================= */

    private void consumeOneFromHand(Player player, EquipmentSlot hand) {
        ItemStack item = (hand == EquipmentSlot.HAND)
                ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();

        if (item == null || item.getType() == Material.AIR) return;

        int amt = item.getAmount();
        if (amt <= 1) {
            if (hand == EquipmentSlot.HAND) player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            else player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        } else {
            item.setAmount(amt - 1);
        }
    }

    /* =========================
       Small structs
       ========================= */

    private record TokenInfo(String typeRaw, int level) {}

    private static final class HandTokenTarget {
        final EquipmentSlot tokenHand;
        final String tokenTypeRaw;
        final int tokenLevel;
        final ItemStack target;

        HandTokenTarget(EquipmentSlot tokenHand, String tokenTypeRaw, int tokenLevel, ItemStack target) {
            this.tokenHand = tokenHand;
            this.tokenTypeRaw = tokenTypeRaw;
            this.tokenLevel = tokenLevel;
            this.target = target;
        }
    }
}
