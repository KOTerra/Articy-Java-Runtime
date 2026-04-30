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
        
        // Default pause on DialogueFragment
        this.pauseOnTypes.add(DialogueFragment.class);
    }

    public void setPauseOn(Set<Class<? extends FlowObject>> types) {
        this.pauseOnTypes = types;
    }

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
                boolean followedInput = false;
                for (Pin inPin : flowObj.getInputPins()) {
                    if (!inPin.getConnections().isEmpty()) {
                        ArticyVariableManager shadowVars = masterVars.createShadowState();
                        List<Branch> branches = new ArrayList<>();
                        evaluateForecasting(inPin, shadowVars, branches, new ArrayList<>(), new HashSet<>());
                        if (!branches.isEmpty()) {
                            callbacks.onBranchesUpdated(branches);
                            followedInput = true;
                            break; 
                        }
                    }
                }
                if (!followedInput) {
                    advanceTo(flowObj);
                }
            }
        }
    }

    public void advance(Branch branch) {
        // Replay the path on master state to commit side effects
        ArticyVariableManager shadowVars = masterVars.createShadowState(); // Temporary shadow to check conditions if needed, though branch is already validated
        
        // We actually want to replay on masterVars directly
        for (FlowObject node : branch.getPath()) {
            executeNodeInstructions(node, masterVars);
            // We also need to execute pin instructions if we could identify which pins were traversed.
            // The branch path currently only stores nodes. 
            // For full fidelity, the Branch should probably store the sequence of Pins or we re-traverse.
        }
        
        // Final move to the target node
        advanceTo(branch.getTargetNode());
    }

    public FlowObject getCurrentPausedObject() {
        return currentPausedObject;
    }

    private void advanceTo(FlowObject node) {
        currentPausedObject = node;
        callbacks.onFlowPlayerPaused(node);
        
        List<Branch> branches = forecastBranches(node);
        callbacks.onBranchesUpdated(branches);
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
                                     List<Branch> branches, List<FlowObject> currentPath, 
                                     Set<Long> visitedNodes) {
        
        // Execute pin instructions
        if (currentPin.getScript() != null && !currentPin.getScript().isEmpty()) {
            engine.executeInstruction(currentPin.getScript(), shadowVars, methodProvider);
        }

        for (Connection conn : currentPin.getConnections()) {
            ArticyObject targetObj = database.getObject(conn.getTargetNodeId(), ArticyObject.class);
            if (!(targetObj instanceof FlowObject)) {
                continue;
            }
            
            FlowObject targetNode = (FlowObject) targetObj;
            
            // Check target pin condition
            // In Articy, connections go to input pins.
            // We should find the input pin and check its script.
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

            // Cycle detection
            if (targetNode instanceof TransparentNode && visitedNodes.contains(targetNode.getId())) {
                continue;
            }
            
            if (targetNode instanceof TransparentNode) {
                visitedNodes.add(targetNode.getId());
            }

            if (isPausable(targetNode)) {
                System.out.println("FOUND PAUSABLE: " + targetNode.getTechnicalName() + " ID: " + Long.toHexString(targetNode.getId()));
                branches.add(new Branch(targetNode, currentPath));
            } else {
                List<FlowObject> nextPath = new ArrayList<>(currentPath);
                nextPath.add(targetNode);
                
                if (targetNode instanceof Jump) {
                    Jump jump = (Jump) targetNode;
                    ArticyObject jumpTarget = database.getObject(jump.getTargetId(), ArticyObject.class);
                    if (jumpTarget instanceof FlowObject) {
                        // Jump directly to output pins of target? 
                        // Usually Jumps target a node, we enter it via input pin or skip to output.
                        // Spec: "teleports execution to the target node"
                        // For simplicity, we start forecasting from that node's output pins 
                        // but we need to check the target pin if specified.
                        // Actually, let's just recurse into the target node.
                        for(Pin out : ((FlowObject)jumpTarget).getOutputPins()) {
                             evaluateForecasting(out, shadowVars, branches, nextPath, visitedNodes);
                        }
                    }
                } else if (targetNode instanceof Condition) {
                    Condition cond = (Condition) targetNode;
                    boolean result = engine.evaluateCondition(cond.getExpression(), shadowVars, methodProvider);
                    // Index 0 is True, Index 1 is False
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
                    // Hub, Dialogue, FlowFragment
                    for (Pin out : targetNode.getOutputPins()) {
                        evaluateForecasting(out, shadowVars, branches, nextPath, visitedNodes);
                    }
                }
            }
        }
    }

    private boolean isPausable(FlowObject node) {
        for (Class<? extends FlowObject> type : pauseOnTypes) {
            if (type.isInstance(node)) {
                System.out.println("isPausable TRUE for " + node.getTechnicalName() + " (matches " + type.getSimpleName() + ")");
                return true;
            }
        }
        return false;
    }

    private void executeNodeInstructions(FlowObject node, ArticyVariableManager vars) {
        if (node instanceof Instruction) {
            engine.executeInstruction(((Instruction) node).getExpression(), vars, methodProvider);
        }
        // Also pins?
    }
}
