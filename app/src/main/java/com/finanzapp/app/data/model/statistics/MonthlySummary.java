package com.finanzapp.app.data.model.statistics;

public class MonthlySummary {
    private final String monthLabel; // e.g. "Ene", "Feb 24"
    private final double income;
    private final double expense;

    public MonthlySummary(String monthLabel, double income, double expense) {
        this.monthLabel = monthLabel;
        this.income = income;
        this.expense = expense;
    }

    public String getMonthLabel() { return monthLabel; }
    public double getIncome() { return income; }
    public double getExpense() { return expense; }
    public double getNet() { return income - expense; }
}
