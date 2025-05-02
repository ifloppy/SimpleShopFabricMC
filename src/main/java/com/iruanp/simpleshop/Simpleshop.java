package com.iruanp.simpleshop;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.iruanp.mcuniversaleconomy.api.UniversalEconomyAPI;
import com.iruanp.mcuniversaleconomy.api.UniversalEconomyAPIImpl;
import com.iruanp.simpleshop.service.ShopService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.serialization.JsonOps;

import me.lucko.fabric.api.permissions.v0.Permissions;
import java.math.BigDecimal;
import java.util.UUID;

public class Simpleshop implements ModInitializer {
    public static final String MOD_ID = "simpleshop";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static String savePath;
    public static ShopDatabase shopDatabase;
    private UniversalEconomyAPI economyAPI;
    private ShopGUI shopGUI;
    private ShopService shopService;

    public MinecraftServer serverInstance;

    private static Simpleshop instance;

    public static RegistryOps<JsonElement> jsonops;

    private NotificationManager notificationManager;

    @Override
    public void onInitialize() {
        instance = this;
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        CommandRegistrationCallback.EVENT.register(this::registerCommands);

        // Register player join event
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (notificationManager != null) {
                notificationManager.checkNotifications(handler.player);
            }
        });
    }

    public static Simpleshop getInstance() {
        return instance;
    }

    private void onServerStarted(MinecraftServer server) {
        this.serverInstance = server;
        String serverPath = serverInstance.getRunDirectory().toAbsolutePath().toString();
        savePath = serverPath + "/" + serverInstance.getSaveProperties().getLevelName();
        
        jsonops = serverInstance.getOverworld().getRegistryManager().getOps(JsonOps.INSTANCE);
        
        Config.init(server.getRunDirectory().resolve("config"));
        I18n.init();
        
        shopDatabase = new ShopDatabase();
        shopService = new ShopService(shopDatabase);
        shopGUI = new ShopGUI(shopDatabase, shopService);
        
        // Initialize NotificationManager after database is ready
        notificationManager = new NotificationManager(shopDatabase, server);
        
        this.economyAPI = UniversalEconomyAPIImpl.getInstance();
    }

    private void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher,
            CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("shop")
                .requires(Permissions.require("Simpleshop.Use", 0))
                .executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayer();
                    shopGUI.openShopList(player, 0);
                    return 1;
                })
                .then(CommandManager.literal("reload")
                    .requires(Permissions.require("Simpleshop.Admin", 4))
                    .executes(context -> {
                        Config.init(serverInstance.getRunDirectory().resolve("config"));
                        I18n.init();
                        context.getSource().sendFeedback(() -> I18n.translate("command.reload.success"), true);
                        return 1;
                    })));
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


    // Core methods used by GUI
    public void stockItemInShopCore(ServerCommandSource source, Integer itemId, int amount) {
        if (!shopDatabase.itemExists(itemId)) {
            throw new IllegalStateException(I18n.translate("item.not_found", itemId).getString());
        }

        if (shopDatabase.isAdminShopByItemId(itemId)) {
            throw new IllegalStateException(I18n.translate("item.no_permission", "stock").getString());
        }

        String itemCreator = shopDatabase.getItemCreator(itemId);
        if (!source.getPlayer().getUuidAsString().equals(itemCreator)) {
            throw new IllegalStateException(I18n.translate("item.no_permission", "stock").getString());
        }

        ItemStack shopItem = shopDatabase.getItemStack(itemId);
        if (shopItem == null) {
            throw new IllegalStateException(I18n.translate("item.not_found", itemId).getString());
        }

        ServerPlayerEntity player = source.getPlayer();
        int totalAvailable = countMatchingItems(player, shopItem);
        if (totalAvailable < amount) {
            throw new IllegalStateException(I18n.translate("item.stock.not_matching").getString());
        }

        int remaining = amount;
        PlayerInventory inventory = player.getInventory();

        for (int i = 0; i < inventory.size() && remaining > 0; i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty() && stack.isOf(shopItem.getItem())) {
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

        shopDatabase.addStockToItem(itemId, amount);
        source.sendFeedback(() -> I18n.translate("item.stock.success", amount, totalAvailable - amount), false);
    }

    public void buyItemFromShopCore(ServerCommandSource source, Integer itemId, int amount) {
        if (!shopDatabase.itemExists(itemId)) {
            throw new IllegalStateException(I18n.translate("item.not_found", itemId).getString());
        }

        if (!shopDatabase.isItemForSale(itemId)) {
            throw new IllegalStateException(I18n.translate("item.not_for_sale").getString());
        }

        String itemCreator = shopDatabase.getItemCreator(itemId);

        if (!shopDatabase.isAdminShopByItemId(itemId)) {
            if (source.getPlayer().getUuidAsString().equals(itemCreator)) {
                throw new IllegalStateException(I18n.translate("item.buy.own_shop").getString());
            }
        }

        ServerPlayerEntity player = source.getPlayer();
        int maxPurchaseableAmount = getMaxPurchaseableAmount(player, itemId, amount);
        
        if (maxPurchaseableAmount <= 0) {
            if (!shopDatabase.isAdminShopByItemId(itemId)) {
                int currentStock = shopDatabase.getItemQuantity(itemId);
                throw new IllegalStateException(I18n.translate("error.insufficient_stock", currentStock).getString());
            }
            throw new IllegalStateException(I18n.translate("error.insufficient_space").getString());
        }

        ItemStack shopItem = shopDatabase.getItemStack(itemId);
        ItemStack purchaseStack = shopItem.copy();
        purchaseStack.setCount(maxPurchaseableAmount);

        BigDecimal price = shopDatabase.getItemPrice(itemId);
        BigDecimal totalCost = price.multiply(BigDecimal.valueOf(maxPurchaseableAmount));
        
        if (economyAPI.getBalance(player.getUuid()).join().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException(I18n.translate("error.no_account").getString());
        }
        
        if (!economyAPI.withdrawPlayer(player.getUuid(), totalCost).join()) {
            throw new IllegalStateException(I18n.translate("error.transaction_failed").getString());
        }

        if (!shopDatabase.isAdminShopByItemId(itemId)) {
            if (!itemCreator.isEmpty()) {
                economyAPI.depositPlayer(UUID.fromString(itemCreator), totalCost);
            }
            shopDatabase.removeStockFromItem(itemId, maxPurchaseableAmount);
        }

        player.getInventory().insertStack(purchaseStack);
        source.sendFeedback(() -> I18n.translate("item.buy.success", maxPurchaseableAmount, economyAPI.formatAmount(totalCost)), false);
    }

    public void sellItemToShopCore(ServerCommandSource source, Integer itemId, int amount) {
        if (!shopDatabase.itemExists(itemId)) {
            throw new IllegalStateException(I18n.translate("item.not_found", itemId).getString());
        }

        if (shopDatabase.isItemForSale(itemId)) {
            throw new IllegalStateException(I18n.translate("item.not_for_sale").getString());
        }

        String itemCreator = shopDatabase.getItemCreator(itemId);
        if (!shopDatabase.isAdminShopByItemId(itemId)) {
            if (source.getPlayer().getUuidAsString().equals(itemCreator)) {
                throw new IllegalStateException(I18n.translate("item.sell.own_shop").getString());
            }
        }

        ItemStack shopItem = shopDatabase.getItemStack(itemId);
        if (shopItem == null) {
            throw new IllegalStateException(I18n.translate("item.not_found", itemId).getString());
        }

        int currentStock = shopDatabase.getItemQuantity(itemId);
        if (currentStock + amount > 1024) {
            throw new IllegalStateException(I18n.translate("item.stock.max", 1024).getString());
        }

        ServerPlayerEntity player = source.getPlayer();
        ItemStack playerStack = player.getMainHandStack();
        if (!playerStack.isOf(shopItem.getItem()) || playerStack.getCount() < amount) {
            throw new IllegalStateException(I18n.translate("item.stock.not_matching").getString());
        }

        BigDecimal price = shopDatabase.getItemPrice(itemId);
        BigDecimal totalCost = price.multiply(BigDecimal.valueOf(amount));

        if (!shopDatabase.isAdminShopByItemId(itemId)) {
            if (!itemCreator.isEmpty()) {
                if (economyAPI.getBalance(UUID.fromString(itemCreator)).join().compareTo(BigDecimal.ZERO) < 0) {
                    throw new IllegalStateException(I18n.translate("error.no_account").getString());
                }
                
                if (!economyAPI.withdrawPlayer(UUID.fromString(itemCreator), totalCost).join()) {
                    throw new IllegalStateException(I18n.translate("error.transaction_failed").getString());
                }
            }
        }

        if (economyAPI.getBalance(player.getUuid()).join().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException(I18n.translate("error.no_account").getString());
        }
        
        if (!economyAPI.depositPlayer(player.getUuid(), totalCost).join()) {
            throw new IllegalStateException(I18n.translate("error.transaction_failed").getString());
        }

        playerStack.decrement(amount);
        if (!shopDatabase.isAdminShopByItemId(itemId)) {
            shopDatabase.addStockToItem(itemId, amount);
        }

        source.sendFeedback(() -> I18n.translate("item.sell.success", amount, economyAPI.formatAmount(totalCost)), false);
    }

    public NotificationManager getNotificationManager() {
        return notificationManager;
    }

    public int getMaxPurchaseableAmount(ServerPlayerEntity player, Integer itemId, int requestedAmount) {
        if (shopDatabase.isAdminShopByItemId(itemId)) {
            ItemStack shopItem = shopDatabase.getItemStack(itemId);
            ItemStack testStack = shopItem.copy();
            testStack.setCount(requestedAmount);
            return PlayerUtils.hasEnoughInventorySpace(player, testStack) ? requestedAmount : 0;
        }

        int currentStock = shopDatabase.getItemQuantity(itemId);
        int maxFromStock = Math.min(requestedAmount, currentStock);
        if (maxFromStock <= 0) {
            return 0;
        }

        ItemStack shopItem = shopDatabase.getItemStack(itemId);
        ItemStack testStack = shopItem.copy();
        testStack.setCount(maxFromStock);
        return PlayerUtils.hasEnoughInventorySpace(player, testStack) ? maxFromStock : 0;
    }
}
