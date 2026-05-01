package com.articy.runtime.core;

import com.articy.runtime.logic.ArticyVariableManager;
import com.articy.runtime.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

public class ArticyLoader {
    private final ArticyDatabase database;
    private final ArticyVariableManager variableManager;
    private final ObjectMapper mapper = new ObjectMapper();

    public ArticyLoader(ArticyDatabase database, ArticyVariableManager variableManager) {
        this.database = database;
        this.variableManager = variableManager;
    }

    public void loadFromDirectory(String exportDir) throws IOException {
        File manifestFile = new File(exportDir, "manifest.json");
        JsonNode manifest = mapper.readTree(manifestFile);

        // 1. Load Global Variables
        if (manifest.has("GlobalVariables")) {
            String gvFile = manifest.get("GlobalVariables").get("FileName").asText();
            loadGlobalVariables(new File(exportDir, gvFile));
        }

        // 2. Load Packages
        if (manifest.has("Packages")) {
            for (JsonNode pkg : manifest.get("Packages")) {
                String objFile = pkg.get("Files").get("Objects").get("FileName").asText();
                loadObjects(new File(exportDir, objFile));
            }
        }

        // 3. Resolve Hierarchy
        if (manifest.has("Hierarchy")) {
            String hierarchyFile = manifest.get("Hierarchy").get("FileName").asText();
            loadHierarchy(new File(exportDir, hierarchyFile));
        }
    }

    private void loadGlobalVariables(File file) throws IOException {
        JsonNode root = mapper.readTree(file);
        for (JsonNode setNode : root.get("VariableSets")) {
            String setName = setNode.get("Name").asText();
            for (JsonNode varNode : setNode.get("Variables")) {
                String varName = varNode.get("Name").asText();
                String type = varNode.get("Type").asText();
                String valueStr = varNode.get("Value").asText();

                Object value = valueStr;
                if ("Boolean".equals(type)) value = Boolean.parseBoolean(valueStr);
                else if ("Integer".equals(type)) value = Integer.parseInt(valueStr);

                variableManager.setVariable(setName, varName, value);
            }
        }
    }

    private void loadHierarchy(File file) throws IOException {
        JsonNode root = mapper.readTree(file);
        parseHierarchyNode(root.get("Hierarchy"), 0);
    }

    private void parseHierarchyNode(JsonNode node, long parentId) {
        long id = ArticyDatabase.parseHexId(node.get("Id").asText());
        ArticyObject obj = database.getObject(id, ArticyObject.class);
        if (obj != null) {
            obj.setParentId(parentId);
        }

        if (node.has("Children")) {
            for (JsonNode child : node.get("Children")) {
                parseHierarchyNode(child, id);
            }
        }
    }

    private void loadObjects(File file) throws IOException {
        JsonNode root = mapper.readTree(file);
        for (JsonNode objNode : root.get("Objects")) {
            parseObject(objNode);
        }
    }

    private void parseObject(JsonNode node) {
        String type = node.get("Type").asText();
        JsonNode props = node.get("Properties");
        ArticyObject obj = createObjectByType(type);
        if (obj == null) return;

        obj.setId(ArticyDatabase.parseHexId(props.get("Id").asText()));
        if (props.has("TechnicalName")) obj.setTechnicalName(props.get("TechnicalName").asText());
        if (props.has("Parent")) {
            obj.setParentId(ArticyDatabase.parseHexId(props.get("Parent").asText()));
        }

        if (obj instanceof FlowObject) {
            parseFlowProperties((FlowObject) obj, props);
        }

        if (obj instanceof DialogueFragment) {
            DialogueFragment df = (DialogueFragment) obj;
            if (props.has("Speaker")) df.setSpeakerId(ArticyDatabase.parseHexId(props.get("Speaker").asText()));
            if (props.has("Text")) df.setText(props.get("Text").asText());
            if (props.has("MenuText")) df.setMenuText(props.get("MenuText").asText());
        } else if (obj instanceof Condition) {
            if (props.has("Expression")) ((Condition) obj).setExpression(props.get("Expression").asText());
        } else if (obj instanceof Instruction) {
            if (props.has("Expression")) ((Instruction) obj).setExpression(props.get("Expression").asText());
        } else if (obj instanceof Jump) {
            Jump j = (Jump) obj;
            if (props.has("Target")) j.setTargetId(ArticyDatabase.parseHexId(props.get("Target").asText()));
            if (props.has("TargetPin")) j.setTargetPinId(ArticyDatabase.parseHexId(props.get("TargetPin").asText()));
        } else if (obj instanceof AssetObject) {
            AssetObject a = (AssetObject) obj;
            if (props.has("AssetRef")) a.setAssetRef(props.get("AssetRef").asText());
            if (node.has("AssetCategory")) a.setCategory(node.get("AssetCategory").asText());
        }

        database.registerObject(obj);
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
        if (node.has("Text")) pin.setScript(node.get("Text").asText());
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
            case "Dialogue":
                return new Dialogue();
            case "FlowFragment":
                return new FlowFragment();
            case "Hub":
                return new Hub();
            case "Condition":
                return new Condition();
            case "Instruction":
                return new Instruction();
            case "Jump":
                return new Jump();
            case "Asset":
                return new AssetObject();
            default:
                return null;
        }
    }
}
