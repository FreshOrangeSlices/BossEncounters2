package com.orangeslices.bossencounters.message;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class MessageService {

    public enum Mode {
        CHAT,
        ACTIONBAR,
        TITLE;

        public static Mode fromConfig(String raw, Mode fallback) {
            if (raw == null) return fallback;
            try {
                return Mode.valueOf(raw.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                return fallback;
            }
        }
    }

    private final Plugin plugin;

    public MessageService(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Broadcast a message to players within a radius of a location.
     * Message supports & color codes.
     */
    public void broadcastLocal(Location at, double radius, String msgColored, Mode mode) {
        if (at == null) return;
        World w = at.getWorld();
        if (w == null) return;

        double r2 = radius * radius;
        String colored = ChatColor.translateAlternateColorCodes('&', msgColored == null ? "" : msgColored);

        for (Player p : w.getPlayers()) {
            if (p.getLocation().distanceSquared(at) > r2) continue;

            switch (mode) {
                case ACTIONBAR -> sendActionBar(p, colored);
                case TITLE -> p.sendTitle(colored, "", 5, 40, 10);
                case CHAT -> p.sendMessage(colored);
            }
        }
    }

    public void broadcastLocalChat(Location at, double radius, String msgColored) {
        broadcastLocal(at, radius, msgColored, Mode.CHAT);
    }

    public void broadcastLocalActionbar(Location at, double radius, String msgColored) {
        broadcastLocal(at, radius, msgColored, Mode.ACTIONBAR);
    }

    public void broadcastLocalTitle(Location at, double radius, String msgColored) {
        broadcastLocal(at, radius, msgColored, Mode.TITLE);
    }

    private void sendActionBar(Player player, String coloredMessage) {
        try {
            player.spigot().sendMessage(
                    net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    net.md_5.bungee.api.chat.TextComponent.fromLegacyText(coloredMessage)
            );
        } catch (Throwable t) {
            // Fallback if server jar is weird / API mismatch
            player.sendMessage(coloredMessage);
            plugin.getLogger().fine("ActionBar failed, fell back to chat: " + t.getMessage());
        }
    }
}
