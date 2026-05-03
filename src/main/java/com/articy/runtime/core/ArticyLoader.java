package com.articy.runtime.core;

import com.articy.runtime.logic.ArticyVariableManager;
import com.articy.runtime.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Handles parsing and loading of Articy JSON export files.
 * Populates the database with objects and sets up global variables.
 */
public class ArticyLoader {
    private final ArticyDatabase database;
    private final ArticyVariableManager variableManager;
    private final ObjectMapper mapper = new ObjectMapper();

    public ArticyLoader(ArticyDatabase database, ArticyVariableManager variableManager) {
        this.database = database;
        this.variableManager = variableManager;
    }

    /**
     * Loads all Articy data from the specified directory.
     * Parses manifest.json to find and load packages, variables, and hierarchy.
     */
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

    /**
     * Loads Articy data directly from a .articygen (ZIP) archive.
     */
    public void loadFromArchive(File archiveFile) throws IOException {
        try (ZipFile zip = new ZipFile(archiveFile)) {
            ZipEntry manifestEntry = zip.getEntry("manifest.json");
            if (manifestEntry == null) {
                throw new IOException("Archive is missing manifest.json");
            }

            JsonNode manifest;
            try (InputStream is = zip.getInputStream(manifestEntry)) {
                manifest = mapper.readTree(is);
            }

            // 1. Load Global Variables
            if (manifest.has("GlobalVariables")) {
                String gvFile = manifest.get("GlobalVariables").get("FileName").asText();
                ZipEntry entry = zip.getEntry(gvFile);
                if (entry != null) {
                    try (InputStream is = zip.getInputStream(entry)) {
                        loadGlobalVariables(mapper.readTree(is));
                    }
                }
            }

            // 2. Load Packages
            if (manifest.has("Packages")) {
                for (JsonNode pkg : manifest.get("Packages")) {
                    String objFile = pkg.get("Files").get("Objects").get("FileName").asText();
                    ZipEntry entry = zip.getEntry(objFile);
                    if (entry != null) {
                        try (InputStream is = zip.getInputStream(entry)) {
                            loadObjects(mapper.readTree(is));
                        }
                    }
                }
            }

            // 3. Resolve Hierarchy
            if (manifest.has("Hierarchy")) {
                String hierarchyFile = manifest.get("Hierarchy").get("FileName").asText();
                ZipEntry entry = zip.getEntry(hierarchyFile);
                if (entry != null) {
                    try (InputStream is = zip.getInputStream(entry)) {
                        loadHierarchy(mapper.readTree(is));
                    }
                }
            }
        }
    }

    private void loadGlobalVariables(File file) throws IOException {
        loadGlobalVariables(mapper.readTree(file));
    }

    private void loadGlobalVariables(JsonNode root) {
        if (root.has("GlobalVariables")) {
            for (JsonNode setNode : root.get("GlobalVariables")) {
                String setName = setNode.get("Namespace").asText();
                for (JsonNode varNode : setNode.get("Variables")) {
                    String varName = varNode.get("Variable").asText();
                    String type = varNode.get("Type").asText();
                    String valueStr = varNode.get("Value").asText();

                    Object value = valueStr;
                    if ("Boolean".equals(type)) value = Boolean.parseBoolean(valueStr);
                    else if ("Integer".equals(type)) value = Integer.parseInt(valueStr);

                    variableManager.setVariable(setName, varName, value);
                }
            }
        } else if (root.has("VariableSets")) {
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
    }

    private void loadHierarchy(File file) throws IOException {
        loadHierarchy(mapper.readTree(file));
    }

    private void loadHierarchy(JsonNode root) {
        if (root.has("Hierarchy")) {
            parseHierarchyNode(root.get("Hierarchy"), 0);
        } else {
            parseHierarchyNode(root, 0);
        }
    }

    private void parseHierarchyNode(JsonNode node, long parentId) {
        if (node == null) return;
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
        loadObjects(mapper.readTree(file));
    }

    private void loadObjects(JsonNode root) {
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
        // 1. Check dynamic registry first (No hardcoded game types!)
        Map<String, Class<? extends ArticyObject>> registry = ArticyRuntime.getCustomTypeRegistry();
        if (registry.containsKey(type)) {
            try {
                return registry.get(type).getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                System.err.println("ArticyLoader: Failed to instantiate custom type: " + type);
                return null;
            }
        }

        // 2. Core Articy Flow Objects
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

            // 3. Known Structural/Editor Metadata Types
            // We explicitly return null here so they aren't instantiated as Entities.
            // This prevents test failures (e.g., Dracula/ManicManfred tests) and DB pollution.
            case "Project":
            case "UserFolder":
            case "Comment":
            case "Feature":
            case "Template":
            case "VariableSet":
            case "Entities":
            case "EntitiesUserFolder":
            case "Locations":
            case "LocationText":
            case "LocationImage":
            case "Documents":
            case "Journeys":
            case "TemplateDesign":
            case "Features":
            case "PropertyTemplates":
            case "TypedPropertyTemplates":
            case "Templates":
            case "TemplateTypeFolder":
            case "RuleSets":
            case "RuleSet":
            case "RuleSetPackage":
            case "Assets":
            case "AssetsUserFolder":
            case "ProjectSettingsFolder":
            case "ProjectSettingsFlow":
            case "ProjectSettingsGeneral":
            case "ProjectSettingsJourneys":
            case "ProjectSettingsLocation":
            case "GlobalVariables":
            case "Flow":
                return null;

            // 4. THE TRUE CATCH-ALL
            // Any type not caught above is assumed to be a custom game template or entity.
            default:
                return new Entity();
        }
    }
}
