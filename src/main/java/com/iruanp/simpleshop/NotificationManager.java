package com.iruanp.simpleshop;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.server.MinecraftServer;

public class NotificationManager {
    private final ShopDatabase database;
    private final MinecraftServer server;

    public NotificationManager(ShopDatabase database, MinecraftServer server) {
        this.database = database;
        this.server = server;
    }

    public void notifyPlayer(String playerName, String message) {
        // Try to find online player
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerName);
        
        if (player != null && (!player.isDisconnected())) {
            // Player is online, send message directly
            player.sendMessage(Text.literal(message).formatted(Formatting.YELLOW), false);
        } else {
            // Player is offline, save notification to database
            database.addNotification(playerName, message);
        }
    }

    public void checkNotifications(ServerPlayerEntity player) {
        String playerName = player.getName().getString();
        for (String message : database.getUnreadNotifications(playerName)) {
            player.sendMessage(Text.literal(message).formatted(Formatting.YELLOW), false);
        }
        database.markNotificationsAsRead(playerName);
    }

    public void cleanOldNotifications() {
        database.cleanOldNotifications();
    }
} 