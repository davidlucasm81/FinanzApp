package com.finanzapp.app.data.model.statistics;

public class MonthlySummary {
    private final String monthLabel;
    private final double income;
    private final double expense;
    private final long startDateMillis;
    private final long endDateMillis;

    public MonthlySummary(String monthLabel, double income, double expense, long startDateMillis, long endDateMillis) {
        this.monthLabel = monthLabel;
        this.income = income;
        this.expense = expense;
        this.startDateMillis = startDateMillis;
        this.endDateMillis = endDateMillis;
    }

    public String getMonthLabel() { return monthLabel; }
    public double getIncome() { return income; }
    public double getExpense() { return expense; }
    public long getStartDateMillis() { return startDateMillis; }
    public long getEndDateMillis() { return endDateMillis; }
    public double getNet() { return income - expense; }
}
