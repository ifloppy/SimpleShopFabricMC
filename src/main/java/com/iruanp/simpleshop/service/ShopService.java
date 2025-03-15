package com.iruanp.simpleshop.service;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;

import com.iruanp.simpleshop.ShopDatabase;
import com.iruanp.simpleshop.Simpleshop;
import com.iruanp.simpleshop.I18n;
import com.iruanp.simpleshop.PlayerUtils;

public class ShopService {
    private final ShopDatabase database;
    private final InventoryService inventoryService;

    public ShopService(ShopDatabase database) {
        this.database = database;
        this.inventoryService = new InventoryService();
    }

    public boolean shopExists(String shopName) {
        return database.shopExists(shopName);
    }

    public void createShop(String shopName, String serializedItem, String description, boolean isAdminShop) {
        database.addShop(shopName, serializedItem, description, isAdminShop);
    }

    public void updateShopIcon(String shopName, String serializedItem) {
        database.updateShopIcon(shopName, serializedItem);
    }

    public void updateShopName(String oldShopName, String newShopName) {
        database.updateShopName(oldShopName, newShopName);
    }

    public void deleteShop(String shopName) {
        database.deleteShop(shopName);
    }

    public boolean isShopEmpty(String shopName) {
        return database.isShopEmpty(shopName);
    }

    public void updateShopDescription(String shopName, String description) {
        database.updateShopDescription(shopName, description);
    }

    public List<ShopEntry> getShops() {
        return database.getShops();
    }

    public List<ShopItemEntry> getShopItems(String shopName) {
        return database.getShopItems(shopName);
    }

    public boolean isAdminShop(String shopName) {
        return database.isAdminShop(shopName);
    }

    public void stockItem(ServerPlayerEntity player, Integer itemId, int amount) {
        if (!database.itemExists(itemId)) {
            throw new IllegalStateException(I18n.translate("item.not_found", itemId).getString());
        }

        if (database.isAdminShopByItemId(itemId)) {
            throw new IllegalStateException(I18n.translate("item.no_permission", "stock").getString());
        }

        String itemCreator = database.getItemCreator(itemId);
        if (!player.getUuidAsString().equals(itemCreator)) {
            throw new IllegalStateException(I18n.translate("item.no_permission", "stock").getString());
        }

        ItemStack shopItem = database.getItemStack(itemId);
        if (shopItem == null) {
            throw new IllegalStateException(I18n.translate("item.not_found", itemId).getString());
        }

        int totalAvailable = inventoryService.countMatchingItems(player, shopItem);
        if (totalAvailable < amount) {
            throw new IllegalStateException(I18n.translate("item.stock.not_matching").getString());
        }

        if (inventoryService.removeItems(player, shopItem, amount)) {
            database.addStockToItem(itemId, amount);
        }
    }

    public void buyItem(ServerPlayerEntity player, Integer itemId, int amount) {
        if (!database.itemExists(itemId)) {
            throw new IllegalStateException(I18n.translate("item.not_found", itemId).getString());
        }

        if (!database.isItemForSale(itemId)) {
            throw new IllegalStateException(I18n.translate("item.not_for_sale").getString());
        }

        String itemCreator = database.getItemCreator(itemId);
        if (!database.isAdminShopByItemId(itemId) && player.getUuidAsString().equals(itemCreator)) {
            throw new IllegalStateException(I18n.translate("item.buy.own_shop").getString());
        }

        ItemStack shopItem = database.getItemStack(itemId);
        int maxPurchaseableAmount = calculateMaxPurchaseableAmount(player, itemId, amount);

        if (maxPurchaseableAmount <= 0) {
            if (!database.isAdminShopByItemId(itemId)) {
                int currentStock = database.getItemQuantity(itemId);
                throw new IllegalStateException(I18n.translate("error.insufficient_stock", currentStock).getString());
            }
            throw new IllegalStateException(I18n.translate("error.insufficient_space").getString());
        }

        ItemStack purchaseStack = shopItem.copy();
        purchaseStack.setCount(maxPurchaseableAmount);

        if (inventoryService.addItems(player, purchaseStack)) {
            if (!database.isAdminShopByItemId(itemId)) {
                database.removeStockFromItem(itemId, maxPurchaseableAmount);
            }
        }
    }

    public int calculateMaxPurchaseableAmount(ServerPlayerEntity player, Integer itemId, int requestedAmount) {
        if (database.isAdminShopByItemId(itemId)) {
            ItemStack shopItem = database.getItemStack(itemId);
            ItemStack testStack = shopItem.copy();
            testStack.setCount(requestedAmount);
            return inventoryService.hasEnoughInventorySpace(player, testStack) ? requestedAmount : 0;
        } else {
            int currentStock = database.getItemQuantity(itemId);
            int maxFromStock = Math.min(requestedAmount, currentStock);
            ItemStack shopItem = database.getItemStack(itemId);
            ItemStack testStack = shopItem.copy();
            testStack.setCount(maxFromStock);
            return inventoryService.hasEnoughInventorySpace(player, testStack) ? maxFromStock : 0;
        }
    }

    public List<ShopEntry> getUserShops(String excludeShopName) {
        List<ShopEntry> shops = new ArrayList<>();
        String sql = "SELECT name, description, isAdminShop, item FROM shops WHERE isAdminShop = 0 AND name != ? ORDER BY name";

        try (PreparedStatement pstmt = database.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, excludeShopName);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                shops.add(new ShopEntry(
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getBoolean("isAdminShop"),
                        rs.getString("item")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return shops;
    }

    public void moveItemToShop(ServerPlayerEntity player, int itemId, String fromShopName, String toShopName) {
        try {
            int targetShopId = database.getShopIdByName(toShopName);
            
            String sql = "UPDATE items SET shopId = ? WHERE id = ?";
            try (PreparedStatement pstmt = database.getConnection().prepareStatement(sql)) {
                pstmt.setInt(1, targetShopId);
                pstmt.setInt(2, itemId);
                pstmt.executeUpdate();
            }

            player.sendMessage(I18n.translate("item.move.success", fromShopName, toShopName).formatted(Formatting.GREEN), false);

            String creatorName = PlayerUtils.getPlayerName(UUID.fromString(database.getItemCreator(itemId)));
            if (creatorName != null) {
                String notificationMsg = I18n.translate("notification.item.moved", 
                    player.getName().getString(), fromShopName, toShopName).getString();
                Simpleshop.getInstance().getNotificationManager().notifyPlayer(creatorName, notificationMsg);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            player.sendMessage(I18n.translate("error.move_failed").formatted(Formatting.RED), false);
            throw new IllegalStateException(e);
        }
    }
}