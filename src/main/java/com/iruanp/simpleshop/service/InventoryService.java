package com.iruanp.simpleshop.service;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import com.google.gson.JsonElement;
import com.iruanp.simpleshop.Simpleshop;
import com.mojang.serialization.JsonOps;

public class InventoryService {
    public int countMatchingItems(ServerPlayerEntity player, ItemStack targetItem) {
        int total = 0;
        PlayerInventory inventory = player.getInventory();

        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty() && ItemStack.areItemsAndComponentsEqual(stack, targetItem)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    public boolean removeItems(ServerPlayerEntity player, ItemStack targetItem, int amount) {
        PlayerInventory inventory = player.getInventory();
        int remaining = amount;

        for (int i = 0; i < inventory.size() && remaining > 0; i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty() && stack.isOf(targetItem.getItem())) {
                int toRemove = Math.min(remaining, stack.getCount());
                int newCount = stack.getCount() - toRemove;
                if (newCount > 0) {
                    ItemStack newStack = stack.copy();
                    newStack.setCount(newCount);
                    inventory.setStack(i, newStack);
                } else {
                    inventory.setStack(i, ItemStack.EMPTY);
                }
                remaining -= toRemove;
            }
        }

        return remaining == 0;
    }

    public boolean hasEnoughInventorySpace(ServerPlayerEntity player, ItemStack itemToAdd) {
        PlayerInventory inventory = player.getInventory();
        int amountLeft = itemToAdd.getCount();

        for (int i = 0; i < inventory.main.size() && amountLeft > 0; i++) {
            ItemStack stack = inventory.main.get(i);
            if (stack.isEmpty()) {
                amountLeft -= itemToAdd.getMaxCount();
            } else if (stack.isOf(itemToAdd.getItem()) && stack.isStackable()) {
                amountLeft -= (stack.getMaxCount() - stack.getCount());
            }
        }

        return amountLeft <= 0;
    }

    public boolean addItems(ServerPlayerEntity player, ItemStack itemStack) {
        return player.getInventory().insertStack(itemStack);
    }

    public String serializeHeldItem(ServerPlayerEntity player) {
        ItemStack heldItem = player.getMainHandStack();
        if (heldItem.isEmpty()) {
            return null;
        }
        return serializeHeldItem(heldItem);
    }

    public String serializeHeldItem(ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return null;
        }
        return ItemStack.CODEC
                .encode(itemStack, Simpleshop.jsonops, JsonOps.INSTANCE.empty())
                .result()
                .map(JsonElement::toString)
                .orElse(null);
    }
}