package com.articy.runtime.core;

import com.articy.runtime.logic.ArticyVariableManager;
import com.articy.runtime.logic.ExpressoEngine;
import com.articy.runtime.logic.IScriptMethodProvider;
import java.io.IOException;

public class ArticyRuntime {
    private static ArticyDatabase database;
    private static ArticyVariableManager variableManager;
    private static ExpressoEngine engine;

    public static ArticyDatabase initialize(String exportDir, IScriptMethodProvider methodProvider) throws IOException {
        database = new ArticyDatabase();
        variableManager = new ArticyVariableManager();
        engine = new ExpressoEngine(methodProvider);

        ArticyLoader loader = new ArticyLoader(database, variableManager);
        loader.loadFromDirectory(exportDir);

        return database;
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
}
