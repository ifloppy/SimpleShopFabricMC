package com.iruanp.simpleshop;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.JsonOps;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.impactdev.impactor.api.Impactor;
import net.impactdev.impactor.api.economy.*;
import net.impactdev.impactor.api.economy.accounts.Account;
import net.impactdev.impactor.api.economy.transactions.EconomyTransaction;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

public class Simpleshop implements ModInitializer {
    public static final String MOD_ID = "simpleshop";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static String savePath;
    public static ShopDatabase shopDatabase;
    EconomyService economyService;

    public MinecraftServer serverInstance;

    private static Simpleshop instance;

    public static RegistryOps<JsonElement> jsonops;

    @Override
    public void onInitialize() {
        LOGGER.info("SimpleShop is loading.");
        instance = this;
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        CommandRegistrationCallback.EVENT.register(this::registerCommands);
    }

    public static Simpleshop getInstance() {
        return instance;
    }

    private void onServerStarted(MinecraftServer server) {
        this.serverInstance = server;
        String serverPath = serverInstance.getRunDirectory().toAbsolutePath().toString();
        savePath = serverPath + "/" + serverInstance.getSaveProperties().getLevelName();
        shopDatabase = new ShopDatabase();

        economyService = Impactor.instance().services().provide(EconomyService.class);
        jsonops = serverInstance.getOverworld().getRegistryManager().getOps(JsonOps.INSTANCE);
    }

    private void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher,
            CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("shop")
                .executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayer();
                    new ShopGUI(shopDatabase).openShopList(player, 0);
                    return 1;
                })
                .then(CommandManager.literal("create")
                        .requires(Permissions.require("Simpleshop.Admin", 2))
                        .then(CommandManager.argument("name", StringArgumentType.string())
                                .executes(context -> {
                                    String name = StringArgumentType.getString(context, "name");
                                    if (shopDatabase.shopExists(name)) {
                                        context.getSource().sendFeedback(
                                                () -> Text.literal("Shop already exists: " + name), false);
                                        return 0;
                                    }
                                    ServerPlayerEntity player = context.getSource().getPlayer();
                                    ItemStack heldItem = player.getMainHandStack();

                                    if (heldItem.isEmpty()) {
                                        context.getSource()
                                                .sendFeedback(() -> Text.literal(
                                                        "You must hold an item in your main hand to create a shop."),
                                                        false);
                                        return 1;
                                    }

                                    JsonElement serializedItem = ItemStack.CODEC
                                            .encode(heldItem, jsonops, jsonops.empty())
                                            .result()
                                            .orElseThrow();
                                    shopDatabase.addShop(name, serializedItem.toString(), "", false);
                                    context.getSource().sendFeedback(() -> Text.literal("Shop created: " + name),
                                            false);
                                    return 1;
                                })))
                .then(CommandManager.literal("remove")
                        .requires(Permissions.require("Simpleshop.Admin", 2))
                        .then(CommandManager.argument("name", StringArgumentType.string())
                                .suggests((context, builder) -> {
                                    shopDatabase.getAllShopNames().forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    String name = StringArgumentType.getString(context, "name");
                                    if (shopDatabase.shopExists(name)) {
                                        context.getSource().sendFeedback(
                                                () -> Text.literal("Shop already exists: " + name), false);
                                        return 0;
                                    }
                                    shopDatabase.addShop(name, "", "", true);
                                    context.getSource().sendFeedback(() -> Text.literal("Admin shop created: " + name),
                                            false);
                                    return 1;
                                })))
                .then(CommandManager.literal("createAdmin")
                        .requires(Permissions.require("Simpleshop.Admin", 2))
                        .then(CommandManager.argument("name", StringArgumentType.string())
                                .executes(context -> {
                                    String name = StringArgumentType.getString(context, "name");
                                    ServerPlayerEntity player = context.getSource().getPlayer();
                                    ItemStack heldItem = player.getMainHandStack();

                                    if (heldItem.isEmpty()) {
                                        context.getSource()
                                                .sendFeedback(() -> Text.literal(
                                                        "You must hold an item in your main hand to create a shop."),
                                                        false);
                                        return 1;
                                    }

                                    JsonElement serializedItem = ItemStack.CODEC
                                            .encode(heldItem, jsonops, jsonops.empty())
                                            .result()
                                            .orElseThrow();
                                    shopDatabase.addShop(name, serializedItem.toString(), "", true);
                                    context.getSource().sendFeedback(() -> Text.literal("Admin shop created: " + name),
                                            false);
                                    return 1;
                                })))
                .then(CommandManager.literal("itemAdd")
                        .then(CommandManager.argument("shopName", StringArgumentType.string())
                                .suggests((context, builder) -> {
                                    shopDatabase.getAllShopNames().forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .then(CommandManager.literal("sell")
                                        .then(CommandManager.argument("price", DoubleArgumentType.doubleArg(0))
                                                .executes(context -> {
                                                    return handleItemAdd(context, true);
                                                })))
                                .then(CommandManager.literal("buy")
                                        .then(CommandManager.argument("price", DoubleArgumentType.doubleArg(0))
                                                .executes(context -> {
                                                    return handleItemAdd(context, false);
                                                })))))

                .then(CommandManager.literal("removeItem")
                        .then(CommandManager.argument("itemId", StringArgumentType.string())
                                .executes(context -> removeItemFromShop(context))))
                .then(CommandManager.literal("stock")
                        .then(CommandManager.argument("itemId", StringArgumentType.string())
                                .then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(context -> stockItemInShop(context)))))
                .then(CommandManager.literal("buy")
                        .then(CommandManager.argument("itemId", StringArgumentType.string())
                                .then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(context -> buyItemFromShop(context)))))
                .then(CommandManager.literal("sell")
                        .then(CommandManager.argument("itemId", StringArgumentType.string())
                                .then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(context -> sellItemToShop(context)))))
                .then(CommandManager.literal("take")
                        .then(CommandManager.argument("itemId", StringArgumentType.string())
                                .then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(context -> takeItemFromShop(context)))))
                .then(CommandManager.literal("edit")
                        .then(CommandManager.argument("itemId", StringArgumentType.string())
                                .then(CommandManager.literal("selling")
                                        .executes(context -> editShopItem(context, "selling", "true")))
                                .then(CommandManager.literal("buying")
                                        .executes(context -> editShopItem(context, "selling", "false")))
                                .then(CommandManager.literal("price")
                                        .then(CommandManager.argument("value", DoubleArgumentType.doubleArg(0))
                                                .executes(context -> editShopItem(context, "price", String
                                                        .valueOf(DoubleArgumentType.getDouble(context, "value")))))))));
    }

    public int handleItemAdd(CommandContext<ServerCommandSource> context, boolean isSell) {
        String shopName = StringArgumentType.getString(context, "shopName");
        BigDecimal itemPrice = BigDecimal.valueOf(DoubleArgumentType.getDouble(context, "price"));

        if (!shopDatabase.shopExists(shopName)) {
            context.getSource()
                    .sendFeedback(() -> Text.literal("Shop not found: " + shopName), false);
            return 0;
        }

        boolean isAdminShop = shopDatabase.isAdminShop(shopName);
        if (isAdminShop && !Permissions.check(context.getSource(), "Simpleshop.Admin", 2)) {
            context.getSource()
                    .sendFeedback(() -> Text.literal(
                            "You do not have permission to add items to admin shops."),
                            false);
            return 0;
        }

        addItemToShop(context, shopName, isSell, itemPrice);
        return 1;
    }

    public void addItemToShop(CommandContext<ServerCommandSource> context, String shopName, boolean isSell,
            BigDecimal price) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();
        ItemStack itemStack = player.getMainHandStack();

        if (itemStack.isEmpty()) {
            source.sendFeedback(() -> Text.literal("You must hold an item in your main hand to add it to the shop."),
                    false);
            return;
        }

        ItemStack singleItemStack = itemStack.copy();
        //singleItemStack.setCount(1);

        int totalItems = countMatchingItems(player, singleItemStack);
        JsonElement nbtData;
        try {
            Optional<JsonElement> encodedResult = ItemStack.CODEC
                    .encode(singleItemStack, jsonops, jsonops.empty())
                    .resultOrPartial(error -> {
                        LOGGER.error("Encoding error: {}", error);
                    });

            if (encodedResult.isPresent()) {
                nbtData = encodedResult.get();
            } else {
                LOGGER.error("Failed to encode ItemStack");
                return;
            }
        } catch (Exception e) {
            LOGGER.error("Exception during ItemStack encoding", e);
            return;
        }

        String nbtJson = nbtData.toString();
        Integer shopId = shopDatabase.getShopIdByName(shopName);
        shopDatabase.addItem(shopId, nbtJson, 0, isSell, price, player.getUuidAsString());
        source.sendFeedback(() -> Text
                .literal("Item added to shop: " + shopName + " (You have " + totalItems + " in your inventory)"),
                false);
    }

    public int removeItemFromShop(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        Integer itemId = Integer.parseInt(StringArgumentType.getString(context, "itemId"));

        if (!shopDatabase.itemExists(itemId)) {
            source.sendFeedback(() -> Text.literal("Item not found with ID: " + itemId), false);
            return 0;
        }

        String itemCreator = shopDatabase.getItemCreator(itemId);
        boolean isAdmin = Permissions.check(source, "Simpleshop.Admin", 2);

        if (!source.getPlayer().getUuidAsString().equals(itemCreator) && !isAdmin) {
            source.sendFeedback(() -> Text.literal("You don't have permission to remove this item."), false);
            return 0;
        }

        int quantity = shopDatabase.getItemQuantity(itemId);
        if (quantity > 0) {
            source.sendFeedback(
                    () -> Text.literal("Cannot remove item with remaining stock. Current quantity: " + quantity),
                    false);
            return 0;
        }

        shopDatabase.removeItem(itemId);
        source.sendFeedback(() -> Text.literal("Item successfully removed from shop."), false);
        return 1;
    }

    public int stockItemInShop(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();
        Integer itemId = Integer.parseInt(StringArgumentType.getString(context, "itemId"));
        int amount = IntegerArgumentType.getInteger(context, "amount");

        if (!shopDatabase.itemExists(itemId)) {
            source.sendFeedback(() -> Text.literal("Item not found with ID: " + itemId), false);
            return 0;
        }

        if (shopDatabase.isAdminShopByItemId(itemId)) {
            source.sendFeedback(() -> Text.literal("Cannot stock items in admin shops."), false);
            return 0;
        }

        String itemCreator = shopDatabase.getItemCreator(itemId);
        if (!source.getPlayer().getUuidAsString().equals(itemCreator)) {
            source.sendFeedback(() -> Text.literal("You don't have permission to stock this item."), false);
            return 0;
        }

        ItemStack shopItem = shopDatabase.getItemStack(itemId);
        if (shopItem == null) {
            source.sendFeedback(() -> Text.literal("Failed to get item data."), false);
            return 0;
        }

        int totalAvailable = countMatchingItems(player, shopItem);
        if (totalAvailable < amount) {
            source.sendFeedback(
                    () -> Text.literal(
                            "You don't have enough items. Required: " + amount + ", Available: " + totalAvailable),
                    false);
            return 0;
        }

        int remaining = amount;
        PlayerInventory inventory = player.getInventory();

        for (int i = 0; i < inventory.size() && remaining > 0; i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty() && stack.isOf(shopItem.getItem())) {
                int toRemove = Math.min(remaining, stack.getCount());
                stack.decrement(toRemove);
                remaining -= toRemove;
            }
        }

        shopDatabase.addStockToItem(itemId, amount);

        source.sendFeedback(() -> Text.literal("Successfully stocked " + amount + " items. (Remaining in inventory: "
                + (totalAvailable - amount) + ")"), false);
        return 1;
    }

    public int takeItemFromShop(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        Integer itemId = Integer.parseInt(StringArgumentType.getString(context, "itemId"));
        int amount = IntegerArgumentType.getInteger(context, "amount");

        try {
            takeItemFromShop(source, itemId, amount);
            return 1;
        } catch (IllegalStateException e) {
            source.sendFeedback(() -> Text.literal(e.getMessage()), false);
            return 0;
        }
    }

    public void takeItemFromShop(ServerCommandSource source, Integer itemId, int amount) {
        if (!shopDatabase.itemExists(itemId)) {
            throw new IllegalStateException("Item not found with ID: " + itemId);
        }

        String itemCreator = shopDatabase.getItemCreator(itemId);
        boolean isAdmin = Permissions.check(source, "Simpleshop.Admin", 2);

        if (!source.getPlayer().getUuidAsString().equals(itemCreator) && !isAdmin) {
            throw new IllegalStateException("You don't have permission to take this item.");
        }

        int currentQuantity = shopDatabase.getItemQuantity(itemId);
        if (currentQuantity < amount) {
            throw new IllegalStateException("Not enough items in stock. Available: " + currentQuantity);
        }

        ItemStack shopItem = shopDatabase.getItemStack(itemId);
        if (shopItem == null) {
            throw new IllegalStateException("Failed to get item data.");
        }

        ServerPlayerEntity player = source.getPlayer();
        ItemStack itemToGive = shopItem.copy();
        itemToGive.setCount(amount);
        if (!player.getInventory().insertStack(itemToGive.copy())) {
            throw new IllegalStateException("Your inventory is too full to receive these items.");
        }

        shopDatabase.removeStockFromItem(itemId, amount);

        player.getInventory().insertStack(itemToGive);

        source.sendFeedback(() -> Text.literal("Successfully took " + amount + " items from shop."), false);
    }

    public int buyItemFromShop(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        Integer itemId = Integer.parseInt(StringArgumentType.getString(context, "itemId"));
        int amount = IntegerArgumentType.getInteger(context, "amount");

        try {
            buyItemFromShopCore(source, itemId, amount);
            return 1;
        } catch (IllegalStateException e) {
            source.sendFeedback(() -> Text.literal(e.getMessage()), false);
            return 0;
        }
    }

    private void buyItemFromShopCore(ServerCommandSource source, Integer itemId, int amount) {
        if (!shopDatabase.itemExists(itemId)) {
            throw new IllegalStateException("Item not found with ID: " + itemId);
        }

        if (!shopDatabase.isItemForSale(itemId)) {
            throw new IllegalStateException("This item is not for sale.");
        }

        String itemCreator = shopDatabase.getItemCreator(itemId);

        if (!shopDatabase.isAdminShopByItemId(itemId)) {
            if (source.getPlayer().getUuidAsString().equals(itemCreator)) {
                throw new IllegalStateException("You cannot buy from your own item shop.");
            }

            int currentStock = shopDatabase.getItemQuantity(itemId);
            if (currentStock < amount) {
                throw new IllegalStateException("Not enough items in stock. Available: " + currentStock);
            }
        }

        ItemStack shopItem = shopDatabase.getItemStack(itemId);
        if (shopItem == null) {
            throw new IllegalStateException("Failed to get item data.");
        }

        ItemStack purchaseStack = shopItem.copy();
        purchaseStack.setCount(amount);

        ServerPlayerEntity player = source.getPlayer();
        if (!PlayerUtils.hasEnoughInventorySpace(player, purchaseStack)) {
            throw new IllegalStateException("You don't have enough inventory space!");
        }

        BigDecimal price = shopDatabase.getItemPrice(itemId);
        BigDecimal totalCost = price.multiply(BigDecimal.valueOf(amount));
        Account account = economyService.account(player.getUuid()).join();

        if (account.balance().compareTo(totalCost) < 0) {
            throw new IllegalStateException("You cannot afford this purchase. Cost: " + totalCost);
        }

        EconomyTransaction result = account.withdraw(totalCost);
        if (!result.successful()) {
            throw new IllegalStateException("Transaction failed: " + result.result().name());
        }

        if (!shopDatabase.isAdminShopByItemId(itemId)) {
            if (!itemCreator.isEmpty()) {
                Account sellerAccount = economyService.account(UUID.fromString(itemCreator)).join();
                sellerAccount.deposit(totalCost);
            } else {
                LOGGER.warn("Seller is NULL in this transaction");
            }
            shopDatabase.removeStockFromItem(itemId, amount);
        }

        player.getInventory().insertStack(purchaseStack);
        source.sendFeedback(() -> Text.literal("Successfully bought " + amount + " items for " + totalCost), false);
    }

    public int sellItemToShop(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        Integer itemId = Integer.parseInt(StringArgumentType.getString(context, "itemId"));
        int amount = IntegerArgumentType.getInteger(context, "amount");

        try {
            sellItemToShopCore(source, itemId, amount);
            return 1;
        } catch (IllegalStateException e) {
            source.sendFeedback(() -> Text.literal(e.getMessage()), false);
            return 0;
        }
    }

    private void sellItemToShopCore(ServerCommandSource source, Integer itemId, int amount) {
        if (!shopDatabase.itemExists(itemId)) {
            throw new IllegalStateException("Item not found with ID: " + itemId);
        }

        if (shopDatabase.isItemForSale(itemId)) {
            throw new IllegalStateException("This item is not available for selling.");
        }

        String itemCreator = shopDatabase.getItemCreator(itemId);
        if (!shopDatabase.isAdminShopByItemId(itemId)) {
            if (source.getPlayer().getUuidAsString().equals(itemCreator)) {
                throw new IllegalStateException("You cannot sell to your own item shop.");
            }
        }

        ItemStack shopItem = shopDatabase.getItemStack(itemId);
        if (shopItem == null) {
            throw new IllegalStateException("Failed to get item data.");
        }

        int currentStock = shopDatabase.getItemQuantity(itemId);
        if (currentStock + amount > 1024) {
            throw new IllegalStateException("Cannot exceed maximum stock limit of 1024.");
        }

        ServerPlayerEntity player = source.getPlayer();
        ItemStack playerStack = player.getMainHandStack();
        if (!playerStack.isOf(shopItem.getItem()) || playerStack.getCount() < amount) {
            throw new IllegalStateException("You don't have enough matching items in your main hand.");
        }

        BigDecimal price = shopDatabase.getItemPrice(itemId);
        BigDecimal totalCost = price.multiply(BigDecimal.valueOf(amount));

        if (!shopDatabase.isAdminShopByItemId(itemId)) {
            if (!itemCreator.isEmpty()) {
                Account creatorAccount = economyService.account(UUID.fromString(itemCreator)).join();
                if (creatorAccount.balance().compareTo(totalCost) < 0) {
                    throw new IllegalStateException("The shop owner cannot afford this purchase.");
                }

                EconomyTransaction creatorResult = creatorAccount.withdraw(totalCost);
                if (!creatorResult.successful()) {
                    throw new IllegalStateException("Transaction failed: " + creatorResult.result().name());
                }
            }
        }

        Account sellerAccount = economyService.account(player.getUuid()).join();
        EconomyTransaction sellerResult = sellerAccount.deposit(totalCost);
        if (!sellerResult.successful()) {
            throw new IllegalStateException("Transaction failed: " + sellerResult.result().name());
        }

        playerStack.decrement(amount);
        if (!shopDatabase.isAdminShopByItemId(itemId)) {
            shopDatabase.addStockToItem(itemId, amount);
        }

        source.sendFeedback(() -> Text.literal("Successfully sold " + amount + " items for " + totalCost), false);
    }

    public int buyItemFromShopDirect(ServerCommandSource source, int itemId, int amount) {
        try {
            buyItemFromShopCore(source, itemId, amount);
            return 1;
        } catch (IllegalStateException e) {
            source.sendFeedback(() -> Text.literal(e.getMessage()), false);
            return 0;
        }
    }

    public int sellItemToShopDirect(ServerCommandSource source, int itemId, int amount) {
        try {
            sellItemToShopCore(source, itemId, amount);
            return 1;
        } catch (IllegalStateException e) {
            source.sendFeedback(() -> Text.literal(e.getMessage()), false);
            return 0;
        }
    }

    private int editShopItem(CommandContext<ServerCommandSource> context, String property, String value) {
        ServerCommandSource source = context.getSource();
        Integer itemId = Integer.parseInt(StringArgumentType.getString(context, "itemId"));

        if (!shopDatabase.itemExists(itemId)) {
            source.sendFeedback(() -> Text.literal("Item not found with ID: " + itemId), false);
            return 0;
        }

        String itemCreator = shopDatabase.getItemCreator(itemId);
        boolean isAdmin = Permissions.check(source, "Simpleshop.Admin", 2);

        if (!source.getPlayer().getUuidAsString().equals(itemCreator) && !isAdmin) {
            source.sendFeedback(() -> Text.literal("You don't have permission to edit this item."), false);
            return 0;
        }

        String sql = "";
        if (property.equals("selling")) {
            sql = "UPDATE items SET isSelling = ? WHERE id = ?";
            try (PreparedStatement pstmt = shopDatabase.getConnection().prepareStatement(sql)) {
                pstmt.setBoolean(1, Boolean.parseBoolean(value));
                pstmt.setInt(2, itemId);
                pstmt.executeUpdate();
                source.sendFeedback(
                        () -> Text.literal("Item is now " + (Boolean.parseBoolean(value) ? "selling" : "buying")),
                        false);
            } catch (SQLException e) {
                e.printStackTrace();
                source.sendFeedback(() -> Text.literal("Failed to update item property"), false);
                return 0;
            }
        } else if (property.equals("price")) {
            sql = "UPDATE items SET price = ? WHERE id = ?";
            try (PreparedStatement pstmt = shopDatabase.getConnection().prepareStatement(sql)) {
                pstmt.setBigDecimal(1, new BigDecimal(value));
                pstmt.setInt(2, itemId);
                pstmt.executeUpdate();
                source.sendFeedback(() -> Text.literal("Item price updated to " + value), false);
            } catch (SQLException e) {
                e.printStackTrace();
                source.sendFeedback(() -> Text.literal("Failed to update item property"), false);
                return 0;
            }
        }

        return 1;
    }

    private int countMatchingItems(ServerPlayerEntity player, ItemStack targetItem) {
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
}