package com.articy.runtime.logic;

import org.apache.commons.jexl3.*;
import org.apache.commons.jexl3.introspection.JexlPermissions;
import java.util.HashMap;
import java.util.Map;

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

    public boolean evaluateCondition(String script, ArticyVariableManager vars, IScriptMethodProvider provider) {
        if (script == null || script.trim().isEmpty()) {
            return true;
        }
        JexlExpression expression = jexl.createExpression(preprocess(script));
        JexlContext context = createContext(vars, provider);
        Object result = expression.evaluate(context);
        if (result instanceof Boolean) {
            return (Boolean) result;
        }
        return false;
    }

    public void executeInstruction(String script, ArticyVariableManager vars, IScriptMethodProvider provider) {
        if (script == null || script.trim().isEmpty()) {
            return;
        }
        JexlExpression expression = jexl.createExpression(preprocess(script));
        JexlContext context = createContext(vars, provider);
        expression.evaluate(context);
    }

    private String preprocess(String script) {
        if (script == null) return null;
        // Simple regex to transform var++ into var = var + 1
        // and var-- into var = var - 1
        // Note: This is a basic implementation for the purpose of the runtime.
        String processed = script.replaceAll("([a-zA-Z0-9_.]+)\\s*\\+\\+", "$1 = $1 + 1");
        processed = processed.replaceAll("([a-zA-Z0-9_.]+)\\s*--", "$1 = $1 - 1");
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
