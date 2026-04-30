package com.articy.runtime.model;

import java.util.ArrayList;
import java.util.List;

public class Branch {
    private final FlowObject targetNode;
    private final List<FlowObject> path;

    public Branch(FlowObject targetNode, List<FlowObject> path) {
        this.targetNode = targetNode;
        this.path = new ArrayList<>(path);
    }

    public FlowObject getTargetNode() {
        return targetNode;
    }

    public List<FlowObject> getPath() {
        return path;
    }
}
