package com.finanzapp.app.data.model;

public class MemberSummary {
    private final String uid;
    private final String displayName;
    private final double amount;
    private final double percentage;

    public MemberSummary(String uid, String displayName, double amount, double percentage) {
        this.uid = uid;
        this.displayName = displayName;
        this.amount = amount;
        this.percentage = percentage;
    }

    public String getUid() {
        return uid;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getAmount() {
        return amount;
    }

    public double getPercentage() {
        return percentage;
    }
}
