package com.finanzapp.app.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.IgnoreExtraProperties;

@IgnoreExtraProperties
public class Invitation {
    private String id;
    private String type; // "email_invite" | "code_request"
    private String targetEmail;
    private String requestedByUid;
    private String requesterName;
    private String requesterEmail;
    private String invitedByUid;
    private String status; // "pending" | "accepted" | "approved" | "rejected"
    private Timestamp createdAt;
    private Timestamp resolvedAt;
    private String resolvedByUid;

    public Invitation() {
        // Required for Firestore serialization
    }

    public Invitation(String id, String type, String targetEmail, String requestedByUid, String invitedByUid, String status, Timestamp createdAt) {
        this.id = id;
        this.type = type;
        this.targetEmail = targetEmail;
        this.requestedByUid = requestedByUid;
        this.invitedByUid = invitedByUid;
        this.status = status;
        this.createdAt = createdAt;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getTargetEmail() { return targetEmail; }
    public void setTargetEmail(String targetEmail) { this.targetEmail = targetEmail; }

    public String getRequestedByUid() { return requestedByUid; }
    public void setRequestedByUid(String requestedByUid) { this.requestedByUid = requestedByUid; }

    public String getRequesterName() { return requesterName; }
    public void setRequesterName(String requesterName) { this.requesterName = requesterName; }

    public String getRequesterEmail() { return requesterEmail; }
    public void setRequesterEmail(String requesterEmail) { this.requesterEmail = requesterEmail; }

    public String getInvitedByUid() { return invitedByUid; }
    public void setInvitedByUid(String invitedByUid) { this.invitedByUid = invitedByUid; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Timestamp resolvedAt) { this.resolvedAt = resolvedAt; }

    public String getResolvedByUid() { return resolvedByUid; }
    public void setResolvedByUid(String resolvedByUid) { this.resolvedByUid = resolvedByUid; }
}
