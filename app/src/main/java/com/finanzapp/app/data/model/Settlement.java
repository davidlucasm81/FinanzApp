package com.finanzapp.app.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.IgnoreExtraProperties;

@IgnoreExtraProperties
public class Settlement {
    private String id;
    private String fromUid;
    private String toUid;
    private double amount;
    private String note;
    private String createdBy;
    private Timestamp createdAt;

    public Settlement() {
        // Required for Firestore
    }

    public Settlement(String id, String fromUid, String toUid, double amount, String note, String createdBy, Timestamp createdAt) {
        this.id = id;
        this.fromUid = fromUid;
        this.toUid = toUid;
        this.amount = amount;
        this.note = note;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFromUid() { return fromUid; }
    public void setFromUid(String fromUid) { this.fromUid = fromUid; }

    public String getToUid() { return toUid; }
    public void setToUid(String toUid) { this.toUid = toUid; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
