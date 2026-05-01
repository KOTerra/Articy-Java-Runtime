package com.articy.runtime.logic;

public interface IScriptMethodProvider {
    Object invokeCustomMethod(String name, Object... args);
    void setVariableContext(ArticyVariableManager vars);
    default boolean isShadowState() {
        return false;
    }
}
