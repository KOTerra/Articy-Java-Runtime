package com.articy.runtime.model;

public class AssetObject extends ArticyObject {
    private String assetRef;
    private String category;

    public String getAssetRef() {
        return assetRef;
    }

    public void setAssetRef(String assetRef) {
        this.assetRef = assetRef;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }
}
