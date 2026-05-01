package com.articy.runtime.core;

import com.articy.runtime.logic.ArticyVariableManager;
import com.articy.runtime.logic.ExpressoEngine;
import com.articy.runtime.logic.IScriptMethodProvider;
import com.articy.runtime.model.*;

import java.util.*;

/**
 * Manages the traversal of the Articy flow (dialogues, stories, etc.).
 * Handles condition evaluation, instruction execution, and branch forecasting.
 */
public class ArticyFlowPlayer {
    private final ArticyDatabase database;
    private final ExpressoEngine engine;
    private final ArticyVariableManager masterVars;
    private final IArticyFlowPlayerCallbacks callbacks;
    private final IScriptMethodProvider methodProvider;

    private Set<Class<? extends FlowObject>> pauseOnTypes = new HashSet<>();
    private FlowObject currentPausedObject;

    public ArticyFlowPlayer(ArticyDatabase database, ArticyVariableManager variableManager, 
                            ExpressoEngine engine, IScriptMethodProvider methodProvider,
                            IArticyFlowPlayerCallbacks callbacks) {
        this.database = database;
        this.masterVars = variableManager;
        this.engine = engine;
        this.methodProvider = methodProvider;
        this.callbacks = callbacks;
        
        // Default pause types: Only Dialogue Fragments
        this.pauseOnTypes.add(DialogueFragment.class);
    }

    /**
     * Defines which node types should cause the flow player to pause.
     */
    public void setPauseOn(Set<Class<? extends FlowObject>> types) {
        this.pauseOnTypes = types;
    }

    /**
     * Starts flow traversal at the specified node.
     */
    public void startOn(long id) {
        ArticyObject obj = database.getObject(id, ArticyObject.class);
        if (obj == null) {
            return;
        }
        if (obj instanceof FlowObject) {
            FlowObject flowObj = (FlowObject) obj;
            
            if (isPausable(flowObj)) {
                advanceTo(flowObj);
            } else {
                List<Branch> branches = new ArrayList<>();
                ArticyVariableManager shadowVars = masterVars.createShadowState();
                
                boolean hasInternalFlow = false;
                for (Pin inPin : flowObj.getInputPins()) {
                    if (!inPin.getConnections().isEmpty()) {
                        evaluateForecasting(inPin, shadowVars, branches, new ArrayList<>(), new HashSet<>());
                        hasInternalFlow = true;
                    }
                }
                
                if (hasInternalFlow) {
                    if (branches.size() == 1) {
                         advance(branches.get(0));
                    } else {
                         callbacks.onFlowPlayerPaused(flowObj);
                         callbacks.onBranchesUpdated(branches);
                    }
                } else {
                    advanceTo(flowObj);
                }
            }
        }
    }

    /**
     * Advances the flow along the specified branch, executing all scripts on the path.
     */
    public void advance(Branch branch) {
        for (Branch.PathItem item : branch.getPath()) {
            if (item.outputPin != null && item.outputPin.getScript() != null && !item.outputPin.getScript().isEmpty()) {
                engine.executeInstruction(item.outputPin.getScript(), masterVars, methodProvider);
            }
            if (item.inputPin != null && item.inputPin.getScript() != null && !item.inputPin.getScript().isEmpty()) {
                engine.executeInstruction(item.inputPin.getScript(), masterVars, methodProvider);
            }
            executeNodeInstructions(item.node, masterVars);
        }
        advanceTo(branch.getTargetNode());
    }

    public FlowObject getCurrentPausedObject() {
        return currentPausedObject;
    }

    public long getCurrentPausedObjectId() {
        return currentPausedObject != null ? currentPausedObject.getId() : -1L;
    }

    /**
     * Restores the flow player to a specific node state, usually for save/load.
     */
    public void restoreState(long nodeId) {
        if (nodeId == -1L) {
            currentPausedObject = null;
            return;
        }
        ArticyObject obj = database.getObject(nodeId, ArticyObject.class);
        if (obj instanceof FlowObject) {
            advanceTo((FlowObject) obj);
        }
    }

    private void advanceTo(FlowObject node) {
        currentPausedObject = node;

        if (isPausable(node)) {
            executeNodeInstructions(node, masterVars);
            callbacks.onFlowPlayerPaused(node);
            List<Branch> branches = forecastBranches(node);
            callbacks.onBranchesUpdated(branches);
        } else {
            List<Branch> branches = forecastBranches(node);
            if (branches.size() == 1) {
                advance(branches.get(0));
            } else {
                callbacks.onFlowPlayerPaused(node);
                callbacks.onBranchesUpdated(branches);
            }
        }
    }

    private List<Branch> forecastBranches(FlowObject node) {
        List<Branch> branches = new ArrayList<>();
        ArticyVariableManager shadowVars = masterVars.createShadowState();
        
        for (Pin outPin : node.getOutputPins()) {
            evaluateForecasting(outPin, shadowVars, branches, new ArrayList<>(), new HashSet<>());
        }
        return branches;
    }

    private void evaluateForecasting(Pin currentPin, ArticyVariableManager shadowVars, 
                                     List<Branch> branches, List<Branch.PathItem> currentPath, 
                                     Set<Long> visitedNodes) {
        
        if (currentPin.getScript() != null && !currentPin.getScript().isEmpty()) {
            engine.executeInstruction(currentPin.getScript(), shadowVars, methodProvider);
        }

        for (Connection conn : currentPin.getConnections()) {
            ArticyObject targetObj = database.getObject(conn.getTargetNodeId(), ArticyObject.class);
            if (!(targetObj instanceof FlowObject)) {
                continue;
            }
            
            FlowObject targetNode = (FlowObject) targetObj;
            
            Pin inputPin = null;
            for(Pin p : targetNode.getInputPins()) {
                if(p.getId() == conn.getTargetPinId()) {
                    inputPin = p;
                    break;
                }
            }
            
            if (inputPin != null && inputPin.getScript() != null && !inputPin.getScript().isEmpty()) {
                if (!engine.evaluateCondition(inputPin.getScript(), shadowVars, methodProvider)) {
                    continue;
                }
            }

            if (targetNode instanceof TransparentNode && visitedNodes.contains(targetNode.getId())) {
                continue;
            }
            
            if (targetNode instanceof TransparentNode) {
                visitedNodes.add(targetNode.getId());
            }

            if (isPausable(targetNode)) {
                List<Branch.PathItem> finalPath = new ArrayList<>(currentPath);
                finalPath.add(new Branch.PathItem(targetNode, currentPin, inputPin));
                branches.add(new Branch(targetNode, finalPath));
            } else {
                List<Branch.PathItem> nextPath = new ArrayList<>(currentPath);
                nextPath.add(new Branch.PathItem(targetNode, currentPin, inputPin));
                
                if (targetNode instanceof Jump) {
                    Jump jump = (Jump) targetNode;
                    ArticyObject jumpTarget = database.getObject(jump.getTargetId(), ArticyObject.class);
                    if (jumpTarget instanceof FlowObject) {
                        for(Pin out : ((FlowObject)jumpTarget).getOutputPins()) {
                             evaluateForecasting(out, shadowVars, branches, nextPath, visitedNodes);
                        }
                    }
                } else if (targetNode instanceof Condition) {
                    Condition cond = (Condition) targetNode;
                    boolean result = engine.evaluateCondition(cond.getExpression(), shadowVars, methodProvider);
                    int pinIndex = result ? 0 : 1;
                    if (targetNode.getOutputPins().size() > pinIndex) {
                        evaluateForecasting(targetNode.getOutputPins().get(pinIndex), shadowVars, branches, nextPath, visitedNodes);
                    }
                } else if (targetNode instanceof Instruction) {
                    Instruction instr = (Instruction) targetNode;
                    engine.executeInstruction(instr.getExpression(), shadowVars, methodProvider);
                    for (Pin out : targetNode.getOutputPins()) {
                        evaluateForecasting(out, shadowVars, branches, nextPath, visitedNodes);
                    }
                } else {
                    for (Pin out : targetNode.getOutputPins()) {
                        evaluateForecasting(out, shadowVars, branches, nextPath, visitedNodes);
                    }
                }
            }
            
            if (isPausable(targetNode)) {
                 // Stop at first pausable
            }
        }
    }

    private boolean isPausable(FlowObject node) {
        for (Class<? extends FlowObject> type : pauseOnTypes) {
            if (type.isInstance(node)) {
                return true;
            }
        }
        return false;
    }

    private void executeNodeInstructions(FlowObject node, ArticyVariableManager vars) {
        if (node instanceof Instruction) {
            engine.executeInstruction(((Instruction) node).getExpression(), vars, methodProvider);
        }
    }
}
