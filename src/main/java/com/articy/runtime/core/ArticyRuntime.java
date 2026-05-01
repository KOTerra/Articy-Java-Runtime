package com.articy.runtime.core;

import com.articy.runtime.logic.ArticyVariableManager;
import com.articy.runtime.logic.ExpressoEngine;
import com.articy.runtime.logic.IScriptMethodProvider;
import java.io.File;
import java.io.IOException;

/**
 * Central entry point for the Articy runtime.
 * Manages the database, variable manager, expression engine, and localization.
 */
public class ArticyRuntime {
    private static ArticyDatabase database;
    private static ArticyVariableManager variableManager;
    private static ExpressoEngine engine;
    private static LocalizationManager localizationManager;
    private static ArticyFlowPlayer flowPlayer;

    /**
     * Initializes the runtime by loading data from the specified export directory.
     * @param exportDir The path to the Articy JSON export directory.
     * @param methodProvider Provider for custom script methods.
     * @return The initialized ArticyDatabase.
     * @throws IOException If loading fails.
     */
    public static ArticyDatabase initialize(String exportDir, IScriptMethodProvider methodProvider) throws IOException {
        database = new ArticyDatabase();
        variableManager = new ArticyVariableManager();
        engine = new ExpressoEngine(methodProvider);
        localizationManager = new LocalizationManager();

        ArticyLoader loader = new ArticyLoader(database, variableManager);
        loader.loadFromDirectory(exportDir);
        
        // Load localization from directory (looking for any localization.json)
        File dir = new File(exportDir);
        File[] files = dir.listFiles((d, name) -> name.endsWith("localization.json"));
        if (files != null) {
            for (File f : files) {
                localizationManager.loadFromFile(f);
            }
        }

        return database;
    }

    public static LocalizationManager getLocalization() {
        return localizationManager;
    }

    public static ArticyDatabase getDatabase() {
        return database;
    }

    public static ArticyVariableManager getVariableManager() {
        return variableManager;
    }

    public static ExpressoEngine getEngine() {
        return engine;
    }

    public static ArticyFlowPlayer getFlowPlayer() {
        return flowPlayer;
    }

    public static void setFlowPlayer(ArticyFlowPlayer player) {
        flowPlayer = player;
    }
}
