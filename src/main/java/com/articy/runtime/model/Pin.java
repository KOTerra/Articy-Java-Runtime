package com.articy.runtime.model;

import java.util.ArrayList;
import java.util.List;

public class Pin extends ArticyObject {
    private String script;
    private List<Connection> connections = new ArrayList<>();
    private long ownerId;

    public String getScript() {
        return script;
    }

    public void setScript(String script) {
        this.script = script;
    }

    public List<Connection> getConnections() {
        return connections;
    }

    public long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(long ownerId) {
        this.ownerId = ownerId;
    }
}
