package com.finanzapp.app.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.IgnoreExtraProperties;

@IgnoreExtraProperties
public class Account {
    private String id;
    private String name;
    private double initialBalance;
    private double currentBalance;
    private boolean active;
    private String createdBy;
    private Timestamp createdAt;

    // Campo calculado en el cliente (no se persiste en Firestore): indica si la cuenta
    // tiene al menos un movimiento asociado. Lo calcula AccountRepository.getAccounts()
    // cruzando con la colección de transactions, para que la UI sepa si debe permitir
    // borrar (sin movimientos) o solo archivar (con movimientos).
    private transient boolean hasTransactions = false;

    public Account() {
        // Required for Firestore
    }

    public Account(String id, String name, double initialBalance, double currentBalance, boolean active, String createdBy, Timestamp createdAt) {
        this.id = id;
        this.name = name;
        this.initialBalance = initialBalance;
        this.currentBalance = currentBalance;
        this.active = active;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public double getInitialBalance() { return initialBalance; }
    public void setInitialBalance(double initialBalance) { this.initialBalance = initialBalance; }

    public double getCurrentBalance() { return currentBalance; }
    public void setCurrentBalance(double currentBalance) { this.currentBalance = currentBalance; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    // @Exclude es imprescindible: sin él, Firestore incluiría este getter al serializar
    // el objeto en createAccount()/updateAccount() y escribiría un campo "hasTransactions"
    // inexistente/incorrecto en el documento.
    @Exclude
    public boolean isHasTransactions() { return hasTransactions; }
    public void setHasTransactions(boolean hasTransactions) { this.hasTransactions = hasTransactions; }
}