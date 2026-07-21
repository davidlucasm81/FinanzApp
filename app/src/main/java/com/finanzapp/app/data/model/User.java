package com.finanzapp.app.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.IgnoreExtraProperties;

@IgnoreExtraProperties
public class User {
    private String uid;
    private String displayName;
    private String email;
    private String photoUrl;
    private String familyId;
    private Timestamp createdAt;
    private Timestamp privacyPolicyAcceptedAt;

    public User() {
        // Required for Firestore serialization
    }

    public User(String uid, String displayName, String email, String photoUrl, String familyId, Timestamp createdAt) {
        this(uid, displayName, email, photoUrl, familyId, createdAt, null);
    }

    public User(String uid, String displayName, String email, String photoUrl, String familyId, Timestamp createdAt, Timestamp privacyPolicyAcceptedAt) {
        this.uid = uid;
        this.displayName = displayName;
        this.email = email;
        this.photoUrl = photoUrl;
        this.familyId = familyId;
        this.createdAt = createdAt;
        this.privacyPolicyAcceptedAt = privacyPolicyAcceptedAt;
    }

    // Getters and Setters
    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }

    public String getFamilyId() { return familyId; }
    public void setFamilyId(String familyId) { this.familyId = familyId; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getPrivacyPolicyAcceptedAt() { return privacyPolicyAcceptedAt; }
    public void setPrivacyPolicyAcceptedAt(Timestamp privacyPolicyAcceptedAt) { this.privacyPolicyAcceptedAt = privacyPolicyAcceptedAt; }
}
