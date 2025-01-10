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

import net.minecraft.item.ItemStack;

class ShopDatabase {
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
        String createShopsTable = "CREATE TABLE IF NOT EXISTS shops (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "item TEXT," +
                "description TEXT," +
                "isAdminShop BOOLEAN" +
                ");";

        String createItemsTable = "CREATE TABLE IF NOT EXISTS items (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "shopId INTEGER," +
                "nbtData TEXT," +
                "quantity INTEGER," +
                "isSelling BOOLEAN," +
                "price DECIMAL(10, 2)," +
                "creator TEXT," +
                "FOREIGN KEY(shopId) REFERENCES shops(id)" +
                ");";

        try (Statement stmt = getConnection().createStatement()) {
            stmt.execute(createShopsTable);
            stmt.execute(createItemsTable);
        } catch (SQLException e) {
            e.printStackTrace();
        }
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

    public boolean removeShopByName(String name) {
        String findShopSql = "SELECT id FROM shops WHERE name = ?";
        String deleteShopSql = "DELETE FROM shops WHERE id = ?";
        String deleteItemsSql = "DELETE FROM items WHERE shopId = ?";

        try (PreparedStatement findStmt = getConnection().prepareStatement(findShopSql)) {
            findStmt.setString(1, name);
            ResultSet rs = findStmt.executeQuery();
            if (rs.next()) {
                int shopId = rs.getInt("id");

                try (PreparedStatement deleteShopStmt = getConnection().prepareStatement(deleteShopSql);
                        PreparedStatement deleteItemsStmt = getConnection().prepareStatement(deleteItemsSql)) {
                    deleteShopStmt.setInt(1, shopId);
                    deleteItemsStmt.setInt(1, shopId);

                    deleteItemsStmt.executeUpdate();
                    deleteShopStmt.executeUpdate();
                    return true;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
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
        return "";
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

    public List<String> getAllShopNames() {
        String sql = "SELECT name FROM shops";
        List<String> shopNames = new ArrayList<>();
        
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                shopNames.add(rs.getString("name"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return shopNames;
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

    public void normalizeItemCounts() {
        String selectSql = "SELECT id, nbtData FROM items";
        String updateSql = "UPDATE items SET nbtData = ? WHERE id = ?";
        
        try (PreparedStatement selectStmt = getConnection().prepareStatement(selectSql)) {
            ResultSet rs = selectStmt.executeQuery();
            
            while (rs.next()) {
                int id = rs.getInt("id");
                String nbtJson = rs.getString("nbtData");
                JsonElement element = JsonParser.parseString(nbtJson);
                
                ItemStack itemStack = ItemStack.CODEC.decode(Simpleshop.jsonops, element)
                    .result()
                    .map(Pair::getFirst)
                    .orElse(null);
                
                if (itemStack != null && !itemStack.isEmpty() && itemStack.getCount() > 1) {
                    itemStack.setCount(1);
                    JsonElement updatedElement = ItemStack.CODEC
                        .encode(itemStack, Simpleshop.jsonops, Simpleshop.jsonops.empty())
                        .result()
                        .orElse(null);
                    
                    if (updatedElement != null) {
                        try (PreparedStatement updateStmt = getConnection().prepareStatement(updateSql)) {
                            updateStmt.setString(1, updatedElement.toString());
                            updateStmt.setInt(2, id);
                            updateStmt.executeUpdate();
                        }
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}