package com.finanzapp.app.data.importer;

import com.finanzapp.app.data.firebase.FirestorePaths;
import com.finanzapp.app.data.model.Account;
import com.finanzapp.app.data.model.Category;
import com.finanzapp.app.data.model.ImportResult;
import com.finanzapp.app.data.model.ImportedRow;
import com.finanzapp.app.data.model.Transaction;
import com.finanzapp.app.util.CategoryColorPalette;
import com.finanzapp.app.util.Result;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TransactionImportRepository {
    private final FirebaseFirestore db;
    private final String uid;

    public TransactionImportRepository() {
        this.db = FirebaseFirestore.getInstance();
        this.uid = FirebaseAuth.getInstance().getUid();
    }

    public interface Callback {
        void onResult(Result<ImportResult> result);
    }

    public void importTransactions(String familyId, List<ImportedRow> rows, Callback callback) {
        if (uid == null) {
            callback.onResult(new Result.Error<>(new Exception("User not authenticated")));
            return;
        }

        ImportResult result = new ImportResult();
        if (rows.isEmpty()) {
            callback.onResult(new Result.Success<>(result));
            return;
        }

        // 1. Fetch existing accounts and categories to resolve names
        Task<List<Account>> accountsTask = fetchAccounts(familyId);
        Task<List<Category>> categoriesTask = fetchCategories(familyId);

        Tasks.whenAllSuccess(accountsTask, categoriesTask).addOnSuccessListener(tasks -> {
            List<Account> existingAccounts = (List<Account>) tasks.get(0);
            List<Category> existingCategories = (List<Category>) tasks.get(1);

            processImport(familyId, rows, existingAccounts, existingCategories, result, callback);
        }).addOnFailureListener(e -> callback.onResult(new Result.Error<>(e)));
    }

    private Task<List<Account>> fetchAccounts(String familyId) {
        return db.collection(FirestorePaths.getAccountsPath(familyId)).get().continueWith(task -> {
            List<Account> accounts = new ArrayList<>();
            for (QueryDocumentSnapshot doc : task.getResult()) {
                accounts.add(doc.toObject(Account.class));
            }
            return accounts;
        });
    }

    private Task<List<Category>> fetchCategories(String familyId) {
        return db.collection(FirestorePaths.getCategoriesPath(familyId)).get().continueWith(task -> {
            List<Category> categories = new ArrayList<>();
            for (QueryDocumentSnapshot doc : task.getResult()) {
                categories.add(doc.toObject(Category.class));
            }
            return categories;
        });
    }

    private void processImport(String familyId, List<ImportedRow> rows, List<Account> existingAccounts, 
                               List<Category> existingCategories, ImportResult result, Callback callback) {
        
        Map<String, Account> accountsByName = new HashMap<>();
        for (Account a : existingAccounts) accountsByName.put(a.getName().toLowerCase(), a);

        Map<String, Category> categoriesByNameAndType = new HashMap<>();
        for (Category c : existingCategories) {
            categoriesByNameAndType.put(getCategoryKey(c.getName(), c.getAppliesTo()), c);
        }

        List<WriteBatch> batches = new ArrayList<>();
        batches.add(db.batch());
        int[] operationCount = {0};
        int currentBatchIndex = 0;

        Map<String, Double> accountDeltas = new HashMap<>();
        Map<String, Double> initialBalanceDeltas = new HashMap<>();
        Timestamp now = Timestamp.now();

        for (ImportedRow row : rows) {
            // Resolve Account
            Account account = accountsByName.get(row.getAccountName().toLowerCase());
            if (account == null) {
                account = new Account();
                DocumentReference accRef = db.collection(FirestorePaths.getAccountsPath(familyId)).document();
                account.setId(accRef.getId());
                account.setName(row.getAccountName());
                account.setInitialBalance(0);
                account.setCurrentBalance(0);
                account.setActive(true);
                account.setCreatedBy(uid);
                account.setCreatedAt(now);
                
                batches.get(currentBatchIndex).set(accRef, account);
                accountsByName.put(row.getAccountName().toLowerCase(), account);
                result.setNewAccountsCount(result.getNewAccountsCount() + 1);
                incrementOperation(batches, operationCount, db);
            }

            double delta = "income".equals(row.getType()) ? row.getAmount() : -row.getAmount();

            // Special handling for "Ahorros" category
            if (row.getCategoryName().equalsIgnoreCase("Ahorros")) {
                Double currentInitialDelta = initialBalanceDeltas.get(account.getId());
                initialBalanceDeltas.put(account.getId(), (currentInitialDelta != null ? currentInitialDelta : 0.0) + delta);
                
                Double currentAccDelta = accountDeltas.get(account.getId());
                accountDeltas.put(account.getId(), (currentAccDelta != null ? currentAccDelta : 0.0) + delta);

                result.setImportedCount(result.getImportedCount() + 1);
                continue;
            }

            // Resolve Category
            String categoryKey = getCategoryKey(row.getCategoryName(), row.getType());
            Category category = categoriesByNameAndType.get(categoryKey);
            if (category == null) {
                // Check if it exists with "both"
                category = categoriesByNameAndType.get(getCategoryKey(row.getCategoryName(), "both"));
            }

            if (category == null) {
                category = new Category();
                DocumentReference catRef = db.collection(FirestorePaths.getCategoriesPath(familyId)).document();
                category.setId(catRef.getId());
                category.setName(row.getCategoryName());
                category.setAppliesTo(row.getType());
                category.setColor(CategoryColorPalette.getColorForCategory(row.getCategoryName()));
                category.setDefault(false);
                category.setCreatedBy(uid);
                
                batches.get(batches.size() - 1).set(catRef, category);
                categoriesByNameAndType.put(categoryKey, category);
                result.setNewCategoriesCount(result.getNewCategoriesCount() + 1);
                incrementOperation(batches, operationCount, db);
            }

            // Create Transaction
            Transaction transaction = new Transaction();
            DocumentReference transRef = db.collection(FirestorePaths.getFamilyPath(familyId) + "/" + FirestorePaths.TRANSACTIONS).document();
            transaction.setId(transRef.getId());
            transaction.setAccountId(account.getId());
            transaction.setDate(row.getDate());
            transaction.setDescription(row.getDescription());
            transaction.setAmount(row.getAmount());
            transaction.setType(row.getType());
            transaction.setCategoryId(category.getId());
            transaction.setPaymentMethod(row.getPaymentMethod());
            transaction.setCreatedBy(uid);
            transaction.setCreatedAt(now);

            batches.get(batches.size() - 1).set(transRef, transaction);
            result.setImportedCount(result.getImportedCount() + 1);
            incrementOperation(batches, operationCount, db);

            // Accumulate delta for account current balance
            accountDeltas.put(account.getId(), accountDeltas.getOrDefault(account.getId(), 0.0) + delta);
        }

        // Add balance increments to batches
        for (Map.Entry<String, Double> entry : accountDeltas.entrySet()) {
            DocumentReference accRef = db.collection(FirestorePaths.getAccountsPath(familyId)).document(entry.getKey());
            batches.get(batches.size() - 1).update(accRef, "currentBalance", FieldValue.increment(entry.getValue()));
            
            // If there's an initial balance delta, update it too
            Double initialDelta = initialBalanceDeltas.get(entry.getKey());
            if (initialDelta != null) {
                batches.get(batches.size() - 1).update(accRef, "initialBalance", FieldValue.increment(initialDelta));
            }

            incrementOperation(batches, operationCount, db);
        }

        // Commit all batches
        List<Task<Void>> commitTasks = new ArrayList<>();
        for (WriteBatch batch : batches) {
            commitTasks.add(batch.commit());
        }

        Tasks.whenAll(commitTasks).addOnSuccessListener(aVoid -> {
            callback.onResult(new Result.Success<>(result));
        }).addOnFailureListener(e -> callback.onResult(new Result.Error<>(e)));
    }

    private String getCategoryKey(String name, String type) {
        return (name + "|" + type).toLowerCase();
    }

    private void incrementOperation(List<WriteBatch> batches, int[] count, FirebaseFirestore db) {
        count[0]++;
        if (count[0] >= 490) { // Keep a small margin
            batches.add(db.batch());
            count[0] = 0;
        }
    }
}
