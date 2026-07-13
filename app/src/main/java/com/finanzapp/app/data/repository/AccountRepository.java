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
     * Actualiza una cuenta existente. Pensado para la fase de onboarding, donde todavía no
     * existen movimientos asociados: aquí currentBalance se iguala directamente a initialBalance.
     * NOTA (Fase 4): una vez existan transacciones, la edición del initialBalance de una cuenta
     * ya creada debe recalcular currentBalance mediante un delta dentro de una Firestore
     * transaction (currentBalance_nuevo = currentBalance_actual + (initialBalance_nuevo - initialBalance_anterior)),
     * en vez de sobrescribirlo directamente como se hace aquí.
     */
    public void updateAccount(String familyId, Account account, AccountCallback callback) {
        if (account.getId() == null) {
            callback.onResult(new Result.Error<>(new Exception("Account id missing")));
            return;
        }
        account.setCurrentBalance(account.getInitialBalance());

        db.collection(FirestorePaths.getFamilyPath(familyId) + "/" + FirestorePaths.ACCOUNTS)
                .document(account.getId())
                .set(account)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        callback.onResult(new Result.Success<>(account));
                    } else {
                        callback.onResult(new Result.Error<>(task.getException()));
                    }
                });
    }

    public void deleteAccount(String familyId, String accountId, SimpleCallback callback) {
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
}