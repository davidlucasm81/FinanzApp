package com.finanzapp.app.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.IgnoreExtraProperties;

@IgnoreExtraProperties
public class FamilyMembership {
    private String familyId;
    private String familyName;
    private String role;
    private Timestamp joinedAt;

    public FamilyMembership() {
        // Required for Firestore serialization
    }

    public FamilyMembership(String familyId, String familyName, String role, Timestamp joinedAt) {
        this.familyId = familyId;
        this.familyName = familyName;
        this.role = role;
        this.joinedAt = joinedAt;
    }

    // Getters and Setters
    public String getFamilyId() { return familyId; }
    public void setFamilyId(String familyId) { this.familyId = familyId; }

    public String getFamilyName() { return familyName; }
    public void setFamilyName(String familyName) { this.familyName = familyName; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public Timestamp getJoinedAt() { return joinedAt; }
    public void setJoinedAt(Timestamp joinedAt) { this.joinedAt = joinedAt; }
}
