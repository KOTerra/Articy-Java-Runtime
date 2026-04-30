package com.articy.runtime.model;

public class Connection {
    private long targetPinId;
    private long targetNodeId;

    public Connection(long targetPinId, long targetNodeId) {
        this.targetPinId = targetPinId;
        this.targetNodeId = targetNodeId;
    }

    public long getTargetPinId() {
        return targetPinId;
    }

    public long getTargetNodeId() {
        return targetNodeId;
    }
}
