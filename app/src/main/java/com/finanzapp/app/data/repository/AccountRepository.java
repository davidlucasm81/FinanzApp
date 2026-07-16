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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    /**
     * Devuelve las cuentas de la familia, marcando en cada una si tiene o no movimientos
     * asociados ({@link Account#isHasTransactions()}). La UI usa ese flag para decidir si
     * ofrece "Archivar" (cuenta con movimientos) o "Eliminar" (cuenta sin movimientos),
     * en vez de dejar que el usuario intente borrar y encontrarse con un error.
     * <p>
     * Se escuchan tanto la colección de cuentas como la de movimientos, para que el flag
     * se mantenga correcto si se añade/borra un movimiento sin que cambien las cuentas.
     */
    public LiveData<List<Account>> getAccounts(String familyId) {
        MutableLiveData<List<Account>> live = new MutableLiveData<>();
        // Guardamos la última lista de cuentas conocida para poder recalcular el flag
        // cuando llegue un cambio en la colección de movimientos (sin esperar a que
        // también cambien las cuentas).
        final List<Account>[] lastAccounts = new List[]{new ArrayList<Account>()};

        db.collection(FirestorePaths.getFamilyPath(familyId) + "/" + FirestorePaths.ACCOUNTS)
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) return;
                    List<Account> accounts = new ArrayList<>();
                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : value) {
                        accounts.add(doc.toObject(Account.class));
                    }
                    lastAccounts[0] = accounts;
                    refreshHasTransactionsFlag(familyId, accounts, live);
                });

        db.collection(FirestorePaths.getFamilyPath(familyId) + "/" + FirestorePaths.TRANSACTIONS)
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) return;
                    refreshHasTransactionsFlag(familyId, lastAccounts[0], live);
                });

        return live;
    }

    private void refreshHasTransactionsFlag(String familyId, List<Account> accounts, MutableLiveData<List<Account>> live) {
        if (accounts.isEmpty()) {
            live.setValue(accounts);
            return;
        }

        List<String> ids = new ArrayList<>();
        for (Account a : accounts) {
            if (a.getId() != null) ids.add(a.getId());
        }
        if (ids.isEmpty()) {
            live.setValue(accounts);
            return;
        }
        // Firestore admite un máximo de 30 valores en una cláusula whereIn; en la práctica
        // ninguna familia debería tener tantas cuentas, pero se acota por seguridad.
        List<String> queryIds = ids.size() > 30 ? ids.subList(0, 30) : ids;

        db.collection(FirestorePaths.getFamilyPath(familyId) + "/" + FirestorePaths.TRANSACTIONS)
                .whereIn("accountId", queryIds)
                .get()
                .addOnSuccessListener(snapshot -> {
                    Set<String> accountIdsWithMovements = new HashSet<>();
                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : snapshot) {
                        String accId = doc.getString("accountId");
                        if (accId != null) accountIdsWithMovements.add(accId);
                    }
                    for (Account a : accounts) {
                        a.setHasTransactions(accountIdsWithMovements.contains(a.getId()));
                    }
                    live.setValue(accounts);
                })
                .addOnFailureListener(e -> {
                    // Si falla la comprobación, se muestra la lista sin actualizar el flag
                    // (se mantendrá el valor por defecto/anterior) en vez de bloquear el listado.
                    live.setValue(accounts);
                });
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