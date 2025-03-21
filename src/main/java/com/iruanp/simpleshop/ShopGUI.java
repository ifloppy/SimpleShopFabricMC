package com.iruanp.simpleshop;

import eu.pb4.sgui.api.gui.SimpleGui;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SignGui;
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
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.iruanp.simpleshop.service.InventoryService;
import com.iruanp.simpleshop.service.ShopEntry;
import com.iruanp.simpleshop.service.ShopItemEntry;
import com.iruanp.simpleshop.service.ShopService;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.JsonOps;
import eu.pb4.common.economy.api.CommonEconomy;
import eu.pb4.common.economy.api.EconomyAccount;

public class ShopGUI {
    private static final int ROWS = 6;
    private static final int SLOTS_PER_PAGE = (ROWS - 1) * 9;
    private ShopDatabase database;
    private ShopService shopService;
    private InventoryService inventoryService;

    public ShopGUI(ShopDatabase database, ShopService shopService) {
        this.database = database;
        this.shopService = shopService;
        this.inventoryService = new InventoryService();
    }

    public void openShopList(ServerPlayerEntity player, int page) {
        List<ShopEntry> shops = shopService.getShops();
        int maxPages = (shops.size() + SLOTS_PER_PAGE - 1) / SLOTS_PER_PAGE;
        if (maxPages == 0)
            maxPages = 1;
        if (page < 0)
            page = 0;
        if (page >= maxPages)
            page = maxPages - 1;

        SimpleGui gui = new SimpleGui(ScreenHandlerType.GENERIC_9X6, player, false);
        gui.setTitle(Text.literal(Config.getShopTitle() + " - Page " + (page + 1) + "/" + maxPages));

        int startIndex = page * SLOTS_PER_PAGE;
        int endIndex = Math.min(startIndex + SLOTS_PER_PAGE, shops.size());

        for (int i = startIndex; i < endIndex; i++) {
            ShopEntry shop = shops.get(i);
            int slot = i - startIndex;
            JsonElement shopItem = JsonParser.parseString(shop.item);
            ItemStack shopItemStack = ItemStack.CODEC.decode(JsonOps.INSTANCE, shopItem)
                    .resultOrPartial(Simpleshop.LOGGER::error)
                    .map(Pair::getFirst)
                    .orElse(null);

            GuiElementBuilder element = new GuiElementBuilder()
                    .setItem(shopItemStack.getItem())
                    .setName(Text.literal(shop.name))
                    .addLoreLine(Text.literal(I18n.translate("gui.shop.type", shop.isAdminShop ? 
                            I18n.translate("gui.shop.type.admin").getString() : 
                            I18n.translate("gui.shop.type.player").getString()).getString())
                            .formatted(Formatting.GRAY));

            if (!shop.description.isEmpty()) {
                element.addLoreLine(Text.empty());
                element.addLoreLine(I18n.translate("gui.shop.description").formatted(Formatting.YELLOW));
                element.addLoreLine(Text.literal(shop.description).formatted(Formatting.GRAY));
            }

            element.addLoreLine(Text.empty());
            element.addLoreLine(I18n.translate("gui.shop.click_view").formatted(Formatting.GREEN));
            element.addLoreLine(I18n.translate("gui.shop.shift_edit").formatted(Formatting.YELLOW));

            if (Permissions.check(player.getCommandSource(), "Simpleshop.Admin", 2)) {
                element.addLoreLine(Text.empty());
                element.addLoreLine(I18n.translate("gui.shop.right_edit").formatted(Formatting.GOLD));
            }

            element.setCallback((index, type, action) -> {
                if (type.shift) {
                    if (Permissions.check(player.getCommandSource(), "Simpleshop.Admin", 2)) {
                        openDescriptionEditor(player, shop.name);
                    } else {
                        player.sendMessage(I18n.translate("item.no_permission", "edit").formatted(Formatting.RED), false);
                    }
                } else if (type.isRight && Permissions.check(player.getCommandSource(), "Simpleshop.Admin", 2)) {
                    openShopSettings(player, shop.name);
                } else {
                    openShopItems(player, shop.name, 0);
                }
            });

            gui.setSlot(slot, element.build());
        }

        // Add create shop buttons for admins
        if (Permissions.check(player.getCommandSource(), "Simpleshop.Admin", 2)) {
            // Create normal shop button
            gui.setSlot(SLOTS_PER_PAGE + 3, new GuiElementBuilder(Items.CHEST)
                    .setName(I18n.translate("gui.shop.create_normal").formatted(Formatting.GREEN))
                    .addLoreLine(I18n.translate("gui.shop.create_normal.desc").formatted(Formatting.GRAY))
                    .addLoreLine(I18n.translate("gui.shop.hold_item").formatted(Formatting.YELLOW))
                    .setCallback((index, type, action) -> {
                        openCreateShopDialog(player, false);
                    })
                    .build());

            // Create admin shop button
            gui.setSlot(SLOTS_PER_PAGE + 5, new GuiElementBuilder(Items.ENDER_CHEST)
                    .setName(I18n.translate("gui.shop.create_admin").formatted(Formatting.GOLD))
                    .addLoreLine(I18n.translate("gui.shop.create_admin.desc").formatted(Formatting.GRAY))
                    .addLoreLine(I18n.translate("gui.shop.hold_item").formatted(Formatting.YELLOW))
                    .setCallback((index, type, action) -> {
                        openCreateShopDialog(player, true);
                    })
                    .build());
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
                shopService.updateShopDescription(shopName, description.toString());
                openShopList(player, 0);
            }
        };
        signGui.setLine(0, I18n.translate("dialog.enter_shop_name"));
        signGui.setLine(1, I18n.translate("dialog.enter_shop_name.first"));
        signGui.open();
    }

    private void openShopItems(ServerPlayerEntity player, String shopName, int page) {
        List<ShopItemEntry> items = shopService.getShopItems(shopName);
        int maxPages = (items.size() + SLOTS_PER_PAGE - 1) / SLOTS_PER_PAGE;
        if (maxPages == 0)
            maxPages = 1;
        if (page < 0)
            page = 0;
        if (page >= maxPages)
            page = maxPages - 1;

        SimpleGui gui = new SimpleGui(ScreenHandlerType.GENERIC_9X6, player, false);
        gui.setTitle(I18n.translate("gui.item.details", shopName + " - Page " + (page + 1) + "/" + maxPages));

        int startIndex = page * SLOTS_PER_PAGE;
        int endIndex = Math.min(startIndex + SLOTS_PER_PAGE, items.size());

        for (int i = startIndex; i < endIndex; i++) {
            ShopItemEntry item = items.get(i);
            int slot = i - startIndex;

            GuiElementBuilder element = new GuiElementBuilder(item.itemStack.copy());
            element.addLoreLine(Text.empty());

            if (item.isSelling) {
                element.addLoreLine(I18n.translate("item.price.buy", Simpleshop.getInstance().formatPrice(item.price))
                        .formatted(Formatting.GREEN));
                element.addLoreLine(I18n.translate("item.stock.quantity", item.quantity)
                        .formatted(Formatting.AQUA));
            } else {
                element.addLoreLine(I18n.translate("item.price.sell", Simpleshop.getInstance().formatPrice(item.price))
                        .formatted(Formatting.YELLOW));
            }

            // Add creator info if not an admin shop
            if (!shopService.isAdminShop(shopName) && item.creator != null) {
                String creatorName = PlayerUtils.getPlayerName(UUID.fromString(item.creator));
                if (creatorName != null) {
                    element.addLoreLine(Text.empty());
                    element.addLoreLine(I18n.translate("gui.item.creator", creatorName).formatted(Formatting.GRAY));
                }
            }

            element.addLoreLine(Text.empty());
            element.addLoreLine(I18n.translate("gui.item.click_details").formatted(Formatting.GRAY));
            element.addLoreLine(I18n.translate("gui.item.click_quick_action").formatted(Formatting.GOLD));

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
                    .setName(I18n.translate("gui.item.create_new").formatted(Formatting.GREEN))
                    .addLoreLine(I18n.translate("gui.item.create_new.desc").formatted(Formatting.GRAY))
                    .setCallback((index, type, action) -> {
                        openCreateItemDialog(player, shopName);
                    })
                    .build());
        }

        // Back button
        gui.setSlot(SLOTS_PER_PAGE + 4, new GuiElementBuilder()
                .setItem(Items.BARRIER)
                .setName(I18n.translate("gui.shop.back").formatted(Formatting.RED))
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
                        // Get item creator before transaction
                        String creatorName = PlayerUtils.getPlayerName(UUID.fromString(database.getItemCreator(itemId)));
                        
                        // Perform transaction
                        Simpleshop.getInstance().buyItemFromShopCore(player.getCommandSource(), itemId, amount);
                        
                        // Send notification to item owner
                        if (creatorName != null && !database.isAdminShop(shopName)) {
                            String notificationMsg = I18n.translate("notification.item.bought", 
                                player.getName().getString(), amount, shopName).getString();
                            Simpleshop.getInstance().getNotificationManager().notifyPlayer(creatorName, notificationMsg);
                        }
                    }
                } catch (IllegalStateException e) {
                    player.sendMessage(Text.literal(e.getMessage()).formatted(Formatting.RED), false);
                } catch (NumberFormatException e) {
                    player.sendMessage(I18n.translate("error.invalid_amount").formatted(Formatting.RED), false);
                }
                openItemDetails(player, shopName, itemId);
            }
        };
        signGui.setLine(0, Text.literal(""));
        signGui.setLine(1, I18n.translate("dialog.enter_amount.buy"));

        // Calculate max amount player can buy based on stock, inventory space, and money
        BigDecimal price = database.getItemPrice(itemId);
        Collection<EconomyAccount> accounts = CommonEconomy.getAccounts(player, Simpleshop.getInstance().defaultCurrency);
        EconomyAccount account = accounts.isEmpty() ? null : accounts.iterator().next();
        BigDecimal balance = account != null ? BigDecimal.valueOf(account.balance()).divide(BigDecimal.valueOf(1000), RoundingMode.FLOOR) : BigDecimal.ZERO;
        int maxAffordable = price.compareTo(BigDecimal.ZERO) > 0 ? balance.divide(price, RoundingMode.FLOOR).intValue() : Integer.MAX_VALUE;
        
        // Get max purchaseable amount considering inventory space and stock
        int maxPurchaseable = Simpleshop.getInstance().getMaxPurchaseableAmount(player, itemId, maxAffordable);
        
        signGui.setLine(2, I18n.translate("dialog.enter_amount.max", maxPurchaseable));
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
                        // Get item creator before transaction
                        String creatorName = PlayerUtils.getPlayerName(UUID.fromString(database.getItemCreator(itemId)));
                        
                        // Perform transaction
                        Simpleshop.getInstance().sellItemToShopCore(player.getCommandSource(), itemId, amount);
                        
                        // Send notification to item owner
                        if (creatorName != null && !database.isAdminShop(shopName)) {
                            String notificationMsg = I18n.translate("notification.item.sold", 
                                player.getName().getString(), amount, shopName).getString();
                            Simpleshop.getInstance().getNotificationManager().notifyPlayer(creatorName, notificationMsg);
                        }
                    }
                } catch (IllegalStateException e) {
                    player.sendMessage(Text.literal(e.getMessage()).formatted(Formatting.RED), false);
                } catch (NumberFormatException e) {
                    player.sendMessage(I18n.translate("error.invalid_amount").formatted(Formatting.RED), false);
                }
                openItemDetails(player, shopName, itemId);
            }
        };
        signGui.setLine(0, Text.literal(""));
        signGui.setLine(1, I18n.translate("dialog.enter_amount.sell"));

        // Calculate max amount player can sell based on their inventory
        ItemStack itemStack = database.getItemStack(itemId);
        int maxAmount = 0;
        for (ItemStack stack : player.getInventory().main) {
            if (!stack.isEmpty() && stack.isOf(itemStack.getItem())) {
                maxAmount += stack.getCount();
            }
        }

        // Check shop stock limit
        if (!database.isAdminShop(shopName)) {
            int currentStock = database.getItemQuantity(itemId);
            int remainingSpace = 1024 - currentStock;
            maxAmount = Math.min(maxAmount, remainingSpace);
        }

        signGui.setLine(2, I18n.translate("dialog.enter_amount.max", maxAmount));
        signGui.open();
    }

    private void openCreateItemDialog(ServerPlayerEntity player, String shopName) {
        SimpleGui gui = new SimpleGui(ScreenHandlerType.GENERIC_9X3, player, false);
        gui.setTitle(I18n.translate("gui.item.create_new"));

        // Display current held item
        ItemStack heldItem = player.getMainHandStack();
        boolean hasItem = !heldItem.isEmpty();
        
        // Item slot
        gui.setSlot(4, new GuiElementBuilder(hasItem ? heldItem.getItem() : Items.BARRIER)
                .setName(I18n.translate(hasItem ? "gui.item.current" : "gui.item.no_item")
                        .formatted(hasItem ? Formatting.GREEN : Formatting.RED))
                .addLoreLine(I18n.translate(hasItem ? "gui.item.hold_change" : "gui.shop.hold_item")
                        .formatted(Formatting.GRAY))
                .build());

        if (hasItem) {
            // Buy mode button (default)
            gui.setSlot(11, new GuiElementBuilder(Items.EMERALD)
                    .setName(I18n.translate("gui.item.create_buy").formatted(Formatting.GREEN))
                    .addLoreLine(I18n.translate("gui.item.create_buy.desc").formatted(Formatting.GRAY))
                    .addLoreLine(I18n.translate("gui.item.set_price").formatted(Formatting.YELLOW))
                    .setCallback((index, type, action) -> {
                        createNewShopItem(player, shopName, heldItem, true);
                    })
                    .build());

            // Sell mode button
            gui.setSlot(15, new GuiElementBuilder(Items.GOLD_INGOT)
                    .setName(I18n.translate("gui.item.create_sell").formatted(Formatting.YELLOW))
                    .addLoreLine(I18n.translate("gui.item.create_sell.desc").formatted(Formatting.GRAY))
                    .addLoreLine(I18n.translate("gui.item.set_price").formatted(Formatting.YELLOW))
                    .setCallback((index, type, action) -> {
                        createNewShopItem(player, shopName, heldItem, false);
                    })
                    .build());
        }

        // Back button
        gui.setSlot(22, new GuiElementBuilder(Items.BARRIER)
                .setName(I18n.translate("gui.shop.back").formatted(Formatting.RED))
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
                        // Ensure item count is 1
                        ItemStack normalizedItem = item.copy();
                        if (normalizedItem.getCount() > 1) {
                            normalizedItem.setCount(1);
                        }
                        String serializedItem = inventoryService.serializeHeldItem(normalizedItem);
                        
                        int shopId = database.getShopIdByName(shopName);
                        database.addItem(shopId, serializedItem.toString(), 0, isSelling, price, player.getUuidAsString());
                        player.sendMessage(I18n.translate("item.create.success").formatted(Formatting.GREEN), false);
                        openShopItems(player, shopName, 0);
                    } else {
                        player.sendMessage(I18n.translate("error.price.zero").formatted(Formatting.RED), false);
                        openCreateItemDialog(player, shopName);
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage(I18n.translate("error.price.invalid").formatted(Formatting.RED), false);
                    openCreateItemDialog(player, shopName);
                } catch (Exception e) {
                    player.sendMessage(I18n.translate("error.item.create", e.getMessage()).formatted(Formatting.RED), false);
                    openCreateItemDialog(player, shopName);
                }
            }
        };
        signGui.setLine(0, Text.literal(""));
        signGui.setLine(1, I18n.translate("dialog.enter_price"));
        signGui.setLine(2, I18n.translate("dialog.enter_price.example"));
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
                    player.sendMessage(I18n.translate("error.price.invalid").formatted(Formatting.RED), false);
                    openShopItems(player, shopName, 0);
                }
            }
        };
        signGui.setLine(0, Text.literal(""));
        signGui.setLine(1, I18n.translate("dialog.enter_price"));
        signGui.setLine(2, I18n.translate("dialog.enter_price.example"));
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
                .setName(I18n.translate("dialog.confirm").formatted(Formatting.GREEN))
                .setCallback((index, type, action) -> {
                    onConfirm.run();
                })
                .build());

        // Cancel button
        gui.setSlot(15, new GuiElementBuilder(Items.RED_CONCRETE)
                .setName(I18n.translate("dialog.cancel").formatted(Formatting.RED))
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
                    .setName(I18n.translate("gui.shop.prev_page").formatted(Formatting.YELLOW))
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
                    .setName(I18n.translate("gui.shop.next_page").formatted(Formatting.YELLOW))
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



    public void openItemDetails(ServerPlayerEntity player, String shopName, int itemId) {
        // Change to 6-row GUI for more space
        SimpleGui gui = new SimpleGui(ScreenHandlerType.GENERIC_9X6, player, false);

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

                    // Display item in the center top
                    GuiElementBuilder itemElement = new GuiElementBuilder(itemStack.copy())
                            .addLoreLine(Text.empty())
                            .addLoreLine(I18n.translate("gui.item.quantity", quantity).formatted(Formatting.AQUA))
                            .addLoreLine(I18n.translate(isSelling ? "item.price.buy" : "item.price.sell", 
                                    Simpleshop.getInstance().formatPrice(price))
                                    .formatted(isSelling ? Formatting.GREEN : Formatting.YELLOW))
                            .addLoreLine(Text.empty())
                            .addLoreLine(I18n.translate("gui.item.creator", creator).formatted(Formatting.GRAY));

                    gui.setSlot(4, itemElement.build());

                    // Client actions (Row 2: slots 18-26)
                    if (isSelling) {
                        // Buy button
                        gui.setSlot(22, new GuiElementBuilder(Items.EMERALD)
                                .setName(I18n.translate("gui.item.buy").formatted(Formatting.GREEN))
                                .addLoreLine(I18n.translate("gui.item.buy.desc").formatted(Formatting.GRAY))
                                .setCallback((index, type, action) -> {
                                    openQuickBuyDialog(player, itemId, shopName);
                                })
                                .build());
                    } else {
                        // Sell button
                        gui.setSlot(22, new GuiElementBuilder(Items.GOLD_INGOT)
                                .setName(I18n.translate("gui.item.sell").formatted(Formatting.YELLOW))
                                .addLoreLine(I18n.translate("gui.item.sell.desc").formatted(Formatting.GRAY))
                                .setCallback((index, type, action) -> {
                                    openQuickSellDialog(player, itemId, shopName);
                                })
                                .build());
                    }

                    // Creator/Admin controls (Row 4: slots 36-44)
                    if (isCreator || isAdmin) {
                        // Edit price button
                        gui.setSlot(38, new GuiElementBuilder(Items.GOLD_NUGGET)
                                .setName(I18n.translate("gui.item.edit_price").formatted(Formatting.GOLD))
                                .addLoreLine(I18n.translate("gui.item.edit_price.desc").formatted(Formatting.GRAY))
                                .setCallback((index, type, action) -> {
                                    openPriceDialog(player, itemId, shopName);
                                })
                                .build());

                        // Move item button (only for non-admin shops)
                        if (!rs.getBoolean("isAdminShop")) {
                            gui.setSlot(40, new GuiElementBuilder(Items.ENDER_PEARL)
                                    .setName(I18n.translate("gui.item.move").formatted(Formatting.LIGHT_PURPLE))
                                    .addLoreLine(I18n.translate("gui.item.move.desc").formatted(Formatting.GRAY))
                                    .setCallback((index, type, action) -> {
                                        openMoveItemDialog(player, itemId, shopName);
                                    })
                                    .build());
                        }

                        // Toggle buy/sell mode
                        gui.setSlot(42, new GuiElementBuilder(isSelling ? Items.HOPPER : Items.CHEST)
                                .setName(I18n.translate("gui.item.toggle_mode").formatted(Formatting.AQUA))
                                .addLoreLine(I18n.translate("gui.item.toggle_mode.current", 
                                        I18n.translate(isSelling ? "gui.item.toggle_mode.selling" : "gui.item.toggle_mode.buying").getString())
                                        .formatted(Formatting.GRAY))
                                .setCallback((index, type, action) -> {
                                    openConfirmationDialog(player,
                                            I18n.translate("dialog.toggle_mode.title").getString(),
                                            I18n.translate("dialog.toggle_mode.message", 
                                                    I18n.translate(isSelling ? "gui.item.toggle_mode.buying" : "gui.item.toggle_mode.selling").getString()).getString(),
                                            () -> {
                                                database.toggleItemMode(itemId);
                                                openItemDetails(player, shopName, itemId);
                                            },
                                            () -> openItemDetails(player, shopName, itemId));
                                })
                                .build());

                        // Remove item button (only if quantity is 0)
                        if (quantity == 0) {
                            gui.setSlot(44, new GuiElementBuilder(Items.BARRIER)
                                    .setName(I18n.translate("gui.item.remove").formatted(Formatting.RED))
                                    .addLoreLine(I18n.translate("gui.item.remove.desc").formatted(Formatting.GRAY))
                                    .setCallback((index, type, action) -> {
                                        openConfirmationDialog(player,
                                                I18n.translate("dialog.remove_item.title").getString(),
                                                I18n.translate("dialog.remove_item.message").getString(),
                                                () -> {
                                                    database.removeItem(itemId);
                                                    openShopItems(player, shopName, 0);
                                                },
                                                () -> openItemDetails(player, shopName, itemId));
                                    })
                                    .build());
                        }

                        // Stock management buttons for creator (Row 3: slots 27-35)
                        if (isCreator) {
                            // Add stock button
                            gui.setSlot(30, new GuiElementBuilder(Items.HOPPER)
                                    .setName(I18n.translate("gui.item.add_stock").formatted(Formatting.GREEN))
                                    .addLoreLine(I18n.translate("gui.item.add_stock.desc").formatted(Formatting.GRAY))
                                    .setCallback((index, type, action) -> {
                                        openQuickStockDialog(player, itemId, shopName);
                                    })
                                    .build());

                            // Withdraw stock button
                            gui.setSlot(32, new GuiElementBuilder(Items.CHEST_MINECART)
                                    .setName(I18n.translate("gui.item.withdraw").formatted(Formatting.GOLD))
                                    .addLoreLine(I18n.translate("gui.item.withdraw.desc").formatted(Formatting.GRAY))
                                    .addLoreLine(I18n.translate("item.stock.quantity", quantity).formatted(Formatting.AQUA))
                                    .setCallback((index, type, action) -> {
                                        if (quantity > 0) {
                                            openQuickTakeDialog(player, itemId, shopName);
                                        } else {
                                            player.sendMessage(I18n.translate("gui.item.no_stock").formatted(Formatting.RED), false);
                                        }
                                    })
                                    .build());
                        }
                    }

                    // Back button (Bottom row center)
                    gui.setSlot(49, new GuiElementBuilder(Items.BARRIER)
                            .setName(I18n.translate("gui.shop.back").formatted(Formatting.RED))
                            .setCallback((index, type, action) -> openShopItems(player, shopName, 0))
                            .build());

                    gui.setTitle(I18n.translate("gui.item.details", "Item Details - " + shopName));
                    gui.open();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            player.sendMessage(I18n.translate("error.update_failed").formatted(Formatting.RED), false);
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
                    player.sendMessage(I18n.translate("error.invalid_amount").formatted(Formatting.RED), false);
                }
                openItemDetails(player, shopName, itemId);
            }
        };
        signGui.setLine(0, Text.literal(""));
        signGui.setLine(1, I18n.translate("dialog.enter_amount.add"));
        signGui.setLine(2, I18n.translate("dialog.enter_amount.from_inv"));
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
                            ItemStack itemStack = database.getItemStack(itemId);
                            if (itemStack != null) {
                                itemStack.setCount(amount);
                                if (player.getInventory().insertStack(itemStack)) {
                                    database.removeStockFromItem(itemId, amount);
                                    player.sendMessage(I18n.translate("item.take.success", amount).formatted(Formatting.GREEN), false);
                                } else {
                                    player.sendMessage(I18n.translate("error.insufficient_space").formatted(Formatting.RED), false);
                                }
                            }
                        } else {
                            player.sendMessage(I18n.translate("error.insufficient_stock", currentStock).formatted(Formatting.RED), false);
                        }
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage(I18n.translate("error.invalid_amount").formatted(Formatting.RED), false);
                }
                openItemDetails(player, shopName, itemId);
            }
        };
        signGui.setLine(0, Text.literal(""));
        signGui.setLine(1, I18n.translate("dialog.enter_amount.withdraw"));
        signGui.setLine(2, I18n.translate("dialog.enter_amount.from_inv"));
        signGui.open();
    }

    private void openShopSettings(ServerPlayerEntity player, String shopName) {
        SimpleGui gui = new SimpleGui(ScreenHandlerType.GENERIC_9X3, player, false);
        gui.setTitle(I18n.translate("gui.shop.settings", shopName));

        // Edit Name button
        gui.setSlot(11, new GuiElementBuilder(Items.NAME_TAG)
                .setName(I18n.translate("gui.shop.edit_name").formatted(Formatting.YELLOW))
                .addLoreLine(I18n.translate("gui.shop.edit_name.desc").formatted(Formatting.GRAY))
                .setCallback((index, type, action) -> {
                    openShopNameEditor(player, shopName);
                })
                .build());

        // Edit Description button
        gui.setSlot(13, new GuiElementBuilder(Items.WRITABLE_BOOK)
                .setName(I18n.translate("gui.shop.edit_desc").formatted(Formatting.GREEN))
                .addLoreLine(I18n.translate("gui.shop.edit_desc.desc").formatted(Formatting.GRAY))
                .setCallback((index, type, action) -> {
                    openDescriptionEditor(player, shopName);
                })
                .build());

        // Edit Icon button
        gui.setSlot(15, new GuiElementBuilder(Items.ITEM_FRAME)
                .setName(I18n.translate("gui.shop.edit_icon").formatted(Formatting.AQUA))
                .addLoreLine(I18n.translate("gui.shop.edit_icon.desc").formatted(Formatting.GRAY))
                .setCallback((index, type, action) -> {
                    ItemStack heldItem = player.getMainHandStack();
                    if (!heldItem.isEmpty()) {
                        JsonElement serializedItem = ItemStack.CODEC
                                .encode(heldItem, JsonOps.INSTANCE, JsonOps.INSTANCE.empty())
                                .result()
                                .orElseThrow();
                        shopService.updateShopIcon(shopName, serializedItem.toString());
                        player.sendMessage(I18n.translate("gui.shop.icon_updated").formatted(Formatting.GREEN), false);
                        openShopList(player, 0);
                    } else {
                        player.sendMessage(I18n.translate("gui.shop.need_icon").formatted(Formatting.RED), false);
                    }
                })
                .build());

        // Add delete button for admins if shop is empty
        if (Permissions.check(player.getCommandSource(), "Simpleshop.Admin", 2) && shopService.isShopEmpty(shopName)) {
            gui.setSlot(16, new GuiElementBuilder(Items.BARRIER)
                    .setName(I18n.translate("gui.shop.delete").formatted(Formatting.RED))
                    .addLoreLine(I18n.translate("gui.shop.delete.desc").formatted(Formatting.GRAY))
                    .setCallback((index, type, action) -> {
                        openConfirmationDialog(player,
                                I18n.translate("dialog.delete_shop.title").getString(),
                                I18n.translate("dialog.delete_shop.message").getString(),
                                () -> {
                                    shopService.deleteShop(shopName);
                                    player.sendMessage(I18n.translate("shop.delete.success").formatted(Formatting.GREEN), false);
                                    openShopList(player, 0);
                                },
                                () -> openShopSettings(player, shopName));
                    })
                    .build());
        }

        // Back button
        gui.setSlot(22, new GuiElementBuilder(Items.BARRIER)
                .setName(I18n.translate("gui.shop.back").formatted(Formatting.RED))
                .setCallback((index, type, action) -> openShopList(player, 0))
                .build());

        gui.open();
    }

    private void openShopNameEditor(ServerPlayerEntity player, String oldShopName) {
        SignGui signGui = new SignGui(player) {
            @Override
            public void onClose() {
                String newName = this.getLine(0).getString().trim();
                if (!newName.isEmpty() && !newName.equals(oldShopName)) {
                    if (!shopService.shopExists(newName)) {
                        shopService.updateShopName(oldShopName, newName);
                        player.sendMessage(I18n.translate("shop.name.updated").formatted(Formatting.GREEN), false);
                        openShopList(player, 0);
                    } else {
                        player.sendMessage(I18n.translate("shop.name.exists").formatted(Formatting.RED), false);
                        openShopSettings(player, oldShopName);
                    }
                } else {
                    openShopSettings(player, oldShopName);
                }
            }
        };
        signGui.setLine(0, Text.literal(""));
        signGui.setLine(1, I18n.translate("dialog.enter_shop_name"));
        signGui.setLine(2, I18n.translate("dialog.enter_shop_name.first"));
        signGui.open();
    }

    private void openCreateShopDialog(ServerPlayerEntity player, boolean isAdminShop) {
        ItemStack heldItem = player.getMainHandStack();
        if (heldItem.isEmpty()) {
            player.sendMessage(I18n.translate("shop.create.need_item").formatted(Formatting.RED), false);
            return;
        }

        SignGui signGui = new SignGui(player) {
            @Override
            public void onClose() {
                String shopName = this.getLine(0).getString().trim();
                if (!shopName.isEmpty()) {
                    if (!shopService.shopExists(shopName)) {
                        String serializedItem = inventoryService.serializeHeldItem(player);
                        if (serializedItem != null) {
                            shopService.createShop(shopName, serializedItem, "", isAdminShop);
                            player.sendMessage(I18n.translate(isAdminShop ? "shop.create.admin.success" : "shop.create.success", shopName)
                                    .formatted(Formatting.GREEN), false);
                            openShopList(player, 0);
                        } else {
                            player.sendMessage(I18n.translate("shop.create.error").formatted(Formatting.RED), false);
                        }
                    } else {
                        player.sendMessage(I18n.translate("shop.create.exists", shopName).formatted(Formatting.RED), false);
                        openShopList(player, 0);
                    }
                }
            }
        };
        signGui.setLine(0, Text.literal(""));
        signGui.setLine(1, I18n.translate("dialog.enter_shop_name"));
        signGui.setLine(2, I18n.translate("dialog.enter_shop_name.first"));
        signGui.open();
    }


    private void openMoveItemDialog(ServerPlayerEntity player, int itemId, String currentShopName) {
        // Get list of user shops excluding current shop
        List<ShopEntry> userShops = shopService.getUserShops(currentShopName);
        if (userShops.isEmpty()) {
            player.sendMessage(I18n.translate("error.no_other_shops").formatted(Formatting.RED), false);
            return;
        }

        SimpleGui gui = new SimpleGui(ScreenHandlerType.GENERIC_9X6, player, false);
        gui.setTitle(I18n.translate("gui.item.move.title"));

        // Display available shops
        for (int i = 0; i < Math.min(userShops.size(), SLOTS_PER_PAGE); i++) {
            ShopEntry shop = userShops.get(i);
            JsonElement shopItem = JsonParser.parseString(shop.item);
            ItemStack shopItemStack = ItemStack.CODEC.decode(JsonOps.INSTANCE, shopItem)
                    .resultOrPartial(Simpleshop.LOGGER::error)
                    .map(Pair::getFirst)
                    .orElse(null);

            GuiElementBuilder element = new GuiElementBuilder()
                    .setItem(shopItemStack.getItem())
                    .setName(Text.literal(shop.name))
                    .addLoreLine(Text.empty())
                    .addLoreLine(I18n.translate("gui.item.move.click").formatted(Formatting.YELLOW));

            if (!shop.description.isEmpty()) {
                element.addLoreLine(Text.empty());
                element.addLoreLine(Text.literal(shop.description).formatted(Formatting.GRAY));
            }

            element.setCallback((index, type, action) -> {
                shopService.moveItemToShop(player, itemId, currentShopName, shop.name);
openShopItems(player, shop.name, 0);
            });

            gui.setSlot(i, element.build());
        }

        // Back button
        gui.setSlot(SLOTS_PER_PAGE + 4, new GuiElementBuilder()
                .setItem(Items.BARRIER)
                .setName(I18n.translate("gui.shop.back").formatted(Formatting.RED))
                .setCallback((index, type, action) -> openItemDetails(player, currentShopName, itemId))
                .build());

        gui.open();
    }




}