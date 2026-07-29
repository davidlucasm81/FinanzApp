package com.finanzapp.app.data.repository;

import androidx.lifecycle.LiveData;

import com.finanzapp.app.R;
import com.finanzapp.app.data.firebase.FirestorePaths;
import com.finanzapp.app.data.model.Account;
import com.finanzapp.app.data.model.Notification;
import com.finanzapp.app.data.model.Transaction;
import com.finanzapp.app.util.Result;
import com.finanzapp.app.util.FirestoreLiveData;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TransactionRepository {
    private final FirebaseFirestore db;
    private final FirebaseAuth auth;
    private final NotificationRepository notificationRepository;
    private final android.content.Context context;

    // Listeners activos: stopListening() los desconecta todos antes de invalidar la sesión.
    private final List<ListenerRegistration> activeListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    // Caching for LiveData to avoid creating multiple listeners for the same query
    private final Map<String, LiveData<List<Transaction>>> transactionsCache = new ConcurrentHashMap<>();

    public TransactionRepository(android.content.Context context, AuthRepository authRepository) {
        this.db = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
        this.notificationRepository = new NotificationRepository();
        this.context = context.getApplicationContext();
        authRepository.registerPreSignOutCleanup(this::clearCache);
    }

    private void clearCache() {
        transactionsCache.clear();
        stopListening();
    }

    public void stopListening() {
        for (ListenerRegistration reg : activeListeners) {
            reg.remove();
        }
        activeListeners.clear();
    }

    public interface Callback {
        void onResult(Result<Boolean> result);
    }

    public void addTransaction(String familyId, Transaction transaction, Callback callback) {
        String uid = auth.getUid();
        if (uid == null) {
            callback.onResult(new Result.Error<>(new Exception("User not authenticated")));
            return;
        }

        if (transaction.getCreatedBy() == null) {
            transaction.setCreatedBy(uid);
        }
        transaction.setCreatedAt(Timestamp.now());

        DocumentReference transactionRef = db.collection(FirestorePaths.getFamilyPath(familyId) + "/" + FirestorePaths.TRANSACTIONS).document();
        transaction.setId(transactionRef.getId());

        DocumentReference accountRef = db.collection(FirestorePaths.getFamilyPath(familyId) + "/" + FirestorePaths.ACCOUNTS).document(transaction.getAccountId());

        db.runTransaction(firestoreTransaction -> {
                    Account account = firestoreTransaction.get(accountRef).toObject(Account.class);
                    if (account == null) throw new RuntimeException("Account not found");

                    double amount = transaction.getAmount();
                    double balanceChange = "income".equals(transaction.getType()) ? amount : -amount;

                    account.setCurrentBalance(account.getCurrentBalance() + balanceChange);

                    firestoreTransaction.set(transactionRef, transaction);
                    firestoreTransaction.update(accountRef, 
                            "currentBalance", account.getCurrentBalance(),
                            "transactionCount", FieldValue.increment(1));

                    return null;
                }).addOnSuccessListener(result -> {
                    emitTransactionNotification(familyId, transaction);
                    callback.onResult(new Result.Success<>(true));
                })
                .addOnFailureListener(e -> callback.onResult(new Result.Error<>(e)));
    }

    private void emitTransactionNotification(String familyId, Transaction transaction) {
        Notification notification = new Notification();
        notification.setType("new_transaction");
        String title = "income".equals(transaction.getType())
                ? context.getString(R.string.notif_new_income)
                : context.getString(R.string.notif_new_expense);
        notification.setTitle(title);
        notification.setBody(transaction.getDescription() + ": " + transaction.getAmount());
        notification.setCreatedBy(transaction.getCreatedBy());
        notification.setCreatedAt(Timestamp.now());

        Map<String, Object> payload = new HashMap<>();
        payload.put("transactionId", transaction.getId());
        payload.put("amount", transaction.getAmount());
        payload.put("type", transaction.getType());
        notification.setPayload(payload);

        notificationRepository.addNotification(familyId, notification);
    }

    public void updateTransaction(String familyId, Transaction oldTransaction, Transaction newTransaction, Callback callback) {
        String uid = auth.getUid();
        if (uid == null) return;

        DocumentReference transactionRef = db.collection(FirestorePaths.getFamilyPath(familyId) + "/" + FirestorePaths.TRANSACTIONS).document(oldTransaction.getId());
        DocumentReference oldAccountRef = db.collection(FirestorePaths.getFamilyPath(familyId) + "/" + FirestorePaths.ACCOUNTS).document(oldTransaction.getAccountId());
        DocumentReference newAccountRef = db.collection(FirestorePaths.getFamilyPath(familyId) + "/" + FirestorePaths.ACCOUNTS).document(newTransaction.getAccountId());

        db.runTransaction(firestoreTransaction -> {
                    // Revert old transaction balance
                    Account oldAccount = firestoreTransaction.get(oldAccountRef).toObject(Account.class);
                    if (oldAccount == null) throw new RuntimeException("Old account not found");

                    double oldAmount = oldTransaction.getAmount();
                    double oldBalanceRevert = "income".equals(oldTransaction.getType()) ? -oldAmount : oldAmount;
                    oldAccount.setCurrentBalance(oldAccount.getCurrentBalance() + oldBalanceRevert);

                    if (oldTransaction.getAccountId().equals(newTransaction.getAccountId())) {
                        // Same account, transactionCount doesn't change
                        double newAmount = Math.abs(newTransaction.getAmount());
                        double newBalanceChange = "income".equals(newTransaction.getType()) ? newAmount : -newAmount;
                        oldAccount.setCurrentBalance(oldAccount.getCurrentBalance() + newBalanceChange);

                        firestoreTransaction.update(oldAccountRef, "currentBalance", oldAccount.getCurrentBalance());
                    } else {
                        // Different account, update transactionCount for both
                        Account newAccount = firestoreTransaction.get(newAccountRef).toObject(Account.class);
                        if (newAccount == null) throw new RuntimeException("New account not found");

                        double newAmount = Math.abs(newTransaction.getAmount());
                        double newBalanceChange = "income".equals(newTransaction.getType()) ? newAmount : -newAmount;
                        newAccount.setCurrentBalance(newAccount.getCurrentBalance() + newBalanceChange);
                        
                        firestoreTransaction.update(oldAccountRef, 
                                "currentBalance", oldAccount.getCurrentBalance(),
                                "transactionCount", FieldValue.increment(-1));
                        firestoreTransaction.update(newAccountRef, 
                                "currentBalance", newAccount.getCurrentBalance(),
                                "transactionCount", FieldValue.increment(1));
                    }

                    firestoreTransaction.set(transactionRef, newTransaction);
                    return null;
                }).addOnSuccessListener(result -> callback.onResult(new Result.Success<>(true)))
                .addOnFailureListener(e -> callback.onResult(new Result.Error<>(e)));
    }

    public void deleteTransaction(String familyId, Transaction transaction, Callback callback) {
        DocumentReference transactionRef = db.collection(FirestorePaths.getFamilyPath(familyId) + "/" + FirestorePaths.TRANSACTIONS).document(transaction.getId());
        DocumentReference accountRef = db.collection(FirestorePaths.getFamilyPath(familyId) + "/" + FirestorePaths.ACCOUNTS).document(transaction.getAccountId());

        db.runTransaction(firestoreTransaction -> {
                    Account account = firestoreTransaction.get(accountRef).toObject(Account.class);
                    if (account == null) throw new RuntimeException("Account not found");

                    double amount = transaction.getAmount();
                    double balanceRevert = "income".equals(transaction.getType()) ? -amount : amount;

                    account.setCurrentBalance(account.getCurrentBalance() + balanceRevert);

                    firestoreTransaction.delete(transactionRef);
                    firestoreTransaction.update(accountRef, 
                            "currentBalance", account.getCurrentBalance(),
                            "transactionCount", FieldValue.increment(-1));

                    return null;
                }).addOnSuccessListener(result -> callback.onResult(new Result.Success<>(true)))
                .addOnFailureListener(e -> callback.onResult(new Result.Error<>(e)));
    }

    public LiveData getTransactions(String familyId, String accountId, List<String> categoryIds, String type, String paymentMethod, Timestamp startDate, Timestamp endDate) {
        // Cache key based on filters
        String categoryIdsKey = categoryIds != null ? categoryIds.toString() : "null";
        String cacheKey = String.format("%s_%s_%s_%s_%s_%s_%s", familyId, accountId, categoryIdsKey, type, paymentMethod,
                startDate != null ? startDate.getSeconds() : "null",
                endDate != null ? endDate.getSeconds() : "null");

        if (transactionsCache.containsKey(cacheKey)) {
            return transactionsCache.get(cacheKey);
        }

        Query query = db.collection(FirestorePaths.getFamilyPath(familyId) + "/" + FirestorePaths.TRANSACTIONS)
                .orderBy("date", Query.Direction.DESCENDING)
                .orderBy("createdAt", Query.Direction.DESCENDING);

        if (accountId != null && !accountId.isEmpty()) {
            query = query.whereEqualTo("accountId", accountId);
        }
        if (categoryIds != null && !categoryIds.isEmpty()) {
            if (categoryIds.size() == 1) {
                query = query.whereEqualTo("categoryId", categoryIds.get(0));
            } else {
                // Firestore 'in' filter supports up to 30 elements
                List<String> limitedIds = categoryIds.size() > 30 ? categoryIds.subList(0, 30) : categoryIds;
                query = query.whereIn("categoryId", limitedIds);
            }
        }
        if (type != null && !type.isEmpty()) {
            query = query.whereEqualTo("type", type);
        }
        if (paymentMethod != null && !paymentMethod.isEmpty()) {
            query = query.whereEqualTo("paymentMethod", paymentMethod);
        }
        if (startDate != null) {
            query = query.whereGreaterThanOrEqualTo("date", startDate);
        }
        if (endDate != null) {
            query = query.whereLessThanOrEqualTo("date", endDate);
        }

        FirestoreLiveData<Transaction> liveData = new FirestoreLiveData<>(query, Transaction.class, true);
        transactionsCache.put(cacheKey, (LiveData) liveData);
        return (LiveData) liveData;
    }
}