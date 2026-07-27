package com.finanzapp.app.data.model;

public class PaymentMethodSummary {
    private final String methodId;
    private final String label;
    private final double amount;
    private final double percentage;

    public PaymentMethodSummary(String methodId, String label, double amount, double percentage) {
        this.methodId = methodId;
        this.label = label;
        this.amount = amount;
        this.percentage = percentage;
    }

    public String getMethodId() {
        return methodId;
    }

    public String getLabel() {
        return label;
    }

    public double getAmount() {
        return amount;
    }

    public double getPercentage() {
        return percentage;
    }
}
