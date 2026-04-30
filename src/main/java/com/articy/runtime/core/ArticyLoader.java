package com.articy.runtime.core;

import com.articy.runtime.logic.ArticyVariableManager;
import com.articy.runtime.model.*;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Iterator;

public class ArticyLoader {
    private final ArticyDatabase database;
    private final ArticyVariableManager variableManager;
    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonFactory factory = new JsonFactory();

    public ArticyLoader(ArticyDatabase database, ArticyVariableManager variableManager) {
        this.database = database;
        this.variableManager = variableManager;
    }

    public void loadFromDirectory(String exportDir) throws IOException {
        File manifestFile = new File(exportDir, "manifest.json");
        JsonNode manifest = mapper.readTree(manifestFile);

        // 1. Load Global Variables
        String gvFile = manifest.get("GlobalVariables").get("FileName").asText();
        loadGlobalVariables(new File(exportDir, gvFile));

        // 2. Load Packages (Objects)
        JsonNode packages = manifest.get("Packages");
        if (packages.isArray()) {
            for (JsonNode pkg : packages) {
                String objFile = pkg.get("Files").get("Objects").get("FileName").asText();
                loadObjects(new File(exportDir, objFile));
            }
        }

        // 3. Resolve Hierarchy
        String hierarchyFile = manifest.get("Hierarchy").get("FileName").asText();
        resolveHierarchy(new File(exportDir, hierarchyFile));
    }

    private void loadGlobalVariables(File file) throws IOException {
        JsonNode root = mapper.readTree(file);
        JsonNode gvArray = root.get("GlobalVariables");
        if (gvArray != null && gvArray.isArray()) {
            for (JsonNode set : gvArray) {
                String namespace = set.get("Namespace").asText();
                JsonNode vars = set.get("Variables");
                if (vars.isArray()) {
                    for (JsonNode var : vars) {
                        String name = var.get("Variable").asText();
                        String type = var.get("Type").asText();
                        String valueStr = var.get("Value").asText();
                        Object value = parseVariableValue(type, valueStr);
                        variableManager.setVariable(namespace, name, value);
                    }
                }
            }
        }
    }

    private Object parseVariableValue(String type, String valueStr) {
        switch (type) {
            case "Boolean":
                return Boolean.parseBoolean(valueStr);
            case "Integer":
                return Integer.parseInt(valueStr);
            case "String":
                return valueStr;
            default:
                return valueStr;
        }
    }

    private void loadObjects(File file) throws IOException {
        try (JsonParser parser = factory.createParser(file)) {
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String name = parser.getCurrentName();
                if ("Objects".equals(name)) {
                    if (parser.nextToken() == JsonToken.START_ARRAY) {
                        while (parser.nextToken() != JsonToken.END_ARRAY) {
                            JsonNode node = mapper.readTree(parser);
                            parseObject(node);
                        }
                    }
                }
            }
        }
    }

    private void parseObject(JsonNode node) {
        String type = node.get("Type").asText();
        JsonNode props = node.get("Properties");
        ArticyObject obj = createObjectByType(type);
        if (obj == null) {
            // System.out.println("Skipping unknown type: " + type);
            return;
        }

        long id = ArticyDatabase.parseHexId(props.get("Id").asText());
        obj.setId(id);
        // System.out.println("Loading object: " + type + " ID: " + Long.toHexString(id));
        if (props.has("TechnicalName")) {
            obj.setTechnicalName(props.get("TechnicalName").asText());
        }
        if (props.has("Parent")) {
            obj.setParentId(ArticyDatabase.parseHexId(props.get("Parent").asText()));
        }

        if (obj instanceof FlowObject) {
            parseFlowProperties((FlowObject) obj, props);
        }

        if (obj instanceof DialogueFragment) {
            DialogueFragment df = (DialogueFragment) obj;
            df.setSpeakerId(ArticyDatabase.parseHexId(props.get("Speaker").asText()));
            df.setText(props.get("Text").asText());
            df.setMenuText(props.get("MenuText").asText());
        } else if (obj instanceof Condition) {
            ((Condition) obj).setExpression(props.get("Expression").asText());
        } else if (obj instanceof Instruction) {
            ((Instruction) obj).setExpression(props.get("Expression").asText());
        } else if (obj instanceof Jump) {
            Jump j = (Jump) obj;
            j.setTargetId(ArticyDatabase.parseHexId(props.get("Target").asText()));
            j.setTargetPinId(ArticyDatabase.parseHexId(props.get("TargetPin").asText()));
        } else if (obj instanceof AssetObject) {
            AssetObject a = (AssetObject) obj;
            if (props.has("AssetRef")) a.setAssetRef(props.get("AssetRef").asText());
            if (node.has("AssetCategory")) a.setCategory(node.get("AssetCategory").asText());
        }

        database.registerObject(obj);
        if (id == 0x0100000000002CC3L) {
            System.out.println("LOGGED TARGET OBJECT: " + type + " " + Long.toHexString(id));
        }
    }

    private void parseFlowProperties(FlowObject flowObj, JsonNode props) {
        if (props.has("InputPins")) {
            for (JsonNode pinNode : props.get("InputPins")) {
                flowObj.getInputPins().add(parsePin(pinNode));
            }
        }
        if (props.has("OutputPins")) {
            for (JsonNode pinNode : props.get("OutputPins")) {
                flowObj.getOutputPins().add(parsePin(pinNode));
            }
        }
    }

    private Pin parsePin(JsonNode node) {
        Pin pin = new Pin();
        pin.setId(ArticyDatabase.parseHexId(node.get("Id").asText()));
        if (node.has("Text")) pin.setScript(node.get("Text").asText()); // Wait, is script in Text? Spec says "script"
        // Let's re-check the spec and JSON.
        // Spec says: Pin.script.
        // JSON shows Pin has "Text", but instruction/condition has "Expression".
        // Actually, Pins in Articy can have scripts.
        if (node.has("Expression")) pin.setScript(node.get("Expression").asText());
        
        if (node.has("Connections")) {
            for (JsonNode connNode : node.get("Connections")) {
                long targetPin = ArticyDatabase.parseHexId(connNode.get("TargetPin").asText());
                long targetNode = ArticyDatabase.parseHexId(connNode.get("Target").asText());
                pin.getConnections().add(new Connection(targetPin, targetNode));
            }
        }
        return pin;
    }

    private ArticyObject createObjectByType(String type) {
        switch (type) {
            case "DialogueFragment":
            case "DialogueLine": 
                return new DialogueFragment();
            case "Dialogue": return new Dialogue();
            case "Hub": return new Hub();
            case "Condition": return new Condition();
            case "Instruction": return new Instruction();
            case "Jump": return new Jump();
            case "FlowFragment": return new FlowFragment();
            case "Asset": return new AssetObject();
            default: return null;
        }
    }

    private void resolveHierarchy(File file) throws IOException {
        JsonNode root = mapper.readTree(file);
        processHierarchyNode(root);
    }

    private void processHierarchyNode(JsonNode node) {
        if (node.has("Id") && node.has("Children")) {
            long parentId = ArticyDatabase.parseHexId(node.get("Id").asText());
            ArticyObject parent = database.getObject(parentId, ArticyObject.class);
            
            for (JsonNode childNode : node.get("Children")) {
                long childId = ArticyDatabase.parseHexId(childNode.get("Id").asText());
                ArticyObject child = database.getObject(childId, ArticyObject.class);
                if (parent != null && child != null) {
                    parent.addChild(child);
                }
                processHierarchyNode(childNode);
            }
        }
    }
}
