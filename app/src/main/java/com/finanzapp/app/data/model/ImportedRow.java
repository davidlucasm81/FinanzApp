package com.finanzapp.app.data.model;

import com.google.firebase.Timestamp;

public class ImportedRow {
    private Timestamp date;
    private String description;
    private String categoryName;
    private double amount;
    private String type; // "income" | "expense"
    private String paymentMethod;
    private String accountName;
    private int lineNumber;

    public ImportedRow() {}

    public ImportedRow(Timestamp date, String description, String categoryName, double amount, 
                       String type, String paymentMethod, String accountName, int lineNumber) {
        this.date = date;
        this.description = description;
        this.categoryName = categoryName;
        this.amount = amount;
        this.type = type;
        this.paymentMethod = paymentMethod;
        this.accountName = accountName;
        this.lineNumber = lineNumber;
    }

    public Timestamp getDate() { return date; }
    public void setDate(Timestamp date) { this.date = date; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }

    public int getLineNumber() { return lineNumber; }
    public void setLineNumber(int lineNumber) { this.lineNumber = lineNumber; }
}
