package com.articy.runtime.logic;

import org.apache.commons.jexl3.*;
import org.apache.commons.jexl3.introspection.JexlPermissions;
import java.util.HashMap;
import java.util.Map;

/**
 * Evaluates Articy Expresso scripts (conditions and instructions).
 * Uses JEXL as the underlying expression engine.
 */
public class ExpressoEngine {
    private final JexlEngine jexl;

    public ExpressoEngine(IScriptMethodProvider provider) {
        JexlBuilder builder = new JexlBuilder()
                .cache(512)
                .strict(true)
                .silent(false)
                .permissions(JexlPermissions.UNRESTRICTED);
        
        if (provider != null) {
            Map<String, Object> namespaces = new HashMap<>();
            namespaces.put(null, provider);
            namespaces.put("articy", provider);
            builder.namespaces(namespaces);
        }
        
        this.jexl = builder.create();
    }

    /**
     * Evaluates a script as a boolean condition.
     */
    public boolean evaluateCondition(String script, ArticyVariableManager vars, IScriptMethodProvider provider) {
        if (script == null || script.trim().isEmpty()) {
            return true;
        }
        if (provider != null) {
            provider.setVariableContext(vars);
        }
        JexlScript s = jexl.createScript(preprocess(script));
        JexlContext context = createContext(vars, provider);
        Object result = s.execute(context);
        if (result instanceof Boolean) {
            return (Boolean) result;
        }
        return true; // Default to true for side-effect-only scripts
    }

    /**
     * Executes a script as an instruction (side effects only).
     */
    public void executeInstruction(String script, ArticyVariableManager vars, IScriptMethodProvider provider) {
        if (script == null || script.trim().isEmpty()) {
            return;
        }
        if (provider != null) {
            provider.setVariableContext(vars);
        }
        JexlScript s = jexl.createScript(preprocess(script));
        JexlContext context = createContext(vars, provider);
        s.execute(context);
    }

    private String preprocess(String script) {
        if (script == null) return null;
        // 1. Transform var++ and var--
        String processed = script.replaceAll("([a-zA-Z0-9_.]+)\\s*\\+\\+", "$1 = $1 + 1");
        processed = processed.replaceAll("([a-zA-Z0-9_.]+)\\s*--", "$1 = $1 - 1");
        
        // 2. Redirect global function calls to articy.invokeCustomMethod
        // Matches "func(" that isn't preceded by a dot or "articy."
        // Excludes reserved words like if, while, etc.
        // Handles optional leading minus for numeric arguments
        processed = processed.replaceAll("(?<![a-zA-Z0-9_.]|articy\\.)\\b(?!(?:if|while|for|else|switch|return|true|false|null|new|var|function)\\b)([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(", "articy.invokeCustomMethod('$1', ");
        
        return processed;
    }

    private JexlContext createContext(ArticyVariableManager vars, final IScriptMethodProvider provider) {
        class ArticyContext extends MapContext implements JexlContext.NamespaceResolver {
            @Override
            public Object resolveNamespace(String name) {
                if (name == null || "articy".equals(name)) {
                    return provider;
                }
                return null;
            }
        }
        
        ArticyContext context = new ArticyContext();
        for (Map.Entry<String, Map<String, Object>> entry : vars.getVariableSets().entrySet()) {
            context.set(entry.getKey(), entry.getValue());
        }
        
        context.set("unseen", true);
        
        if (provider != null) {
            context.set("articy", provider);
        }
        return context;
    }
}
