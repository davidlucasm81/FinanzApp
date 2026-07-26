package com.finanzapp.app.data.repository;

import androidx.lifecycle.LiveData;

import com.finanzapp.app.data.firebase.FirestorePaths;
import com.finanzapp.app.data.model.Account;
import com.finanzapp.app.util.FirestoreLiveData;
import com.finanzapp.app.util.Result;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AccountRepository {
    private final FirebaseFirestore db;

    // Listeners activos: se van acumulando conforme se piden cuentas de distintas
    // familias/pantallas. stopListening() los desconecta todos de golpe.
    private final List<ListenerRegistration> activeListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    // Caching LiveData instances
    private final Map<String, LiveData<List<Account>>> accountsCache = new ConcurrentHashMap<>();

    public AccountRepository(AuthRepository authRepository) {
        db = FirebaseFirestore.getInstance();

        authRepository.registerPreSignOutCleanup(this::clearCache);
    }

    private void clearCache() {
        accountsCache.clear();
        stopListening();
    }

    /**
     * Desconecta todos los listeners de Firestore activos de este repositorio.
     * Debe llamarse antes de invalidar la sesión (signOut/deleteAccount) para evitar
     * PERMISSION_DENIED cuando el usuario pierde acceso a la familia.
     */
    public void stopListening() {
        for (ListenerRegistration reg : activeListeners) {
            reg.remove();
        }
        activeListeners.clear();
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
        account.setTransactionCount(0L);

        ref.set(account).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                callback.onResult(new Result.Success<>(account));
            } else {
                callback.onResult(new Result.Error<>(task.getException()));
            }
        });
    }

    /**
     * Devuelve las cuentas de la familia (listener en tiempo real).
     * Versión optimizada que NO comprueba si tienen movimientos asociados.
     * Ideal para el Dashboard donde solo se necesita el saldo.
     */
    public LiveData getAccounts(String familyId) {
        if (accountsCache.containsKey(familyId)) {
            return accountsCache.get(familyId);
        }
        FirestoreLiveData<Account> live = new FirestoreLiveData<>(
                db.collection(FirestorePaths.getFamilyPath(familyId) + "/" + FirestorePaths.ACCOUNTS),
                Account.class, true);
        accountsCache.put(familyId, (LiveData) live);
        return (LiveData) live;
    }

    /**
     * Devuelve las cuentas de la familia, marcando en cada una si tiene o no movimientos
     * asociados. Ahora optimizado usando el campo transactionCount denormalizado.
     */
    public LiveData<List<Account>> getAccountsWithTransactionStatus(String familyId) {
        // En esta nueva implementación, getAccounts() ya trae el transactionCount
        // que actualiza el flag hasTransactions en el modelo POJO Account.
        return getAccounts(familyId);
    }

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

            // BUGFIX 2026-07-18: Asegurar que se mantiene el estado activo original.
            // Al deserializar updatedAccount en el cliente para el diálogo de edición,
            // 'active' puede perderse o resetearse a false si no se lee del bundle.
            updatedAccount.setActive(oldAccount.isActive());
            // Preserve transactionCount
            updatedAccount.setTransactionCount(oldAccount.getTransactionCount());

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

    /**
     * Cuenta los movimientos de una cuenta y actualiza el campo denormalizado transactionCount.
     * Útil para migrar datos legados de forma transparente (self-healing).
     */
    public void migrateAccountTransactionCount(String familyId, String accountId) {
        db.collection(FirestorePaths.getFamilyPath(familyId) + "/" + FirestorePaths.TRANSACTIONS)
                .whereEqualTo("accountId", accountId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    long count = querySnapshot.size();
                    db.collection(FirestorePaths.getFamilyPath(familyId) + "/" + FirestorePaths.ACCOUNTS)
                            .document(accountId)
                            .update("transactionCount", count);
                });
    }

    public interface SimpleCallback {
        void onResult(Result<String> result);
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
        // Verificamos si hay transacciones antes de borrar físicamente usando el campo denormalizado
        db.collection(FirestorePaths.getFamilyPath(familyId) + "/" + FirestorePaths.ACCOUNTS)
                .document(accountId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult().exists()) {
                        Account account = task.getResult().toObject(Account.class);
                        if (account != null && account.getTransactionCount() > 0) {
                            callback.onResult(new Result.Error<>(new Exception("No se puede eliminar una cuenta con movimientos. Archívala en su lugar.")));
                        } else {
                            db.collection(FirestorePaths.getFamilyPath(familyId) + "/" + FirestorePaths.ACCOUNTS)
                                    .document(accountId)
                                    .delete()
                                    .addOnCompleteListener(deleteTask -> {
                                        if (deleteTask.isSuccessful()) {
                                            callback.onResult(new Result.Success<>(accountId));
                                        } else {
                                            callback.onResult(new Result.Error<>(deleteTask.getException()));
                                        }
                                    });
                        }
                    } else {
                        callback.onResult(new Result.Error<>(task.getException()));
                    }
                });
    }
}
