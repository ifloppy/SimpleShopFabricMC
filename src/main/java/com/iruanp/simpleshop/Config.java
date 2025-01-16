package com.iruanp.simpleshop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class Config {
    private static final Logger LOGGER = LoggerFactory.getLogger("simpleshop-config");
    private static final String CONFIG_FILENAME = "config.json";
    private static Path configDir;
    private static ConfigData configData;

    public static class ConfigData {
        public String language = "en_us";
        public String shopTitle = "Shop List";
    }

    public static void init(Path rootConfigDir) {
        configDir = rootConfigDir.resolve("simpleshop");
        try {
            Files.createDirectories(configDir);
            loadConfig();
        } catch (IOException e) {
            LOGGER.error("Failed to create config directory", e);
        }
    }

    private static void loadConfig() {
        Path configFile = configDir.resolve(CONFIG_FILENAME);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        if (Files.exists(configFile)) {
            try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                configData = gson.fromJson(reader, ConfigData.class);
            } catch (IOException e) {
                LOGGER.error("Failed to load config file", e);
                configData = new ConfigData();
            }
        } else {
            configData = new ConfigData();
            saveConfig();
        }
    }

    private static void saveConfig() {
        Path configFile = configDir.resolve(CONFIG_FILENAME);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        try (Writer writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
            gson.toJson(configData, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to save config file", e);
        }
    }

    public static String getLanguage() {
        return configData.language;
    }

    public static void setLanguage(String language) {
        configData.language = language;
        saveConfig();
    }

    public static String getShopTitle() {
        return configData.shopTitle;
    }

    public static void setShopTitle(String title) {
        configData.shopTitle = title;
        saveConfig();
    }

    public static Path getConfigDir() {
        return configDir;
    }
} 