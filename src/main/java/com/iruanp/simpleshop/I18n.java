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

        // Copy language files from resources if they don't exist
        copyLanguageFile(langDir, "en_us");
        copyLanguageFile(langDir, "zh_cn");

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

    private static void copyLanguageFile(Path langDir, String language) {
        Path langFile = langDir.resolve(language + ".json");
        if (!Files.exists(langFile)) {
            try (InputStream is = I18n.class.getClassLoader().getResourceAsStream("lang/" + language + ".json")) {
                if (is != null) {
                    Files.copy(is, langFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    LOGGER.info("Copied language file: " + language + ".json");
                } else {
                    LOGGER.warn("Language file not found in resources: " + language + ".json");
                }
            } catch (IOException e) {
                LOGGER.error("Failed to copy language file: " + language + ".json", e);
            }
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