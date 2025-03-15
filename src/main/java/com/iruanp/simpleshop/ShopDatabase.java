package com.iruanp.simpleshop;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import com.iruanp.simpleshop.service.ShopEntry;
import com.iruanp.simpleshop.service.ShopItemEntry;

import net.minecraft.item.ItemStack;

public class ShopDatabase {
    private static String DB_URL;
    private Connection connection;

    public ShopDatabase() {
        DB_URL = "jdbc:sqlite:" + Simpleshop.savePath + "/simpleshop.db";
        connect();
        initializeDatabase();
    }

    private void connect() {
        try {
            connection = DriverManager.getConnection(DB_URL);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void initializeDatabase() {
        try (Statement stmt = getConnection().createStatement()) {
            // Create shops table
            stmt.execute("CREATE TABLE IF NOT EXISTS shops (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "name TEXT NOT NULL," +
                    "item TEXT," +
                    "description TEXT," +
                    "isAdminShop BOOLEAN" +
                    ")");

            // Create items table
            stmt.execute("CREATE TABLE IF NOT EXISTS items (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "shopId INTEGER," +
                    "nbtData TEXT," +
                    "quantity INTEGER," +
                    "isSelling BOOLEAN," +
                    "price DECIMAL(10, 2)," +
                    "creator TEXT," +
                    "FOREIGN KEY(shopId) REFERENCES shops(id)" +
                    ")");

            // Create notifications table
            stmt.execute("CREATE TABLE IF NOT EXISTS notifications (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "player_name TEXT NOT NULL," +
                    "message TEXT NOT NULL," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "is_read INTEGER DEFAULT 0" +
                    ")");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void addNotification(String playerName, String message) {
        String sql = "INSERT INTO notifications (player_name, message) VALUES (?, ?)";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, playerName);
            pstmt.setString(2, message);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<String> getUnreadNotifications(String playerName) {
        List<String> notifications = new ArrayList<>();
        String sql = "SELECT message FROM notifications WHERE player_name = ? AND is_read = 0";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, playerName);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                notifications.add(rs.getString("message"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return notifications;
    }

    public void markNotificationsAsRead(String playerName) {
        String sql = "UPDATE notifications SET is_read = 1 WHERE player_name = ? AND is_read = 0";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, playerName);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void cleanOldNotifications() {
        String sql = "DELETE FROM notifications WHERE created_at < datetime('now', '-30 days')";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<ShopEntry> getShops() {
        List<ShopEntry> shops = new ArrayList<>();
        String sql = "SELECT name, description, isAdminShop, item FROM shops ORDER BY name";

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
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

    public void addShop(String name, String item, String description, boolean isAdminShop) {
        String sql = "INSERT INTO shops(name, item, description, isAdminShop) VALUES(?, ?, ?, ?)";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.setString(2, item);
            pstmt.setString(3, description);
            pstmt.setBoolean(4, isAdminShop);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean shopExists(String name) {
        String sql = "SELECT COUNT(*) FROM shops WHERE name = ?";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, name);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean isAdminShop(String name) {
        String sql = "SELECT isAdminShop FROM shops WHERE name = ?";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, name);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getBoolean("isAdminShop");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public int getShopIdByName(String name) {
        String sql = "SELECT id FROM shops WHERE name = ?";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, name);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public String getItemCreator(Integer itemId) {
        String sql = "SELECT creator FROM items WHERE id = ?";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, itemId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("creator");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<ShopItemEntry> getShopItems(String shopName) {
        List<ShopItemEntry> items = new ArrayList<>();
        String sql = "SELECT i.id, i.nbtData, i.quantity, i.isSelling, i.price, i.creator " +
                    "FROM items i " +
                    "JOIN shops s ON i.shopId = s.id " +
                    "WHERE s.name = ?";

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, shopName);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                String nbtData = rs.getString("nbtData");
                ItemStack itemStack = ItemStack.CODEC.decode(Simpleshop.jsonops, JsonParser.parseString(nbtData))
                    .result()
                    .map(Pair::getFirst)
                    .orElse(ItemStack.EMPTY);
                items.add(new ShopItemEntry(
                    rs.getInt("id"),
                    itemStack,
                    rs.getBigDecimal("price"),
                    rs.getInt("quantity"),
                    rs.getBoolean("isSelling"),
                    rs.getString("creator")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return items;
    }

    public boolean itemExists(Integer itemId) {
        String sql = "SELECT COUNT(*) FROM items WHERE id = ?";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, itemId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void removeItem(Integer itemId) {
        String sql = "DELETE FROM items WHERE id = ?";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, itemId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean isAdminShopByItemId(Integer itemId) {
        String sql = "SELECT s.isAdminShop FROM shops s JOIN items i ON s.id = i.shopId WHERE i.id = ?";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, itemId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getBoolean("isAdminShop");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public ItemStack getItemStack(Integer itemId) {
        String sql = "SELECT nbtData FROM items WHERE id = ?";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, itemId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String nbtJson = rs.getString("nbtData");
                JsonElement element = JsonParser.parseString(nbtJson);
                ItemStack itemStack = ItemStack.CODEC.decode(Simpleshop.jsonops, element)
                    .result()
                    .map(Pair::getFirst)
                    .orElse(ItemStack.EMPTY);
                
                // Ensure item count is 1
                if (!itemStack.isEmpty() && itemStack.getCount() > 1) {
                    itemStack.setCount(1);
                }
                return itemStack;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return ItemStack.EMPTY;
    }

    public void addStockToItem(Integer itemId, int amount) {
        String sql = "UPDATE items SET quantity = quantity + ? WHERE id = ?";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, amount);
            pstmt.setInt(2, itemId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean isItemForSale(Integer itemId) {
        String sql = "SELECT isSelling FROM items WHERE id = ?";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, itemId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getBoolean("isSelling");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public int getItemQuantity(Integer itemId) {
        String sql = "SELECT quantity FROM items WHERE id = ?";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, itemId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("quantity");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public BigDecimal getItemPrice(Integer itemId) {
        String sql = "SELECT price FROM items WHERE id = ?";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, itemId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getBigDecimal("price");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return BigDecimal.ZERO;
    }

    public void removeStockFromItem(Integer itemId, int amount) {
        String sql = "UPDATE items SET quantity = quantity - ? WHERE id = ?";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, amount);
            pstmt.setInt(2, itemId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                connect();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            connect();
        }
        return this.connection;
    }

    public void updateItemPrice(int itemId, BigDecimal price) {
        String sql = "UPDATE items SET price = ? WHERE id = ?";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setBigDecimal(1, price);
            pstmt.setInt(2, itemId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void toggleItemMode(int itemId) {
        String sql = "UPDATE items SET isSelling = NOT isSelling WHERE id = ?";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, itemId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void updateShopDescription(String shopName, String description) {
        String sql = "UPDATE shops SET description = ? WHERE name = ?";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, description);
            pstmt.setString(2, shopName);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void updateShopName(String oldName, String newName) {
        String sql = "UPDATE shops SET name = ? WHERE name = ?";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, newName);
            pstmt.setString(2, oldName);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void updateShopIcon(String shopName, String itemNbtData) {
        String sql = "UPDATE shops SET item = ? WHERE name = ?";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, itemNbtData);
            pstmt.setString(2, shopName);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void addItem(int shopId, String nbtData, int quantity, boolean isSelling, BigDecimal price, String creator) {
        String sql = "INSERT INTO items(shopId, nbtData, quantity, isSelling, price, creator) VALUES(?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, shopId);
            pstmt.setString(2, nbtData);
            pstmt.setInt(3, quantity);
            pstmt.setBoolean(4, isSelling);
            pstmt.setBigDecimal(5, price);
            pstmt.setString(6, creator);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean isShopEmpty(String shopName) {
        String sql = "SELECT COUNT(*) FROM items i JOIN shops s ON i.shopId = s.id WHERE s.name = ?";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, shopName);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) == 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void deleteShop(String shopName) {
        // First delete all items in the shop
        String deleteItemsSql = "DELETE FROM items WHERE shopId IN (SELECT id FROM shops WHERE name = ?)";
        String deleteShopSql = "DELETE FROM shops WHERE name = ?";
        
        try {
            // Delete items first
            try (PreparedStatement pstmt = getConnection().prepareStatement(deleteItemsSql)) {
                pstmt.setString(1, shopName);
                pstmt.executeUpdate();
            }
            
            // Then delete the shop
            try (PreparedStatement pstmt = getConnection().prepareStatement(deleteShopSql)) {
                pstmt.setString(1, shopName);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}