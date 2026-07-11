package com.finanzapp.app.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.IgnoreExtraProperties;

@IgnoreExtraProperties
public class Member {
    private String uid;
    private String displayName;
    private String email;
    private String role; // "admin" | "member"
    private String status; // "approved"
    private Timestamp joinedAt;

    public Member() {
        // Required for Firestore serialization
    }

    public Member(String uid, String displayName, String email, String role, String status, Timestamp joinedAt) {
        this.uid = uid;
        this.displayName = displayName;
        this.email = email;
        this.role = role;
        this.status = status;
        this.joinedAt = joinedAt;
    }

    // Getters and Setters
    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Timestamp getJoinedAt() { return joinedAt; }
    public void setJoinedAt(Timestamp joinedAt) { this.joinedAt = joinedAt; }
}
