package com.finanzapp.app.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.finanzapp.app.data.firebase.FirestorePaths;
import com.finanzapp.app.data.model.Account;
import com.finanzapp.app.util.Result;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class AccountRepository {
    private final FirebaseFirestore db;

    public AccountRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    public interface AccountCallback {
        void onResult(Result<Account> result);
    }

    public void createAccount(String familyId, Account account, AccountCallback callback) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            callback.onResult(new Result.Error<>(new Exception("User not authenticated")));
            return;
        }

        DocumentReference ref = db.collection(FirestorePaths.getFamilyPath(familyId) + "/" + FirestorePaths.ACCOUNTS).document();
        account.setId(ref.getId());
        account.setCreatedBy(uid);
        account.setCreatedAt(Timestamp.now());
        // Ensure currentBalance is initialized to initialBalance if not set
        account.setCurrentBalance(account.getCurrentBalance() == 0.0 ? account.getInitialBalance() : account.getCurrentBalance());
        account.setActive(true);

        ref.set(account).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                callback.onResult(new Result.Success<>(account));
            } else {
                callback.onResult(new Result.Error<>(task.getException()));
            }
        });
    }

    public LiveData<List<Account>> getAccounts(String familyId) {
        MutableLiveData<List<Account>> live = new MutableLiveData<>();
        db.collection(FirestorePaths.getFamilyPath(familyId) + "/" + FirestorePaths.ACCOUNTS)
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) return;
                    List<Account> accounts = new ArrayList<>();
                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : value) {
                        accounts.add(doc.toObject(Account.class));
                    }
                    live.setValue(accounts);
                });
        return live;
    }

    public interface SimpleCallback {
        void onResult(Result<String> result);
    }

    /**
     * Actualiza una cuenta existente de forma atómica.
     * Calcula el delta entre el saldo inicial nuevo y el anterior para actualizar el saldo actual,
     * preservando así el efecto de los movimientos ya registrados.
     */
    public void updateAccount(String familyId, Account updatedAccount, AccountCallback callback) {
        if (updatedAccount.getId() == null) {
            callback.onResult(new Result.Error<>(new Exception("Account id missing")));
            return;
        }

        DocumentReference accountRef = db.collection(FirestorePaths.getFamilyPath(familyId) + "/" + FirestorePaths.ACCOUNTS)
                .document(updatedAccount.getId());

        db.runTransaction(transaction -> {
            Account oldAccount = transaction.get(accountRef).toObject(Account.class);
            if (oldAccount == null) {
                throw new RuntimeException("Account not found");
            }

            double initialBalanceDelta = updatedAccount.getInitialBalance() - oldAccount.getInitialBalance();
            updatedAccount.setCurrentBalance(oldAccount.getCurrentBalance() + initialBalanceDelta);

            // Preservamos metadatos si no vienen en el objeto actualizado
            if (updatedAccount.getCreatedBy() == null) updatedAccount.setCreatedBy(oldAccount.getCreatedBy());
            if (updatedAccount.getCreatedAt() == null) updatedAccount.setCreatedAt(oldAccount.getCreatedAt());
            // Mantener el estado activo a menos que se cambie explícitamente
            // (updateAccount suele usarse para nombre/saldo inicial)

            transaction.set(accountRef, updatedAccount);
            return updatedAccount;
        }).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                callback.onResult(new Result.Success<>(task.getResult()));
            } else {
                callback.onResult(new Result.Error<>(task.getException()));
            }
        });
    }

    public void archiveAccount(String familyId, String accountId, boolean active, SimpleCallback callback) {
        db.collection(FirestorePaths.getFamilyPath(familyId) + "/" + FirestorePaths.ACCOUNTS)
                .document(accountId)
                .update("active", active)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        callback.onResult(new Result.Success<>(accountId));
                    } else {
                        callback.onResult(new Result.Error<>(task.getException()));
                    }
                });
    }

    public void deleteAccount(String familyId, String accountId, SimpleCallback callback) {
        // Verificamos si hay transacciones antes de borrar físicamente
        db.collection(FirestorePaths.getFamilyPath(familyId) + "/" + FirestorePaths.TRANSACTIONS)
                .whereEqualTo("accountId", accountId)
                .limit(1)
                .get()
                .addOnCompleteListener(queryTask -> {
                    if (queryTask.isSuccessful() && !queryTask.getResult().isEmpty()) {
                        callback.onResult(new Result.Error<>(new Exception("No se puede eliminar una cuenta con movimientos. Archívala en su lugar.")));
                    } else {
                        db.collection(FirestorePaths.getFamilyPath(familyId) + "/" + FirestorePaths.ACCOUNTS)
                                .document(accountId)
                                .delete()
                                .addOnCompleteListener(task -> {
                                    if (task.isSuccessful()) {
                                        callback.onResult(new Result.Success<>(accountId));
                                    } else {
                                        callback.onResult(new Result.Error<>(task.getException()));
                                    }
                                });
                    }
                });
    }
}