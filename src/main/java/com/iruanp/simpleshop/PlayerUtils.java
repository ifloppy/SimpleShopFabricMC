package com.iruanp.simpleshop;

import com.mojang.authlib.GameProfile;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.UUID;

public class PlayerUtils {
    public static String getPlayerName(UUID playerUUID) {
        // Get the player entity if they are online
        ServerPlayerEntity player = Simpleshop.getInstance().serverInstance.getPlayerManager().getPlayer(playerUUID);
        if (player != null) {
            return player.getName().getString();
        }

        // If the player is offline, load their profile
        GameProfile profile = Simpleshop.getInstance().serverInstance.getUserCache().getByUuid(playerUUID).orElse(null);
        if (profile != null) {
            return profile.getName();
        }

        // If the profile is not found, return null or handle accordingly
        return null;
    }

    public static boolean hasEnoughInventorySpace(ServerPlayerEntity player, ItemStack itemToAdd) {
        PlayerInventory inventory = player.getInventory();
        int availableSpace = 0;

        // Check empty slots and partially filled slots
        for (int i = 0; i < inventory.main.size(); i++) {
            ItemStack stack = inventory.main.get(i);
            if (stack.isEmpty()) {
                availableSpace += itemToAdd.getMaxCount();
            } else if (stack.getItem() == itemToAdd.getItem() && stack.getCount() < stack.getMaxCount()) {
                availableSpace += stack.getMaxCount() - stack.getCount();
            }
        }

        return availableSpace >= (itemToAdd.getCount());

    }
}