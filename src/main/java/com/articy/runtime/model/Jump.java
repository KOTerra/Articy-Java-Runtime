package com.articy.runtime.model;

public class Jump extends TransparentNode {
    private long targetId;
    private long targetPinId;

    public long getTargetId() {
        return targetId;
    }

    public void setTargetId(long targetId) {
        this.targetId = targetId;
    }

    public long getTargetPinId() {
        return targetPinId;
    }

    public void setTargetPinId(long targetPinId) {
        this.targetPinId = targetPinId;
    }
}
