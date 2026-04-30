package com.articy.runtime.logic;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ArticyVariableManager {
    private final Map<String, Map<String, Object>> variableSets;
    private final boolean isShadowState;

    public ArticyVariableManager() {
        this.variableSets = new ConcurrentHashMap<>();
        this.isShadowState = false;
    }

    private ArticyVariableManager(Map<String, Map<String, Object>> variableSets, boolean isShadowState) {
        this.variableSets = variableSets;
        this.isShadowState = isShadowState;
    }

    public Object getVariable(String set, String var) {
        Map<String, Object> setMap = variableSets.get(set);
        if (setMap != null) {
            return setMap.get(var);
        }
        return null;
    }

    public void setVariable(String set, String var, Object value) {
        Map<String, Object> setMap = variableSets.computeIfAbsent(set, k -> new ConcurrentHashMap<>());
        setMap.put(var, value);
    }

    public Map<String, Map<String, Object>> getVariableSets() {
        return variableSets;
    }

    public boolean isInShadowState() {
        return isShadowState;
    }

    public ArticyVariableManager createShadowState() {
        Map<String, Map<String, Object>> clonedSets = new ConcurrentHashMap<>();
        for (Map.Entry<String, Map<String, Object>> setEntry : variableSets.entrySet()) {
            clonedSets.put(setEntry.getKey(), new ConcurrentHashMap<>(setEntry.getValue()));
        }
        return new ArticyVariableManager(clonedSets, true);
    }
}
