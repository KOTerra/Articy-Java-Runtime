package com.articy.runtime.logic;

/**
 * Interface for providing custom methods to the Expresso script engine.
 */
public interface IScriptMethodProvider {
    /**
     * Invokes a custom script method by name.
     */
    Object invokeCustomMethod(String name, Object... args);

    /**
     * Called by the engine to provide the current variable context before execution.
     */
    void setVariableContext(ArticyVariableManager vars);

    default boolean isShadowState() {
        return false;
    }
}
