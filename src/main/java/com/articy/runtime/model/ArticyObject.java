package com.articy.runtime.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for all objects in the Articy database.
 */
public abstract class ArticyObject {
    private long id;
    private String technicalName;
    private long parentId;
    private List<ArticyObject> children = new ArrayList<>();

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getTechnicalName() {
        return technicalName;
    }

    public void setTechnicalName(String technicalName) {
        this.technicalName = technicalName;
    }

    public long getParentId() {
        return parentId;
    }

    public void setParentId(long parentId) {
        this.parentId = parentId;
    }

    public List<ArticyObject> getChildren() {
        return children;
    }

    public void addChild(ArticyObject child) {
        this.children.add(child);
    }
}
