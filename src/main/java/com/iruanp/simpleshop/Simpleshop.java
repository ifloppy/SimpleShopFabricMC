package com.iruanp.simpleshop;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.serialization.JsonOps;

import me.lucko.fabric.api.permissions.v0.Permissions;
import eu.pb4.common.economy.api.CommonEconomy;
import eu.pb4.common.economy.api.EconomyAccount;
import eu.pb4.common.economy.api.EconomyProvider;
import eu.pb4.common.economy.api.EconomyTransaction;
import eu.pb4.common.economy.api.EconomyCurrency;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.Collection;

public class Simpleshop implements ModInitializer {
    public static final String MOD_ID = "simpleshop";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static String savePath;
    public static ShopDatabase shopDatabase;
    private EconomyProvider economyProvider;
    private EconomyCurrency defaultCurrency;
    private ShopGUI shopGUI;

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
        shopGUI = new ShopGUI(shopDatabase);
        
        // Initialize NotificationManager after database is ready
        notificationManager = new NotificationManager(shopDatabase, server);
        
        var providers = CommonEconomy.providers();
        if (providers.isEmpty()) {
            LOGGER.error(I18n.translate("error.economy_provider").getString());
            return;
        }
        
        economyProvider = providers.iterator().next();
        var currencies = economyProvider.getCurrencies(server);
        if (currencies.isEmpty()) {
            LOGGER.error(I18n.translate("error.currency").getString());
            return;
        }
        defaultCurrency = currencies.iterator().next();
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

    private String formatCurrency(long scaledValue) {
        return defaultCurrency.formatValue(scaledValue, true);
    }

    public String formatPrice(BigDecimal price) {
        String priceStr = String.format("%.2f", price);
        return defaultCurrency.formatValue(defaultCurrency.parseValue(priceStr), true);
    }

    private long scalePrice(BigDecimal price, int amount) {
        String priceStr = String.format("%.2f", price.multiply(BigDecimal.valueOf(amount)));
        return defaultCurrency.parseValue(priceStr);
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

            int currentStock = shopDatabase.getItemQuantity(itemId);
            if (currentStock < amount) {
                throw new IllegalStateException(I18n.translate("error.insufficient_stock", currentStock).getString());
            }
        }

        ItemStack shopItem = shopDatabase.getItemStack(itemId);
        if (shopItem == null) {
            throw new IllegalStateException(I18n.translate("item.not_found", itemId).getString());
        }

        ItemStack purchaseStack = shopItem.copy();
        purchaseStack.setCount(amount);

        ServerPlayerEntity player = source.getPlayer();
        if (!PlayerUtils.hasEnoughInventorySpace(player, purchaseStack)) {
            throw new IllegalStateException(I18n.translate("error.insufficient_space").getString());
        }

        BigDecimal price = shopDatabase.getItemPrice(itemId);
        long totalCost = scalePrice(price, amount);
        
        Collection<EconomyAccount> accounts = CommonEconomy.getAccounts(player, defaultCurrency);
        EconomyAccount account = accounts.isEmpty() ? null : accounts.iterator().next();
        if (account == null) {
            throw new IllegalStateException(I18n.translate("error.no_account").getString());
        }

        EconomyTransaction result = account.decreaseBalance(totalCost);
        if (!result.isSuccessful()) {
            throw new IllegalStateException(I18n.translate("error.transaction_failed", result.message().getString()).getString());
        }

        if (!shopDatabase.isAdminShopByItemId(itemId)) {
            if (!itemCreator.isEmpty()) {
                Collection<EconomyAccount> sellerAccounts = CommonEconomy.getAccounts(serverInstance, new GameProfile(UUID.fromString(itemCreator), ""), defaultCurrency);
                EconomyAccount sellerAccount = sellerAccounts.isEmpty() ? null : sellerAccounts.iterator().next();
                if (sellerAccount != null) {
                    sellerAccount.increaseBalance(totalCost);
                }
            }
            shopDatabase.removeStockFromItem(itemId, amount);
        }

        player.getInventory().insertStack(purchaseStack);
        source.sendFeedback(() -> I18n.translate("item.buy.success", amount, formatCurrency(totalCost)), false);
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
        long totalCost = scalePrice(price, amount);

        if (!shopDatabase.isAdminShopByItemId(itemId)) {
            if (!itemCreator.isEmpty()) {
                Collection<EconomyAccount> creatorAccounts = CommonEconomy.getAccounts(serverInstance, new GameProfile(UUID.fromString(itemCreator), ""), defaultCurrency);
                EconomyAccount creatorAccount = creatorAccounts.isEmpty() ? null : creatorAccounts.iterator().next();
                if (creatorAccount == null) {
                    throw new IllegalStateException(I18n.translate("error.no_account").getString());
                }

                EconomyTransaction creatorResult = creatorAccount.decreaseBalance(totalCost);
                if (!creatorResult.isSuccessful()) {
                    throw new IllegalStateException(I18n.translate("error.transaction_failed", creatorResult.message().getString()).getString());
                }
            }
        }

        Collection<EconomyAccount> sellerAccounts = CommonEconomy.getAccounts(player, defaultCurrency);
        EconomyAccount sellerAccount = sellerAccounts.isEmpty() ? null : sellerAccounts.iterator().next();
        if (sellerAccount == null) {
            throw new IllegalStateException(I18n.translate("error.no_account").getString());
        }

        EconomyTransaction sellerResult = sellerAccount.increaseBalance(totalCost);
        if (!sellerResult.isSuccessful()) {
            throw new IllegalStateException(I18n.translate("error.transaction_failed", sellerResult.message().getString()).getString());
        }

        playerStack.decrement(amount);
        if (!shopDatabase.isAdminShopByItemId(itemId)) {
            shopDatabase.addStockToItem(itemId, amount);
        }

        source.sendFeedback(() -> I18n.translate("item.sell.success", amount, formatCurrency(totalCost)), false);
    }

    public NotificationManager getNotificationManager() {
        return notificationManager;
    }
}