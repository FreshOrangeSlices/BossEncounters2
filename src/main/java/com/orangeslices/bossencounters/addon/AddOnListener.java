package com.orangeslices.bossencounters.addon;

import com.orangeslices.bossencounters.BossEncountersPlugin;
import com.orangeslices.bossencounters.raffle.RaffleApplyResult;
import com.orangeslices.bossencounters.raffle.RaffleService;
import com.orangeslices.bossencounters.raffle.util.RaffleLoreUtil;
import com.orangeslices.bossencounters.token.TokenDefinition;
import com.orangeslices.bossencounters.token.TokenKeys;
import com.orangeslices.bossencounters.token.TokenRegistry;
import com.orangeslices.bossencounters.token.TokenType;
import org.bukkit.ChatColor;
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

    private final TokenKeys keys;
    private final TokenRegistry registry;

    private final NamespacedKey tokenTypeKey;
    private final NamespacedKey tokenLevelKey;

    private final NamespacedKey legacySharpenKitKey;
    private final NamespacedKey legacyMarkKitKey;

    private final NamespacedKey waterBreathingKey;

    public AddOnListener(BossEncountersPlugin plugin) {
        this.plugin = plugin;

        this.keys = new TokenKeys(plugin);
        this.registry = new TokenRegistry();

        this.tokenTypeKey = keys.tokenType();
        this.tokenLevelKey = keys.custom("token_level");

        this.legacySharpenKitKey = keys.custom("sharpen_kit_level");
        this.legacyMarkKitKey = keys.custom("mark_kit_level");

        this.waterBreathingKey = keys.custom("water_breathing_level");
    }

    @EventHandler
    public void onApplyAddon(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (!player.isSneaking()) return;
        if (!event.getAction().isRightClick()) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();

        // =========================
        // 🎟️ RAFFLE APPLY (NEW)
        // =========================
        if (tryApplyRaffle(player, event, main, off)) return;

        // =========================
        // EXISTING ADDONS (UNCHANGED)
        // =========================
        HandTokenTarget tt = resolveTokenAndTarget(main, off);
        if (tt == null) return;

        boolean applied = applyTokenToTarget(tt.tokenTypeRaw, tt.tokenLevel, tt.target);
        if (!applied) return;

        consumeOneFromHand(player, tt.tokenHand);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7f, 1.4f);
        event.setCancelled(true);
    }

    private boolean tryApplyRaffle(Player player, PlayerInteractEvent event, ItemStack main, ItemStack off) {
        boolean mainIsRaffle = RaffleTokenFactory.isRaffleToken(main);
        boolean offIsRaffle = RaffleTokenFactory.isRaffleToken(off);

        // main hand token -> off hand armor
        if (mainIsRaffle && isArmor(off)) {
            RaffleApplyResult result = RaffleService.applyToArmor(plugin, off);

            if (result.isSuccess()) {
                consumeOneFromHand(player, EquipmentSlot.HAND);
                RaffleLoreUtil.updateVagueLore(off, result.getUsedSlots(), result.getMaxSlots());
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7f, 1.4f);
                player.sendMessage(ChatColor.GREEN + "Add-on applied.");
            } else {
                player.sendMessage(ChatColor.RED + result.getMessage());
            }

            event.setCancelled(true);
            return true;
        }

        // off hand token -> main hand armor
        if (offIsRaffle && isArmor(main)) {
            RaffleApplyResult result = RaffleService.applyToArmor(plugin, main);

            if (result.isSuccess()) {
                consumeOneFromHand(player, EquipmentSlot.OFF_HAND);
                RaffleLoreUtil.updateVagueLore(main, result.getUsedSlots(), result.getMaxSlots());
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7f, 1.4f);
                player.sendMessage(ChatColor.GREEN + "Add-on applied.");
            } else {
                player.sendMessage(ChatColor.RED + result.getMessage());
            }

            event.setCancelled(true);
            return true;
        }

        if (mainIsRaffle || offIsRaffle) {
            player.sendMessage(ChatColor.RED + "Hold armor in the other hand to apply the raffle token.");
            event.setCancelled(true);
            return true;
        }

        return false;
    }

    /* =========================
       EXISTING ADDON SYSTEM
       ========================= */

    private boolean applyTokenToTarget(String tokenTypeRaw, int tokenLevel, ItemStack target) {
        if (target == null || target.getType() == Material.AIR) return false;

        String normalized = normalizeTokenType(tokenTypeRaw);

        if (normalized.equals("WATER_BREATHING")) {
            if (!isHelmet(target)) return false;
            int next = clampGeneric(tokenLevel);
            return applyLevelToItem(target, next, waterBreathingKey, "§7Water Breathing: ");
        }

        TokenType type = parseTokenType(normalized);
        if (type == null) return false;

        TokenDefinition def = registry.get(type);
        if (def == null) return false;

        int clamped = def.clampLevel(tokenLevel);
        if (!isValidTarget(def.target(), type, target)) return false;

        NamespacedKey appliedKey = keyForAppliedLevel(type);
        if (appliedKey == null) return false;

        String lorePrefix = "§7" + def.displayName() + ": ";
        return applyLevelToItem(target, clamped, appliedKey, lorePrefix);
    }

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
        if (level == 1) return "I";
        if (level == 2) return "II";
        return String.valueOf(level);
    }

    private int clampGeneric(int lvl) {
        return Math.max(1, Math.min(2, lvl));
    }

    private String normalizeTokenType(String raw) {
        if (raw == null) return "";
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private TokenType parseTokenType(String normalized) {
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

    private boolean isValidTarget(TokenDefinition.Target rule, TokenType type, ItemStack item) {
        if (type == TokenType.HASTE) return isToolOrWeapon(item);
        return switch (rule) {
            case WEAPON -> isWeapon(item);
            case TOOL -> isToolOrWeapon(item);
            case ARMOR_HELMET -> isHelmet(item);
            case ARMOR_CHESTPLATE -> isChestplate(item);
            case ARMOR_LEGGINGS -> isLeggings(item);
            case ARMOR_BOOTS -> isBoots(item);
            case ARMOR_ANY -> isArmor(item);
            case ANY -> true;
        };
    }

    private boolean isWeapon(ItemStack item) {
        String n = item.getType().name();
        return n.endsWith("_SWORD") || n.endsWith("_AXE") || item.getType() == Material.TRIDENT;
    }

    private boolean isToolOrWeapon(ItemStack item) {
        String n = item.getType().name();
        return n.endsWith("_PICKAXE") || n.endsWith("_AXE") || n.endsWith("_SHOVEL") || n.endsWith("_HOE") || isWeapon(item);
    }

    private boolean isArmor(ItemStack item) {
        String n = item.getType().name();
        return n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS");
    }

    private boolean isHelmet(ItemStack item) { return item.getType().name().endsWith("_HELMET"); }
    private boolean isChestplate(ItemStack item) { return item.getType().name().endsWith("_CHESTPLATE"); }
    private boolean isLeggings(ItemStack item) { return item.getType().name().endsWith("_LEGGINGS"); }
    private boolean isBoots(ItemStack item) { return item.getType().name().endsWith("_BOOTS"); }

    private void consumeOneFromHand(Player player, EquipmentSlot hand) {
        ItemStack item = hand == EquipmentSlot.HAND
                ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();

        if (item == null || item.getType() == Material.AIR) return;

        if (item.getAmount() <= 1) {
            if (hand == EquipmentSlot.HAND) player.getInventory().setItemInMainHand(null);
            else player.getInventory().setItemInOffHand(null);
        } else {
            item.setAmount(item.getAmount() - 1);
        }
    }

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
