package com.articy.runtime.model;

import java.util.ArrayList;
import java.util.List;

public class Branch {
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
