package com.articy.runtime.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a possible path in the flow from the current node to a target "pausable" node.
 */
public class Branch {
    /**
     * Represents a single step (node and pins) along a branch's path.
     */
    public static class PathItem {
        public final FlowObject node;
        public final Pin outputPin;
        public final Pin inputPin;

        public PathItem(FlowObject node, Pin outputPin, Pin inputPin) {
            this.node = node;
            this.outputPin = outputPin;
            this.inputPin = inputPin;
        }
    }

    private final FlowObject targetNode;
    private final List<PathItem> path;

    public Branch(FlowObject targetNode, List<PathItem> path) {
        this.targetNode = targetNode;
        this.path = new ArrayList<>(path);
    }

    public FlowObject getTargetNode() {
        return targetNode;
    }

    public List<PathItem> getPath() {
        return path;
    }
}
