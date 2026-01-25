package com.orangeslices.bossencounters.command;

import com.orangeslices.bossencounters.BossEncountersPlugin;
import com.orangeslices.bossencounters.boss.apply.BossApplier;
import com.orangeslices.bossencounters.raffle.RaffleTokenFactory;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;

public final class BecCommand implements CommandExecutor {

    private final BossEncountersPlugin plugin;
    private final BossApplier bossApplier;

    private final NamespacedKey bossKey;
    private final NamespacedKey rankKey;
    private final NamespacedKey affixesKey;

    public BecCommand(BossEncountersPlugin plugin, BossApplier bossApplier) {
        this.plugin = plugin;
        this.bossApplier = bossApplier;

        this.bossKey = new NamespacedKey(plugin, "is_boss");
        this.rankKey = new NamespacedKey(plugin, "rank");
        this.affixesKey = new NamespacedKey(plugin, "affixes");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Player only.");
            return true;
        }

        if (args.length == 0) {
            help(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("test")) {
            handleTest(player, args);
            return true;
        }

        if (args[0].equalsIgnoreCase("raffle")) {
            handleRaffle(player, args);
            return true;
        }

        help(player);
        return true;
    }

    private void handleRaffle(Player player, String[] args) {
        // OP only: /bec raffle [amount]
        if (!player.isOp()) {
            player.sendMessage(ChatColor.RED + "You must be OP to use this command.");
            return;
        }

        int amount = 1;
        if (args.length >= 2) {
            try {
                amount = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
                amount = 1;
            }
        }

        amount = Math.max(1, Math.min(64, amount));

        var token = RaffleTokenFactory.createToken();
        token.setAmount(amount);

        player.getInventory().addItem(token);
        player.sendMessage(ChatColor.GREEN + "Given " + amount + " raffle token(s).");
    }

    private void handleTest(Player player, String[] args) {
        // /bec test <RANK> <MOB> [affix1,affix2,...]
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /bec test <RANK> <MOB> [affixes]");
            player.sendMessage(ChatColor.GRAY + "Example: /bec test GOLD ZOMBIE lifesteal,mark,thorns");
            return;
        }

        String rank = args[1].toUpperCase(Locale.ROOT);

        if (!plugin.getConfig().isConfigurationSection("ranks." + rank)) {
            player.sendMessage(ChatColor.RED + "Unknown rank: " + rank);
            return;
        }

        EntityType type;
        try {
            type = EntityType.valueOf(args[2].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            player.sendMessage(ChatColor.RED + "Unknown mob type: " + args[2]);
            return;
        }

        if (!type.isAlive() || !LivingEntity.class.isAssignableFrom(type.getEntityClass())) {
            player.sendMessage(ChatColor.RED + "That entity isn't a living mob: " + type.name());
            return;
        }

        World world = player.getWorld();
        LivingEntity mob = (LivingEntity) world.spawnEntity(player.getLocation(), type);

        // Force rank BEFORE pipeline runs
        mob.getPersistentDataContainer().set(rankKey, PersistentDataType.STRING, rank);

        // Optional forced affixes (csv)
        if (args.length >= 4) {
            String csv = args[3].trim();
            if (!csv.isBlank()) {
                // store exactly what you typed (lowercase ids are what your config uses)
                mob.getPersistentDataContainer().set(affixesKey, PersistentDataType.STRING, csv.toLowerCase(Locale.ROOT));
            }
        }

        // Run normal boss creation pipeline (messages/fx/despawn/stats)
        plugin.onBossCreated(mob);

        player.sendMessage(ChatColor.GREEN + "Spawned " + rank + " boss " + type.name()
                + (args.length >= 4 ? (" with affixes: " + args[3]) : ""));
    }

    private void help(Player player) {
        player.sendMessage(ChatColor.GOLD + "BossEncounters Commands:");
        player.sendMessage(ChatColor.YELLOW + "/bec test <RANK> <MOB> [affixes]");
        player.sendMessage(ChatColor.GRAY + "Example: /bec test GOLD ZOMBIE lifesteal,mark,thorns");
        player.sendMessage(ChatColor.GRAY + "Ranks: GRAY, GREEN, RED, PURPLE, GOLD");

        if (player.isOp()) {
            player.sendMessage(ChatColor.YELLOW + "/bec raffle [amount]");
            player.sendMessage(ChatColor.GRAY + "Gives raffle tokens for testing (OP only).");
        }
    }
}
