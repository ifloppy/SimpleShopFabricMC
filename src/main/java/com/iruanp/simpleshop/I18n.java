package com.iruanp.simpleshop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class I18n {
    private static final Logger LOGGER = LoggerFactory.getLogger("simpleshop-i18n");
    private static final String DEFAULT_LANGUAGE = "en_us";
    private static Map<String, Map<String, String>> translations = new HashMap<>();
    private static String currentLanguage = DEFAULT_LANGUAGE;

    public static void init() {
        loadTranslations();
        setLanguage(Config.getLanguage());
    }

    private static void loadTranslations() {
        Path langDir = Config.getConfigDir().resolve("lang");
        try {
            Files.createDirectories(langDir);
        } catch (IOException e) {
            LOGGER.error("Failed to create language directory", e);
            return;
        }

        // Create default translations if they don't exist
        if (!Files.exists(langDir.resolve(DEFAULT_LANGUAGE + ".json"))) {
            createDefaultTranslations(langDir);
        }

        try {
            Files.list(langDir)
                .filter(path -> path.toString().endsWith(".json"))
                .forEach(file -> {
                    String langCode = file.getFileName().toString().replace(".json", "");
                    try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                        Type type = new TypeToken<Map<String, String>>(){}.getType();
                        Map<String, String> langData = new Gson().fromJson(reader, type);
                        translations.put(langCode, langData);
                    } catch (IOException e) {
                        LOGGER.error("Failed to load language file: " + file.getFileName(), e);
                    }
                });
        } catch (IOException e) {
            LOGGER.error("Failed to list language files", e);
        }
    }

    private static void createDefaultTranslations(Path langDir) {
        Map<String, String> defaultTranslations = new HashMap<>();
        // Shop GUI translations
        defaultTranslations.put("gui.shop.title", "Shop List - Page %d/%d");
        defaultTranslations.put("gui.shop.type", "Type: %s");
        defaultTranslations.put("gui.shop.type.admin", "Admin Shop");
        defaultTranslations.put("gui.shop.type.player", "Player Shop");
        defaultTranslations.put("gui.shop.description", "Description:");
        defaultTranslations.put("gui.shop.click_view", "Click to view items");
        defaultTranslations.put("gui.shop.shift_edit", "Shift-Click to edit description");
        defaultTranslations.put("gui.shop.right_edit", "Right-Click to edit shop settings");
        defaultTranslations.put("gui.shop.create_normal", "Create Normal Shop");
        defaultTranslations.put("gui.shop.create_normal.desc", "Click to create a new player shop");
        defaultTranslations.put("gui.shop.create_admin", "Create Admin Shop");
        defaultTranslations.put("gui.shop.create_admin.desc", "Click to create a new admin shop");
        defaultTranslations.put("gui.shop.hold_item", "Hold an item to use as shop icon");
        defaultTranslations.put("gui.shop.prev_page", "Previous Page");
        defaultTranslations.put("gui.shop.next_page", "Next Page");
        defaultTranslations.put("gui.shop.back", "Back to Shops");
        defaultTranslations.put("gui.shop.settings", "Shop Settings");
        defaultTranslations.put("gui.shop.edit_name", "Edit Shop Name");
        defaultTranslations.put("gui.shop.edit_name.desc", "Click to change shop name");
        defaultTranslations.put("gui.shop.edit_desc", "Edit Description");
        defaultTranslations.put("gui.shop.edit_desc.desc", "Click to change description");
        defaultTranslations.put("gui.shop.edit_icon", "Change Shop Icon");
        defaultTranslations.put("gui.shop.edit_icon.desc", "Click to set held item as icon");
        defaultTranslations.put("gui.shop.icon_updated", "Shop icon updated!");
        defaultTranslations.put("gui.shop.need_icon", "You must hold an item to set as the shop icon!");
        
        // Item GUI translations
        defaultTranslations.put("gui.item.details", "%s");
        defaultTranslations.put("gui.item.creator", "Creator: %s");
        defaultTranslations.put("gui.item.buy", "Buy Items");
        defaultTranslations.put("gui.item.buy.desc", "Click to specify amount");
        defaultTranslations.put("gui.item.quick_sell", "Quick Sell");
        defaultTranslations.put("gui.item.quick_sell.left", "Left-Click: Sell 1");
        defaultTranslations.put("gui.item.quick_sell.right", "Right-Click: Custom amount");
        defaultTranslations.put("gui.item.edit_price", "Edit Price");
        defaultTranslations.put("gui.item.edit_price.desc", "Click to change price");
        defaultTranslations.put("gui.item.toggle_mode", "Toggle Mode");
        defaultTranslations.put("gui.item.toggle_mode.current", "Current: %s");
        defaultTranslations.put("gui.item.toggle_mode.selling", "Selling");
        defaultTranslations.put("gui.item.toggle_mode.buying", "Buying");
        defaultTranslations.put("gui.item.remove", "Remove Item");
        defaultTranslations.put("gui.item.remove.desc", "Click to remove");
        defaultTranslations.put("gui.item.add_stock", "Add Stock");
        defaultTranslations.put("gui.item.add_stock.desc", "Click to add items from inventory");
        defaultTranslations.put("gui.item.withdraw", "Withdraw Stock");
        defaultTranslations.put("gui.item.withdraw.desc", "Click to withdraw items");
        defaultTranslations.put("gui.item.no_stock", "No items in stock to withdraw!");
        defaultTranslations.put("gui.item.click_details", "Left-Click to view details");
        defaultTranslations.put("gui.item.click_quick_action", "Right-Click to quick buy/sell");
        defaultTranslations.put("gui.item.create_new", "Create New Item");
        defaultTranslations.put("gui.item.create_new.desc", "Click to add new item");
        defaultTranslations.put("gui.item.current", "Current Item");
        defaultTranslations.put("gui.item.no_item", "No Item Selected");
        defaultTranslations.put("gui.item.hold_change", "Hold a different item to change");
        defaultTranslations.put("gui.item.create_buy", "Create Buy Offer");
        defaultTranslations.put("gui.item.create_buy.desc", "Players can buy this item");
        defaultTranslations.put("gui.item.create_sell", "Create Sell Offer");
        defaultTranslations.put("gui.item.create_sell.desc", "Players can sell this item");
        defaultTranslations.put("gui.item.set_price", "Click to set price");
        defaultTranslations.put("gui.item.quantity", "Quantity: %d");
        defaultTranslations.put("gui.item.price", "Price: %s");
        defaultTranslations.put("gui.item.stock", "Stock: %d");
        
        // Dialog translations
        defaultTranslations.put("dialog.enter_amount", "Enter amount");
        defaultTranslations.put("dialog.enter_amount.buy", "Enter amount to buy");
        defaultTranslations.put("dialog.enter_amount.sell", "Enter amount to sell");
        defaultTranslations.put("dialog.enter_amount.add", "Enter amount to add");
        defaultTranslations.put("dialog.enter_amount.withdraw", "Enter amount to withdraw");
        defaultTranslations.put("dialog.enter_amount.from_inv", "from your inventory");
        defaultTranslations.put("dialog.enter_shop_name", "Enter shop name");
        defaultTranslations.put("dialog.enter_shop_name.first", "on the first line");
        defaultTranslations.put("dialog.enter_price", "Enter price");
        defaultTranslations.put("dialog.enter_price.example", "Example: 10.5");
        defaultTranslations.put("dialog.confirm", "Confirm");
        defaultTranslations.put("dialog.cancel", "Cancel");
        defaultTranslations.put("dialog.toggle_mode.title", "Toggle Mode");
        defaultTranslations.put("dialog.toggle_mode.message", "Change to %s mode?");
        defaultTranslations.put("dialog.remove_item.title", "Remove Item");
        defaultTranslations.put("dialog.remove_item.message", "Are you sure you want to remove this item?");
        defaultTranslations.put("dialog.edit_description", "Enter description");
        defaultTranslations.put("dialog.edit_description.lines", "on these lines");
        
        // Shop management
        defaultTranslations.put("shop.create.need_item", "You must hold an item in your main hand to create a shop.");
        defaultTranslations.put("shop.create.exists", "Shop already exists: %s");
        defaultTranslations.put("shop.create.success", "Shop created: %s");
        defaultTranslations.put("shop.create.admin.success", "Admin shop created: %s");
        defaultTranslations.put("shop.name.updated", "Shop name updated!");
        defaultTranslations.put("shop.name.exists", "A shop with that name already exists!");
        defaultTranslations.put("shop.description.updated", "Shop description updated!");
        defaultTranslations.put("shop.remove.success", "Shop removed: %s");
        
        // Item management
        defaultTranslations.put("item.not_found", "Item not found with ID: %s");
        defaultTranslations.put("item.no_permission", "You don't have permission to %s this item.");
        defaultTranslations.put("item.stock.quantity", "Current stock: %d");
        defaultTranslations.put("item.price.buy", "Buy Price: %s");
        defaultTranslations.put("item.price.sell", "Sell Price: %s");
        defaultTranslations.put("item.not_for_sale", "This item is not for sale.");
        defaultTranslations.put("item.buy.own_shop", "You cannot buy from your own item shop.");
        defaultTranslations.put("item.buy.success", "Successfully bought %d items for %s");
        defaultTranslations.put("item.take.success", "Successfully took %d items from shop");
        defaultTranslations.put("item.sell.own_shop", "You cannot sell to your own item shop.");
        defaultTranslations.put("item.sell.success", "Successfully sold %d items for %s");
        defaultTranslations.put("item.stock.success", "Successfully stocked %d items. (Remaining in inventory: %d)");
        defaultTranslations.put("item.stock.max", "Cannot exceed maximum stock limit of %d.");
        defaultTranslations.put("item.stock.not_matching", "You don't have enough matching items.");
        defaultTranslations.put("item.mode.updated", "Item is now %s");
        defaultTranslations.put("item.price.updated", "Item price updated to %s");
        defaultTranslations.put("item.create.success", "Item added to shop!");
        
        // Errors
        defaultTranslations.put("error.invalid_amount", "Invalid amount!");
        defaultTranslations.put("error.insufficient_stock", "Not enough items in stock. Available: %d");
        defaultTranslations.put("error.insufficient_space", "Your inventory is too full to receive these items.");
        defaultTranslations.put("error.insufficient_funds", "You don't have enough funds for this transaction.");
        defaultTranslations.put("error.no_account", "Could not find your economy account!");
        defaultTranslations.put("error.transaction_failed", "Transaction failed: %s");
        defaultTranslations.put("error.update_failed", "Failed to update item property");
        defaultTranslations.put("error.economy_provider", "No economy provider found! Shop functionality will be limited.");
        defaultTranslations.put("error.currency", "No currency found! Shop functionality will be limited.");
        defaultTranslations.put("error.price.zero", "Price must be greater than 0!");
        defaultTranslations.put("error.price.invalid", "Invalid price format!");
        defaultTranslations.put("error.item.create", "Failed to create item: %s");
        defaultTranslations.put("command.reload.success", "Successfully reloaded configuration and messages!");

        try {
            Path defaultLangFile = langDir.resolve(DEFAULT_LANGUAGE + ".json");
            try (Writer writer = Files.newBufferedWriter(defaultLangFile, StandardCharsets.UTF_8)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(defaultTranslations, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to create default language file", e);
        }
    }

    public static MutableText translate(String key, Object... args) {
        Map<String, String> currentTranslations = translations.get(currentLanguage);
        if (currentTranslations == null) {
            currentTranslations = translations.get(DEFAULT_LANGUAGE);
        }

        String pattern = currentTranslations != null ? currentTranslations.get(key) : key;
        if (pattern == null) {
            pattern = key;
        }

        return Text.literal(String.format(pattern, args));
    }

    public static MutableText translate(String key, Formatting formatting, Object... args) {
        return translate(key, args).formatted(formatting);
    }

    public static void setLanguage(String language) {
        if (translations.containsKey(language)) {
            currentLanguage = language;
            Config.setLanguage(language);
        } else {
            LOGGER.warn("Language {} not found, falling back to {}", language, DEFAULT_LANGUAGE);
            currentLanguage = DEFAULT_LANGUAGE;
            Config.setLanguage(DEFAULT_LANGUAGE);
        }
    }
} 