package com.articy.runtime.logic;

public interface IScriptMethodProvider {
    Object invokeCustomMethod(String name, Object... args);
    default boolean isShadowState() {
        return false;
    }
}
