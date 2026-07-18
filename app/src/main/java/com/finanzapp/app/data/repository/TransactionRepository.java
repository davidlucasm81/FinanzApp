package com.finanzapp.app.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.finanzapp.app.data.firebase.FirestorePaths;
import com.finanzapp.app.data.model.Account;
import com.finanzapp.app.data.model.Transaction;
import com.finanzapp.app.util.Result;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class TransactionRepository {
    private final FirebaseFirestore db;
    private final FirebaseAuth auth;

    public TransactionRepository() {
        this.db = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
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

        transaction.setCreatedBy(uid);
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
            firestoreTransaction.update(accountRef, "currentBalance", account.getCurrentBalance());
            
            return null;
        }).addOnSuccessListener(result -> callback.onResult(new Result.Success<>(true)))
          .addOnFailureListener(e -> callback.onResult(new Result.Error<>(e)));
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
                // Same account
                double newAmount = newTransaction.getAmount();
                double newBalanceChange = "income".equals(newTransaction.getType()) ? newAmount : -newAmount;
                oldAccount.setCurrentBalance(oldAccount.getCurrentBalance() + newBalanceChange);
                
                firestoreTransaction.update(oldAccountRef, "currentBalance", oldAccount.getCurrentBalance());
            } else {
                // Different account
                Account newAccount = firestoreTransaction.get(newAccountRef).toObject(Account.class);
                if (newAccount == null) throw new RuntimeException("New account not found");
                
                double newAmount = newTransaction.getAmount();
                double newBalanceChange = "income".equals(newTransaction.getType()) ? newAmount : -newAmount;
                newAccount.setCurrentBalance(newAccount.getCurrentBalance() + newBalanceChange);

                firestoreTransaction.update(oldAccountRef, "currentBalance", oldAccount.getCurrentBalance());
                firestoreTransaction.update(newAccountRef, "currentBalance", newAccount.getCurrentBalance());
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
            firestoreTransaction.update(accountRef, "currentBalance", account.getCurrentBalance());
            
            return null;
        }).addOnSuccessListener(result -> callback.onResult(new Result.Success<>(true)))
          .addOnFailureListener(e -> callback.onResult(new Result.Error<>(e)));
    }

    public LiveData<List<Transaction>> getTransactions(String familyId) {
        return getTransactions(familyId, null, null, null, null, null, null);
    }

    public LiveData<List<Transaction>> getTransactions(String familyId, String accountId, String categoryId, String type, String paymentMethod, Timestamp startDate, Timestamp endDate) {
        MutableLiveData<List<Transaction>> liveData = new MutableLiveData<>();
        
        Query query = db.collection(FirestorePaths.getFamilyPath(familyId) + "/" + FirestorePaths.TRANSACTIONS)
                .orderBy("date", Query.Direction.DESCENDING)
                .orderBy("createdAt", Query.Direction.DESCENDING);

        if (accountId != null && !accountId.isEmpty()) {
            query = query.whereEqualTo("accountId", accountId);
        }
        if (categoryId != null && !categoryId.isEmpty()) {
            query = query.whereEqualTo("categoryId", categoryId);
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

        query.addSnapshotListener((value, error) -> {
            if (error != null || value == null) {
                android.util.Log.e("TransactionRepository", "Error getting transactions", error);
                return;
            }
            List<Transaction> transactions = new ArrayList<>();
            for (QueryDocumentSnapshot doc : value) {
                transactions.add(doc.toObject(Transaction.class));
            }
            liveData.setValue(transactions);
        });
        return liveData;
    }
}
