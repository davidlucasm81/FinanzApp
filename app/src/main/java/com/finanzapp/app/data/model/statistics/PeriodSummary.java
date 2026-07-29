package com.finanzapp.app.data.model.statistics;

public class PeriodSummary {
    private final String label;
    private final double income;
    private final double expense;
    private final long startDateMillis;
    private final long endDateMillis;

    public PeriodSummary(String label, double income, double expense, long startDateMillis, long endDateMillis) {
        this.label = label;
        this.income = income;
        this.expense = expense;
        this.startDateMillis = startDateMillis;
        this.endDateMillis = endDateMillis;
    }

    public String getLabel() { return label; }
    public double getIncome() { return income; }
    public double getExpense() { return expense; }
    public long getStartDateMillis() { return startDateMillis; }
    public long getEndDateMillis() { return endDateMillis; }
    public double getNet() { return income - expense; }
}
