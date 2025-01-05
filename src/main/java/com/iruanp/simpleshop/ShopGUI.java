package com.iruanp.simpleshop;

import eu.pb4.sgui.api.gui.SimpleGui;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import me.lucko.fabric.api.permissions.v0.Permissions;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.JsonOps;

public class ShopGUI {
    private static final int ROWS = 6;
    private static final int SLOTS_PER_PAGE = (ROWS - 1) * 9;
    private ShopDatabase database;

    public ShopGUI(ShopDatabase database) {
        this.database = database;
    }

    public void openShopList(ServerPlayerEntity player, int page) {
        List<ShopEntry> shops = getShops();
        int maxPages = (shops.size() + SLOTS_PER_PAGE - 1) / SLOTS_PER_PAGE;
        if (maxPages == 0)
            maxPages = 1;
        if (page < 0)
            page = 0;
        if (page >= maxPages)
            page = maxPages - 1;

        SimpleGui gui = new SimpleGui(ScreenHandlerType.GENERIC_9X6, player, false);
        gui.setTitle(Text.literal("Shop List - Page " + (page + 1) + "/" + maxPages));

        int startIndex = page * SLOTS_PER_PAGE;
        int endIndex = Math.min(startIndex + SLOTS_PER_PAGE, shops.size());

        for (int i = startIndex; i < endIndex; i++) {
            ShopEntry shop = shops.get(i);
            int slot = i - startIndex;
            String shopName = shop.name;
            JsonElement shopItem = JsonParser.parseString(shop.item);
            ItemStack shopItemStack = ItemStack.CODEC.decode(JsonOps.INSTANCE, shopItem)
                    .resultOrPartial(Simpleshop.LOGGER::error)
                    .map(Pair::getFirst)
                    .orElse(null);

            GuiElementBuilder element = new GuiElementBuilder()
                    .setItem(shopItemStack.getItem())
                    .setName(Text.literal(shop.name))
                    .addLoreLine(Text.literal("Type: " + (shop.isAdminShop ? "Admin Shop" : "Player Shop"))
                            .formatted(Formatting.GRAY));

            if (!shop.description.isEmpty()) {
                element.addLoreLine(Text.empty());
                element.addLoreLine(Text.literal("Description:").formatted(Formatting.YELLOW));
                element.addLoreLine(Text.literal(shop.description).formatted(Formatting.GRAY));
            }

            element.addLoreLine(Text.empty());
            element.addLoreLine(Text.literal("Click to view items").formatted(Formatting.GREEN));

            element.setCallback((index, type, action) -> {
                openShopItems(player, shopName, 0);
            });

            gui.setSlot(slot, element.build());
        }

        addNavigationButtons(gui, player, page, maxPages);
        gui.open();
    }

    private void openShopItems(ServerPlayerEntity player, String shopName, int page) {
        List<ShopItemEntry> items = getShopItems(shopName);
        int maxPages = (items.size() + SLOTS_PER_PAGE - 1) / SLOTS_PER_PAGE;
        if (maxPages == 0)
            maxPages = 1;
        if (page < 0)
            page = 0;
        if (page >= maxPages)
            page = maxPages - 1;

        SimpleGui gui = new SimpleGui(ScreenHandlerType.GENERIC_9X6, player, false);
        gui.setTitle(Text.literal(shopName + " - Page " + (page + 1) + "/" + maxPages));

        int startIndex = page * SLOTS_PER_PAGE;
        int endIndex = Math.min(startIndex + SLOTS_PER_PAGE, items.size());

        for (int i = startIndex; i < endIndex; i++) {
            ShopItemEntry item = items.get(i);
            int slot = i - startIndex;

            GuiElementBuilder element = new GuiElementBuilder(item.itemStack.copy());
            element.addLoreLine(Text.empty());

            if (item.isSelling) {
                element.addLoreLine(Text.literal("Buy Price: " + Simpleshop.getInstance().formatPrice(item.price))
                        .formatted(Formatting.GREEN));
                element.addLoreLine(Text.literal("Stock: " + item.quantity).formatted(Formatting.AQUA));
            } else {
                element.addLoreLine(Text.literal("Sell Price: " + Simpleshop.getInstance().formatPrice(item.price))
                        .formatted(Formatting.YELLOW));
            }

            element.addLoreLine(Text.empty());
            element.addLoreLine(Text.literal("Click to view commands").formatted(Formatting.GRAY));

            element.setCallback((index, type, action) -> {
                openItemDetails(player, shopName, item.id);
            });

            gui.setSlot(slot, element.build());
        }

        // Back button
        gui.setSlot(SLOTS_PER_PAGE + 4, new GuiElementBuilder()
                .setItem(Items.BARRIER)
                .setName(Text.literal("Back to Shops").formatted(Formatting.RED))
                .setCallback((index, type, action) -> openShopList(player, 0))
                .build());

        addNavigationButtons(gui, player, page, maxPages, shopName);
        gui.open();
    }

    private void addNavigationButtons(SimpleGui gui, ServerPlayerEntity player, int page, int maxPages) {
        addNavigationButtons(gui, player, page, maxPages, null);
    }

    private void addNavigationButtons(SimpleGui gui, ServerPlayerEntity player, int page, int maxPages,
            String shopName) {
        if (page > 0) {
            final int currentPage = page;
            gui.setSlot(SLOTS_PER_PAGE + 3, new GuiElementBuilder()
                    .setItem(Items.ARROW)
                    .setName(Text.literal("Previous Page").formatted(Formatting.YELLOW))
                    .setCallback((index, type, action) -> {
                        if (shopName != null) {
                            openShopItems(player, shopName, currentPage - 1);
                        } else {
                            openShopList(player, currentPage - 1);
                        }
                    })
                    .build());
        }

        if (page < maxPages - 1) {
            final int currentPage = page;
            gui.setSlot(SLOTS_PER_PAGE + 5, new GuiElementBuilder()
                    .setItem(Items.ARROW)
                    .setName(Text.literal("Next Page").formatted(Formatting.YELLOW))
                    .setCallback((index, type, action) -> {
                        if (shopName != null) {
                            openShopItems(player, shopName, currentPage + 1);
                        } else {
                            openShopList(player, currentPage + 1);
                        }
                    })
                    .build());
        }
    }

    private List<ShopItemEntry> getShopItems(String shopName) {
        List<ShopItemEntry> items = new ArrayList<>();
        String sql = "SELECT i.id, i.nbtData, i.quantity, i.isSelling, i.price FROM items i " +
                "JOIN shops s ON i.shopId = s.id WHERE s.name = ?";

        try (PreparedStatement pstmt = database.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, shopName);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                ItemStack itemStack = database.getItemStack(rs.getInt("id"));
                if (itemStack != null) {
                    items.add(new ShopItemEntry(
                            rs.getInt("id"),
                            itemStack,
                            rs.getInt("quantity"),
                            rs.getBoolean("isSelling"),
                            rs.getBigDecimal("price")));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return items;
    }

    private List<ShopEntry> getShops() {
        List<ShopEntry> shops = new ArrayList<>();
        String sql = "SELECT name, description, isAdminShop, item FROM shops ORDER BY name";

        try (PreparedStatement pstmt = database.getConnection().prepareStatement(sql)) {
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

    public void openItemDetails(ServerPlayerEntity player, String shopName, int itemId) {
        SimpleGui gui = new SimpleGui(ScreenHandlerType.GENERIC_9X3, player, false);

        try {
            // Get item details
            String sql = "SELECT i.*, s.isAdminShop, i.creator FROM items i " +
                    "JOIN shops s ON i.shopId = s.id " +
                    "WHERE s.name = ? AND i.id = ?";

            try (PreparedStatement pstmt = database.getConnection().prepareStatement(sql)) {
                pstmt.setString(1, shopName);
                pstmt.setInt(2, itemId);
                ResultSet rs = pstmt.executeQuery();

                if (rs.next()) {
                    ItemStack itemStack = database.getItemStack(itemId);
                    boolean isSelling = rs.getBoolean("isSelling");
                    int quantity = rs.getInt("quantity");
                    BigDecimal price = rs.getBigDecimal("price");
                    String creatorUUID = rs.getString("creator");
                    String creator;
                    if (creatorUUID != null) {
                        creator = PlayerUtils.getPlayerName(UUID.fromString(creatorUUID));
                    } else {
                        creator = "Unknown";
                    }
                    boolean isCreator = player.getName().getString().equals(creator);
                    boolean isAdmin = Permissions.check(player, "Simpleshop.Admin", 2);

                    // Display item
                    GuiElementBuilder itemElement = new GuiElementBuilder(itemStack.copy())
                            .addLoreLine(Text.empty())
                            .addLoreLine(Text.literal("Quantity: " + quantity).formatted(Formatting.AQUA))
                            .addLoreLine(Text
                                    .literal(isSelling
                                            ? "Buy Price: " + Simpleshop.getInstance().formatPrice(price)
                                            : "Sell Price: " + Simpleshop.getInstance().formatPrice(price))
                                    .formatted(isSelling ? Formatting.GREEN : Formatting.YELLOW))
                            .addLoreLine(Text.empty())
                            .addLoreLine(Text.literal("Creator: " + creator).formatted(Formatting.GRAY));

                    gui.setSlot(4, itemElement.build());

                    // Buy/Sell button
                    if (isSelling) {
                        gui.setSlot(11, new GuiElementBuilder(Items.EMERALD)
                                .setName(Text.literal("Buy Item").formatted(Formatting.GREEN))
                                .addLoreLine(Text.literal("Click to buy").formatted(Formatting.GRAY))
                                .addLoreLine(Text.literal("Command: /shop buy " + itemId + " <Amount>").formatted(Formatting.GRAY))
                                .setCallback((index, type, action) -> {
                                    Simpleshop.getInstance().buyItemFromShopDirect(player.getCommandSource(), itemId,
                                            1);
                                    openItemDetails(player, shopName, itemId);
                                })
                                .build());
                    } else {
                        gui.setSlot(11, new GuiElementBuilder(Items.GOLD_INGOT)
                                .setName(Text.literal("Sell Item").formatted(Formatting.YELLOW))
                                .addLoreLine(Text.literal("Click to sell").formatted(Formatting.GRAY))
                                .setCallback((index, type, action) -> {
                                    Simpleshop.getInstance().sellItemToShopDirect(player.getCommandSource(), itemId, 1);
                                    openItemDetails(player, shopName, itemId);
                                })
                                .build());
                    }

                    // Creator/Admin controls
                    if (isCreator || isAdmin) {
                        // Remove item button (only if quantity is 0)
                        if (quantity == 0) {
                            gui.setSlot(15, new GuiElementBuilder(Items.BARRIER)
                                    .setName(Text.literal("Remove Item").formatted(Formatting.RED))
                                    .addLoreLine(Text.literal("Click to remove").formatted(Formatting.GRAY))
                                    .setCallback((index, type, action) -> {
                                        database.removeItem(itemId);
                                        openShopItems(player, shopName, 0);
                                    })
                                    .build());
                        }

                        // Stock button for creator
                        if (isCreator) {
                            gui.setSlot(13, new GuiElementBuilder(Items.CHEST)
                                    .setName(Text.literal("Stock Item").formatted(Formatting.GOLD))
                                    .addLoreLine(Text.literal("Click to add stock").formatted(Formatting.GRAY))
                                    .addLoreLine(Text.literal("Command: /shop stock " + itemId + " <amount>").formatted(Formatting.GRAY))
                                    .setCallback((index, type, action) -> {
                                        player.sendMessage(Text.literal("Use: /shop stock " + itemId + " <amount>")
                                                .formatted(Formatting.GOLD));
                                    })
                                    .build());

                            // Take items button
                            if (quantity > 0) {
                                gui.setSlot(14, new GuiElementBuilder(Items.HOPPER)
                                        .setName(Text.literal("Take Items").formatted(Formatting.LIGHT_PURPLE))
                                        .addLoreLine(Text.literal("Click to take items").formatted(Formatting.GRAY))
                                        .setCallback((index, type, action) -> {
                                            player.sendMessage(Text.literal("Use: /shop take " + itemId + " <amount>")
                                                    .formatted(Formatting.LIGHT_PURPLE));
                                        })
                                        .build());
                            }
                        }

                        // Edit item
                        gui.setSlot(16, new GuiElementBuilder(Items.WRITABLE_BOOK)
                                .setName(Text.literal("Edit Item").formatted(Formatting.AQUA))
                                .addLoreLine(Text.literal("Click to edit settings").formatted(Formatting.GRAY))
                                .addLoreLine(Text.literal("Commands:").formatted(Formatting.GRAY))
                                .addLoreLine(Text.literal("/shop edit " + itemId + " selling").formatted(Formatting.GRAY))
                                .addLoreLine(Text.literal("/shop edit " + itemId + " buying").formatted(Formatting.GRAY))
                                .addLoreLine(Text.literal("/shop edit " + itemId + " price <value>").formatted(Formatting.GRAY))
                                .setCallback((index, type, action) -> {
                                    player.sendMessage(Text.literal("Edit commands:").formatted(Formatting.AQUA));
                                    player.sendMessage(Text.literal("/shop edit " + itemId + " selling")
                                            .formatted(Formatting.GRAY));
                                    player.sendMessage(Text.literal("/shop edit " + itemId + " buying")
                                            .formatted(Formatting.GRAY));
                                    player.sendMessage(Text.literal("/shop edit " + itemId + " price <value>")
                                            .formatted(Formatting.GRAY));
                                })
                                .build());
                    }

                    // Back button
                    gui.setSlot(22, new GuiElementBuilder(Items.ARROW)
                            .setName(Text.literal("Back").formatted(Formatting.RED))
                            .setCallback((index, type, action) -> openShopItems(player, shopName, 0))
                            .build());

                    gui.setTitle(Text.literal("Item Details - " + shopName));
                    gui.open();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            player.sendMessage(Text.literal("Error loading item details").formatted(Formatting.RED));
        }
    }

    private static class ShopEntry {
        String name;
        String description;
        boolean isAdminShop;
        String item;

        ShopEntry(String name, String description, boolean isAdminShop, String item) {
            this.name = name;
            this.description = description;
            this.isAdminShop = isAdminShop;
            this.item = item;
        }
    }

    private static class ShopItemEntry {
        int id;
        ItemStack itemStack;
        int quantity;
        boolean isSelling;
        BigDecimal price;

        ShopItemEntry(int id, ItemStack itemStack, int quantity, boolean isSelling, BigDecimal price) {
            this.id = id;
            this.itemStack = itemStack;
            this.quantity = quantity;
            this.isSelling = isSelling;
            this.price = price;
        }
    }
}