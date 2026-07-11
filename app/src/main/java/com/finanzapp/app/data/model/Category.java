package com.finanzapp.app.data.model;

import com.google.firebase.firestore.IgnoreExtraProperties;

@IgnoreExtraProperties
public class Category {
    private String id;
    private String name;
    private String appliesTo; // "income" | "expense" | "both"
    private String icon;
    private String color;
    private boolean isDefault;
    private String createdBy; // null if isDefault is true

    public Category() {
        // Required for Firestore serialization
    }

    public Category(String id, String name, String appliesTo, String icon, String color, boolean isDefault, String createdBy) {
        this.id = id;
        this.name = name;
        this.appliesTo = appliesTo;
        this.icon = icon;
        this.color = color;
        this.isDefault = isDefault;
        this.createdBy = createdBy;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAppliesTo() { return appliesTo; }
    public void setAppliesTo(String appliesTo) { this.appliesTo = appliesTo; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean aDefault) { isDefault = aDefault; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
