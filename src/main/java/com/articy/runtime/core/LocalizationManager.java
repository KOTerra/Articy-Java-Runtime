package com.articy.runtime.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages localized text for Articy objects.
 * Supports multiple languages and fallbacks.
 */
public class LocalizationManager {
    private final Map<String, Map<String, String>> localizations = new HashMap<>();
    private String currentLanguage = "en";

    /**
     * Loads localization data from an Articy localization JSON file.
     */
    public void loadFromFile(File file) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(file);
        
        // Handle both root-level entries and wrapped in "L10n"
        JsonNode l10n = root.has("L10n") ? root.get("L10n") : root;
        
        if (l10n != null && l10n.isObject()) {
            l10n.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                JsonNode languages = entry.getValue();
                if (!languages.isObject()) return;
                
                Map<String, String> langMap = new HashMap<>();
                languages.fields().forEachRemaining(langEntry -> {
                    String lang = langEntry.getKey();
                    JsonNode langNode = langEntry.getValue();
                    if (langNode.isObject() && langNode.has("Text")) {
                        String text = langNode.get("Text").asText();
                        langMap.put(lang, text);
                    }
                });
                if (!langMap.isEmpty()) {
                    localizations.put(key, langMap);
                }
            });
        }
    }

    /**
     * Returns the localized string for the given key in the current language.
     * Falls back to any available language if the current one is missing.
     */
    public String localize(String key) {
        if (key == null) return "";
        Map<String, String> langMap = localizations.get(key);
        if (langMap != null) {
            String text = langMap.get(currentLanguage);
            if (text != null) return text;
            // Fallback to first available
            return langMap.values().stream().findFirst().orElse(key);
        }
        return key;
    }

    public void setLanguage(String language) {
        this.currentLanguage = language;
    }
}
