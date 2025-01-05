package com.iruanp.simpleshop;

import eu.pb4.sgui.api.gui.SimpleGui;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SignGui;
import eu.pb4.sgui.api.ClickType;
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
            element.addLoreLine(Text.literal("Shift-Click to edit description").formatted(Formatting.YELLOW));

            element.setCallback((index, type, action) -> {
                if (type.shift) {
                    openDescriptionEditor(player, shopName);
                } else {
                    openShopItems(player, shopName, 0);
                }
            });

            gui.setSlot(slot, element.build());
        }

        addNavigationButtons(gui, player, page, maxPages);
        gui.open();
    }

    private void openDescriptionEditor(ServerPlayerEntity player, String shopName) {
        SignGui signGui = new SignGui(player) {
            @Override
            public void onClose() {
                Text[] lines = new Text[4];
                for (int i = 0; i < 4; i++) {
                    lines[i] = this.getLine(i);
                }
                
                StringBuilder description = new StringBuilder();
                for (int i = 0; i < 4; i++) {
                    String line = lines[i].getString().trim();
                    if (!line.isEmpty()) {
                        if (description.length() > 0) description.append(" ");
                        description.append(line);
                    }
                }
                database.updateShopDescription(shopName, description.toString());
                openShopList(player, 0);
            }
        };
        signGui.setLine(0, Text.literal("Enter description"));
        signGui.setLine(1, Text.literal("on these lines"));
        signGui.open();
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
            element.addLoreLine(Text.literal("Left-Click to view details").formatted(Formatting.GRAY));
            element.addLoreLine(Text.literal("Right-Click to quick buy/sell").formatted(Formatting.GOLD));

            final int itemId = item.id;
            element.setCallback((index, type, action) -> {
                if (type.isLeft) {
                    openItemDetails(player, shopName, itemId);
                } else if (type.isRight) {
                    if (item.isSelling) {
                        openQuickBuyDialog(player, itemId, shopName);
                    } else {
                        openQuickSellDialog(player, itemId, shopName);
                    }
                }
            });

            gui.setSlot(slot, element.build());
        }

        // Add Create Item button if player has permission
        boolean isAdminShop = database.isAdminShop(shopName);
        if ((!isAdminShop && Permissions.check(player.getCommandSource(), "Simpleshop.Use", 0)) || 
            (isAdminShop && Permissions.check(player.getCommandSource(), "Simpleshop.Admin", 2))) {
            gui.setSlot(SLOTS_PER_PAGE + 0, new GuiElementBuilder(Items.NETHER_STAR)
                    .setName(Text.literal("Create New Item").formatted(Formatting.GREEN))
                    .addLoreLine(Text.literal("Click to add new item").formatted(Formatting.GRAY))
                    .setCallback((index, type, action) -> {
                        openCreateItemDialog(player, shopName);
                    })
                    .build());
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

    private void openQuickBuyDialog(ServerPlayerEntity player, int itemId, String shopName) {
        SignGui signGui = new SignGui(player) {
            @Override
            public void onClose() {
                try {
                    String amountStr = this.getLine(0).getString().trim();
                    int amount = Integer.parseInt(amountStr);
                    if (amount > 0) {
                        Simpleshop.getInstance().buyItemFromShopCore(player.getCommandSource(), itemId, amount);
                    }
                } catch (IllegalStateException e) {
                    player.sendMessage(Text.literal(e.getMessage()).formatted(Formatting.RED), false);
                } catch (NumberFormatException e) {
                    player.sendMessage(Text.literal("Invalid amount!").formatted(Formatting.RED), false);
                }
                openItemDetails(player, shopName, itemId);
            }
        };
        signGui.setLine(0, Text.literal(""));
        signGui.setLine(1, Text.literal("Enter amount to buy"));
        signGui.setLine(2, Text.literal("on the first line"));
        signGui.open();
    }

    private void openQuickSellDialog(ServerPlayerEntity player, int itemId, String shopName) {
        SignGui signGui = new SignGui(player) {
            @Override
            public void onClose() {
                try {
                    String amountStr = this.getLine(0).getString().trim();
                    int amount = Integer.parseInt(amountStr);
                    if (amount > 0) {
                        Simpleshop.getInstance().sellItemToShopCore(player.getCommandSource(), itemId, amount);
                    }
                } catch (IllegalStateException e) {
                    player.sendMessage(Text.literal(e.getMessage()).formatted(Formatting.RED), false);
                } catch (NumberFormatException e) {
                    player.sendMessage(Text.literal("Invalid amount!").formatted(Formatting.RED), false);
                }
                openItemDetails(player, shopName, itemId);
            }
        };
        signGui.setLine(0, Text.literal(""));
        signGui.setLine(1, Text.literal("Enter amount to sell"));
        signGui.setLine(2, Text.literal("on the first line"));
        signGui.open();
    }

    private void openCreateItemDialog(ServerPlayerEntity player, String shopName) {
        SimpleGui gui = new SimpleGui(ScreenHandlerType.GENERIC_9X3, player, false);
        gui.setTitle(Text.literal("Create New Item"));

        // Display current held item
        ItemStack heldItem = player.getMainHandStack();
        boolean hasItem = !heldItem.isEmpty();
        
        // Item slot
        gui.setSlot(4, new GuiElementBuilder(hasItem ? heldItem.getItem() : Items.BARRIER)
                .setName(Text.literal(hasItem ? "Current Item" : "No Item Selected").formatted(hasItem ? Formatting.GREEN : Formatting.RED))
                .addLoreLine(Text.literal(hasItem ? "Hold a different item to change" : "Hold an item in your main hand").formatted(Formatting.GRAY))
                .build());

        if (hasItem) {
            // Buy mode button (default)
            gui.setSlot(11, new GuiElementBuilder(Items.EMERALD)
                    .setName(Text.literal("Create Buy Offer").formatted(Formatting.GREEN))
                    .addLoreLine(Text.literal("Players can buy this item").formatted(Formatting.GRAY))
                    .addLoreLine(Text.literal("Click to set buy price").formatted(Formatting.YELLOW))
                    .setCallback((index, type, action) -> {
                        createNewShopItem(player, shopName, heldItem, true);
                    })
                    .build());

            // Sell mode button
            gui.setSlot(15, new GuiElementBuilder(Items.GOLD_INGOT)
                    .setName(Text.literal("Create Sell Offer").formatted(Formatting.YELLOW))
                    .addLoreLine(Text.literal("Players can sell this item").formatted(Formatting.GRAY))
                    .addLoreLine(Text.literal("Click to set sell price").formatted(Formatting.YELLOW))
                    .setCallback((index, type, action) -> {
                        createNewShopItem(player, shopName, heldItem, false);
                    })
                    .build());
        }

        // Back button
        gui.setSlot(22, new GuiElementBuilder(Items.BARRIER)
                .setName(Text.literal("Back").formatted(Formatting.RED))
                .setCallback((index, type, action) -> openShopItems(player, shopName, 0))
                .build());

        gui.open();
    }

    private void createNewShopItem(ServerPlayerEntity player, String shopName, ItemStack item, boolean isSelling) {
        SignGui signGui = new SignGui(player) {
            @Override
            public void onClose() {
                try {
                    String priceStr = this.getLine(0).getString().trim();
                    BigDecimal price = new BigDecimal(priceStr);
                    if (price.compareTo(BigDecimal.ZERO) > 0) {
                        JsonElement serializedItem = ItemStack.CODEC
                                .encode(item, JsonOps.INSTANCE, JsonOps.INSTANCE.empty())
                                .result()
                                .orElseThrow();
                        
                        // Ensure item count is 1
                        ItemStack normalizedItem = item.copy();
                        if (normalizedItem.getCount() > 1) {
                            normalizedItem.setCount(1);
                        }
                        serializedItem = ItemStack.CODEC
                                .encode(normalizedItem, JsonOps.INSTANCE, JsonOps.INSTANCE.empty())
                                .result()
                                .orElseThrow();
                        
                        int shopId = database.getShopIdByName(shopName);
                        database.addItem(shopId, serializedItem.toString(), 0, isSelling, price, player.getUuidAsString());
                        player.sendMessage(Text.literal("Item added to shop!").formatted(Formatting.GREEN), false);
                        openShopItems(player, shopName, 0);
                    } else {
                        player.sendMessage(Text.literal("Price must be greater than 0!").formatted(Formatting.RED), false);
                        openCreateItemDialog(player, shopName);
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage(Text.literal("Invalid price format!").formatted(Formatting.RED), false);
                    openCreateItemDialog(player, shopName);
                } catch (Exception e) {
                    player.sendMessage(Text.literal("Failed to create item: " + e.getMessage()).formatted(Formatting.RED), false);
                    openCreateItemDialog(player, shopName);
                }
            }
        };
        signGui.setLine(0, Text.literal(""));
        signGui.setLine(1, Text.literal("Enter price"));
        signGui.setLine(2, Text.literal("Example: 10.5"));
        signGui.open();
    }

    private void openPriceDialog(ServerPlayerEntity player, int itemId, String shopName) {
        SignGui signGui = new SignGui(player) {
            @Override
            public void onClose() {
                try {
                    String priceStr = this.getLine(0).getString().trim();
                    BigDecimal price = new BigDecimal(priceStr);
                    if (price.compareTo(BigDecimal.ZERO) > 0) {
                        if (itemId != -1) {
                            // Update existing item price
                            database.updateItemPrice(itemId, price);
                            openItemDetails(player, shopName, itemId);
                        } else {
                            // Handle new item price setting
                            // Implementation needed
                        }
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage(Text.literal("Invalid price!").formatted(Formatting.RED), false);
                    openShopItems(player, shopName, 0);
                }
            }
        };
        signGui.setLine(0, Text.literal(""));
        signGui.setLine(1, Text.literal("Enter price"));
        signGui.setLine(2, Text.literal("Example: 10.5"));
        signGui.open();
    }

    private void openConfirmationDialog(ServerPlayerEntity player, String title, String message, Runnable onConfirm, Runnable onCancel) {
        SimpleGui gui = new SimpleGui(ScreenHandlerType.GENERIC_9X3, player, false);
        gui.setTitle(Text.literal(title));

        // Message
        gui.setSlot(4, new GuiElementBuilder(Items.PAPER)
                .setName(Text.literal(message).formatted(Formatting.YELLOW))
                .build());

        // Confirm button
        gui.setSlot(11, new GuiElementBuilder(Items.LIME_CONCRETE)
                .setName(Text.literal("Confirm").formatted(Formatting.GREEN))
                .setCallback((index, type, action) -> {
                    onConfirm.run();
                })
                .build());

        // Cancel button
        gui.setSlot(15, new GuiElementBuilder(Items.RED_CONCRETE)
                .setName(Text.literal("Cancel").formatted(Formatting.RED))
                .setCallback((index, type, action) -> {
                    onCancel.run();
                })
                .build());

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
                    boolean isAdmin = Permissions.check(player.getCommandSource(), "Simpleshop.Admin", 2);

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

                    // Quick action buttons
                    if (isSelling) {
                        gui.setSlot(11, new GuiElementBuilder(Items.EMERALD)
                                .setName(Text.literal("Buy Items").formatted(Formatting.GREEN))
                                .addLoreLine(Text.literal("Click to specify amount").formatted(Formatting.GRAY))
                                .setCallback((index, type, action) -> {
                                    openQuickBuyDialog(player, itemId, shopName);
                                })
                                .build());
                    } else {
                        gui.setSlot(11, new GuiElementBuilder(Items.GOLD_INGOT)
                                .setName(Text.literal("Quick Sell").formatted(Formatting.YELLOW))
                                .addLoreLine(Text.literal("Left-Click: Sell 1").formatted(Formatting.GRAY))
                                .addLoreLine(Text.literal("Right-Click: Custom amount").formatted(Formatting.GRAY))
                                .setCallback((index, type, action) -> {
                                    if (type.isRight) {
                                        openQuickSellDialog(player, itemId, shopName);
                                    } else {
                                        try {
                                            Simpleshop.getInstance().sellItemToShopCore(player.getCommandSource(), itemId, 1);
                                            openItemDetails(player, shopName, itemId);
                                        } catch (IllegalStateException e) {
                                            player.sendMessage(Text.literal(e.getMessage()).formatted(Formatting.RED), false);
                                        }
                                    }
                                })
                                .build());
                    }

                    // Creator/Admin controls
                    if (isCreator || isAdmin) {
                        // Edit price button
                        gui.setSlot(13, new GuiElementBuilder(Items.GOLD_NUGGET)
                                .setName(Text.literal("Edit Price").formatted(Formatting.GOLD))
                                .addLoreLine(Text.literal("Click to change price").formatted(Formatting.GRAY))
                                .setCallback((index, type, action) -> {
                                    openPriceDialog(player, itemId, shopName);
                                })
                                .build());

                        // Toggle buy/sell mode
                        gui.setSlot(14, new GuiElementBuilder(isSelling ? Items.HOPPER : Items.CHEST)
                                .setName(Text.literal("Toggle Mode").formatted(Formatting.AQUA))
                                .addLoreLine(Text.literal("Current: " + (isSelling ? "Selling" : "Buying")).formatted(Formatting.GRAY))
                                .setCallback((index, type, action) -> {
                                    openConfirmationDialog(player,
                                            "Toggle Mode",
                                            "Change to " + (isSelling ? "Buying" : "Selling") + " mode?",
                                            () -> {
                                                database.toggleItemMode(itemId);
                                                openItemDetails(player, shopName, itemId);
                                            },
                                            () -> openItemDetails(player, shopName, itemId));
                                })
                                .build());

                        // Remove item button (only if quantity is 0)
                        if (quantity == 0) {
                            gui.setSlot(15, new GuiElementBuilder(Items.BARRIER)
                                    .setName(Text.literal("Remove Item").formatted(Formatting.RED))
                                    .addLoreLine(Text.literal("Click to remove").formatted(Formatting.GRAY))
                                    .setCallback((index, type, action) -> {
                                        openConfirmationDialog(player,
                                                "Remove Item",
                                                "Are you sure you want to remove this item?",
                                                () -> {
                                        database.removeItem(itemId);
                                        openShopItems(player, shopName, 0);
                                                },
                                                () -> openItemDetails(player, shopName, itemId));
                                    })
                                    .build());
                        }

                        // Stock button for creator
                        if (isCreator) {
                            gui.setSlot(12, new GuiElementBuilder(Items.CHEST)
                                    .setName(Text.literal("Manage Stock").formatted(Formatting.GOLD))
                                    .addLoreLine(Text.literal("Left-Click: Add stock").formatted(Formatting.GRAY))
                                    .addLoreLine(Text.literal("Right-Click: Take items").formatted(Formatting.GRAY))
                                    .setCallback((index, type, action) -> {
                                        if (type.isLeft) {
                                            openQuickStockDialog(player, itemId, shopName);
                                        } else if (type.isRight && quantity > 0) {
                                            openQuickTakeDialog(player, itemId, shopName);
                                        }
                                    })
                                    .build());
                            }
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
            player.sendMessage(Text.literal("Error loading item details").formatted(Formatting.RED), false);
        }
    }

    private void openQuickStockDialog(ServerPlayerEntity player, int itemId, String shopName) {
        SignGui signGui = new SignGui(player) {
            @Override
            public void onClose() {
                try {
                    String amountStr = this.getLine(0).getString().trim();
                    int amount = Integer.parseInt(amountStr);
                    if (amount > 0) {
                        try {
                            Simpleshop.getInstance().stockItemInShopCore(player.getCommandSource(), itemId, amount);
                        } catch (IllegalStateException e) {
                            player.sendMessage(Text.literal(e.getMessage()).formatted(Formatting.RED), false);
                        }
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage(Text.literal("Invalid amount!").formatted(Formatting.RED), false);
                }
                openItemDetails(player, shopName, itemId);
            }
        };
        signGui.setLine(0, Text.literal(""));
        signGui.setLine(1, Text.literal("Enter amount to add"));
        signGui.setLine(2, Text.literal("from your inventory"));
        signGui.open();
    }

    private void openQuickTakeDialog(ServerPlayerEntity player, int itemId, String shopName) {
        SignGui signGui = new SignGui(player) {
            @Override
            public void onClose() {
                try {
                    String amountStr = this.getLine(0).getString().trim();
                    int amount = Integer.parseInt(amountStr);
                    if (amount > 0) {
                        int currentStock = database.getItemQuantity(itemId);
                        if (amount <= currentStock) {
                            database.removeStockFromItem(itemId, amount);
                            player.sendMessage(Text.literal("Took " + amount + " items from stock").formatted(Formatting.GREEN), false);
                        } else {
                            player.sendMessage(Text.literal("Not enough items in stock!").formatted(Formatting.RED), false);
                        }
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage(Text.literal("Invalid amount!").formatted(Formatting.RED), false);
                }
                openItemDetails(player, shopName, itemId);
            }
        };
        signGui.setLine(0, Text.literal(""));
        signGui.setLine(1, Text.literal("Enter amount to take"));
        signGui.setLine(2, Text.literal("from stock"));
        signGui.open();
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