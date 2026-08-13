package com.finanzapp.app.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.IgnoreExtraProperties;

import java.io.Serializable;

@IgnoreExtraProperties
public class Transaction implements Serializable {
    private String id;
    private String accountId;
    private Timestamp date;
    private String description;
    private double amount;
    private String type; // "income" | "expense"
    private String categoryId;
    private String paymentMethod;
    private String createdBy;
    private Timestamp createdAt;

    // Shared Expenses fields
    private String paidByUid;
    private java.util.List<String> splitAmongUids;
    private String splitMode; // "equal" | "custom"
    private java.util.Map<String, Double> splitAmounts;

    public Transaction() {
        // Required for Firestore
    }

    public Transaction(String id, String accountId, Timestamp date, String description, double amount, 
                       String type, String categoryId, String paymentMethod, String createdBy, Timestamp createdAt) {
        this.id = id;
        this.accountId = accountId;
        this.date = date;
        this.description = description;
        this.amount = amount;
        this.type = type;
        this.categoryId = categoryId;
        this.paymentMethod = paymentMethod;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public Timestamp getDate() { return date; }
    public void setDate(Timestamp date) { this.date = date; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public String getPaidByUid() {
        return paidByUid != null ? paidByUid : createdBy;
    }

    public void setPaidByUid(String paidByUid) {
        this.paidByUid = paidByUid;
    }

    public java.util.List<String> getSplitAmongUids() {
        return splitAmongUids;
    }

    public void setSplitAmongUids(java.util.List<String> splitAmongUids) {
        this.splitAmongUids = splitAmongUids;
    }

    public String getSplitMode() {
        return splitMode;
    }

    public void setSplitMode(String splitMode) {
        this.splitMode = splitMode;
    }

    public java.util.Map<String, Double> getSplitAmounts() {
        return splitAmounts;
    }

    public void setSplitAmounts(java.util.Map<String, Double> splitAmounts) {
        this.splitAmounts = splitAmounts;
    }
}
