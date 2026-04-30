package com.articy.runtime.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class LocalizationManager {
    private final Map<String, Map<String, String>> localizations = new HashMap<>();
    private String currentLanguage = "en";

    public void loadFromFile(File file) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(file);
        JsonNode l10n = root.get("L10n");
        if (l10n != null && l10n.isObject()) {
            l10n.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                JsonNode languages = entry.getValue();
                Map<String, String> langMap = new HashMap<>();
                languages.fields().forEachRemaining(langEntry -> {
                    String lang = langEntry.getKey();
                    String text = langEntry.getValue().get("Text").asText();
                    langMap.put(lang, text);
                });
                localizations.put(key, langMap);
            });
        }
    }

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
