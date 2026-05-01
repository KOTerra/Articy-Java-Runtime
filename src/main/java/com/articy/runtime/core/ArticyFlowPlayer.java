package com.articy.runtime.core;

import com.articy.runtime.logic.ArticyVariableManager;
import com.articy.runtime.logic.ExpressoEngine;
import com.articy.runtime.logic.IScriptMethodProvider;
import com.articy.runtime.model.*;

import java.util.*;

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
        
        // Default pause types
        this.pauseOnTypes.add(DialogueFragment.class);
    }

    public void setPauseOn(Set<Class<? extends FlowObject>> types) {
        this.pauseOnTypes = types;
    }

    public void startOn(long id) {
        ArticyObject obj = database.getObject(id, ArticyObject.class);
        if (obj == null) {
            System.err.println("Articy: startOn failed, object not found: " + Long.toHexString(id));
            return;
        }
        if (obj instanceof FlowObject) {
            FlowObject flowObj = (FlowObject) obj;
            System.out.println("Articy: Starting on " + flowObj.getTechnicalName() + " (" + flowObj.getClass().getSimpleName() + ")");
            
            if (isPausable(flowObj)) {
                advanceTo(flowObj);
            } else {
                // If it's a container (not pausable), we should check its InputPins for internal flow.
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
                         System.out.println("Articy: startOn auto-advancing through internal flow of: " + flowObj.getTechnicalName());
                         advance(branches.get(0));
                    } else {
                         // Multiple choices from entrance or no valid path found (branches might be empty if conditions failed)
                         callbacks.onFlowPlayerPaused(flowObj);
                         callbacks.onBranchesUpdated(branches);
                    }
                } else {
                    // No internal flow defined, treat as a normal node and look at output pins
                    advanceTo(flowObj);
                }
            }
        }
    }

    public void advance(Branch branch) {
        System.out.println("Articy: Advancing to branch " + branch.getTargetNode().getTechnicalName());
        // Replay the path on master state to commit side effects
        for (Branch.PathItem item : branch.getPath()) {
            // 1. Output Pin of previous node
            if (item.outputPin != null && item.outputPin.getScript() != null && !item.outputPin.getScript().isEmpty()) {
                System.out.println("Articy Advance: Executing output pin script: " + item.outputPin.getScript());
                engine.executeInstruction(item.outputPin.getScript(), masterVars, methodProvider);
            }
            // 2. Input Pin of current node
            if (item.inputPin != null && item.inputPin.getScript() != null && !item.inputPin.getScript().isEmpty()) {
                System.out.println("Articy Advance: Executing input pin script: " + item.inputPin.getScript());
                engine.executeInstruction(item.inputPin.getScript(), masterVars, methodProvider);
            }
            // 3. The Node itself
            executeNodeInstructions(item.node, masterVars);
        }
        
        // Final move to the target node
        advanceTo(branch.getTargetNode());
    }

    public FlowObject getCurrentPausedObject() {
        return currentPausedObject;
    }

    public long getCurrentPausedObjectId() {
        return currentPausedObject != null ? currentPausedObject.getId() : -1L;
    }

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
        System.out.println("Articy: Pausing on " + node.getTechnicalName() + " (" + node.getClass().getSimpleName() + ")");

        if (isPausable(node)) {
            executeNodeInstructions(node, masterVars);
            callbacks.onFlowPlayerPaused(node);
            List<Branch> branches = forecastBranches(node);
            callbacks.onBranchesUpdated(branches);
        } else {
            // Auto-advance if there's only one branch
            List<Branch> branches = forecastBranches(node);
            if (branches.size() == 1) {
                System.out.println("Articy: Auto-advancing through non-pausable node: " + node.getTechnicalName());
                advance(branches.get(0));
            } else {
                // Multiple choices or end of flow, must pause anyway
                callbacks.onFlowPlayerPaused(node);
                callbacks.onBranchesUpdated(branches);
            }
        }
    }

    private List<Branch> forecastBranches(FlowObject node) {
        List<Branch> branches = new ArrayList<>();
        ArticyVariableManager shadowVars = masterVars.createShadowState();
        
        System.out.println("Articy: Forecasting branches for " + node.getTechnicalName() + ". Output pin count: " + node.getOutputPins().size());
        for (Pin outPin : node.getOutputPins()) {
            evaluateForecasting(outPin, shadowVars, branches, new ArrayList<>(), new HashSet<>());
        }
        System.out.println("Articy: Forecast finished. Branches found: " + branches.size());
        return branches;
    }

    private void evaluateForecasting(Pin currentPin, ArticyVariableManager shadowVars, 
                                     List<Branch> branches, List<Branch.PathItem> currentPath, 
                                     Set<Long> visitedNodes) {
        
        // Execute pin instructions
        if (currentPin.getScript() != null && !currentPin.getScript().isEmpty()) {
            System.out.println("Articy Forecast: Executing pin script: " + currentPin.getScript());
            engine.executeInstruction(currentPin.getScript(), shadowVars, methodProvider);
        }

        System.out.println("Articy Forecast: Checking " + currentPin.getConnections().size() + " connections from pin " + Long.toHexString(currentPin.getId()));
        for (Connection conn : currentPin.getConnections()) {
            ArticyObject targetObj = database.getObject(conn.getTargetNodeId(), ArticyObject.class);
            if (!(targetObj instanceof FlowObject)) {
                continue;
            }
            
            FlowObject targetNode = (FlowObject) targetObj;
            System.out.println("Articy Forecast: Reached " + targetNode.getTechnicalName() + " (" + targetNode.getClass().getSimpleName() + ")");
            
            // Check target pin condition
            Pin inputPin = null;
            for(Pin p : targetNode.getInputPins()) {
                if(p.getId() == conn.getTargetPinId()) {
                    inputPin = p;
                    break;
                }
            }
            
            if (inputPin != null && inputPin.getScript() != null && !inputPin.getScript().isEmpty()) {
                if (!engine.evaluateCondition(inputPin.getScript(), shadowVars, methodProvider)) {
                    System.out.println("Articy Forecast: Condition FAILED for " + targetNode.getTechnicalName() + ": " + inputPin.getScript());
                    continue;
                }
                System.out.println("Articy Forecast: Condition PASSED for " + targetNode.getTechnicalName());
            }

            // Cycle detection
            if (targetNode instanceof TransparentNode && visitedNodes.contains(targetNode.getId())) {
                continue;
            }
            
            if (targetNode instanceof TransparentNode) {
                visitedNodes.add(targetNode.getId());
            }

            if (isPausable(targetNode)) {
                System.out.println("Articy Forecast: ADDING BRANCH: " + targetNode.getTechnicalName());
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
                    // Hub, Dialogue, FlowFragment, DialogueFragment (when not pausable)
                    for (Pin out : targetNode.getOutputPins()) {
                        evaluateForecasting(out, shadowVars, branches, nextPath, visitedNodes);
                    }
                }
            }
            
            // Always recurse through output pins if it is pausable to find what's AFTER the pause
            if (isPausable(targetNode)) {
                 // For the case of branches, we've already added the branch. 
                 // We don't recurse further here because we want to stop at the first pausable.
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
            System.out.println("Articy Execute: Instruction " + ((Instruction) node).getExpression());
            engine.executeInstruction(((Instruction) node).getExpression(), vars, methodProvider);
        }
    }
}
