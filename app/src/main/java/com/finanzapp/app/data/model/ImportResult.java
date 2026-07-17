package com.finanzapp.app.data.model;

import java.util.ArrayList;
import java.util.List;

public class ImportResult {
    private int importedCount;
    private int newAccountsCount;
    private int newCategoriesCount;
    private final List<String> errors = new ArrayList<>();

    public ImportResult() {}

    public int getImportedCount() { return importedCount; }
    public void setImportedCount(int importedCount) { this.importedCount = importedCount; }

    public int getNewAccountsCount() { return newAccountsCount; }
    public void setNewAccountsCount(int newAccountsCount) { this.newAccountsCount = newAccountsCount; }

    public int getNewCategoriesCount() { return newCategoriesCount; }
    public void setNewCategoriesCount(int newCategoriesCount) { this.newCategoriesCount = newCategoriesCount; }

    public List<String> getErrors() { return errors; }
    public void addError(String error) { this.errors.add(error); }
}
