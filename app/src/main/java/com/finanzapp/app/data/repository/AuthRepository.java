package com.finanzapp.app.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.finanzapp.app.data.firebase.FirestorePaths;
import com.finanzapp.app.data.model.User;
import com.finanzapp.app.util.FirebaseLogger;
import com.finanzapp.app.util.Result;
import com.google.firebase.Timestamp;
import com.google.firebase.perf.metrics.Trace;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class AuthRepository {
    private final FirebaseAuth auth;
    private final FirebaseFirestore db;
    private final MutableLiveData<FirebaseUser> firebaseUserLiveData;

    // Registro central de limpieza: cada ViewModel que mantenga listeners de Firestore
    // se registra aquí. Así, sea cual sea la pantalla desde la que se cierre sesión o
    // se borre la cuenta, TODOS los listeners activos se desconectan antes de invalidar
    // el token, evitando los PERMISSION_DENIED al perder los permisos de Firestore.
    private final java.util.List<Runnable> preSignOutCleanupTasks = new java.util.concurrent.CopyOnWriteArrayList<>();

    public void registerPreSignOutCleanup(Runnable cleanup) {
        preSignOutCleanupTasks.add(cleanup);
    }

    public void unregisterPreSignOutCleanup(Runnable cleanup) {
        preSignOutCleanupTasks.remove(cleanup);
    }

    private void runPreSignOutCleanup() {
        for (Runnable cleanup : preSignOutCleanupTasks) {
            try {
                cleanup.run();
            } catch (Exception e) {
                android.util.Log.e("AuthRepository", "Error during pre-signout listener cleanup", e);
            }
        }
    }

    public AuthRepository() {
        this.auth = FirebaseAuth.getInstance();
        this.db = FirebaseFirestore.getInstance();
        this.firebaseUserLiveData = new MutableLiveData<>(auth.getCurrentUser());

        auth.addAuthStateListener(firebaseAuth -> firebaseUserLiveData.setValue(firebaseAuth.getCurrentUser()));
    }

    public LiveData<FirebaseUser> getCurrentUser() {
        return firebaseUserLiveData;
    }

    public String getUid() {
        return auth.getUid();
    }

    public boolean isLoggedIn() {
        return auth.getCurrentUser() != null;
    }

    public void signInWithCredential(AuthCredential credential, AuthCallback callback) {
        Trace trace = FirebaseLogger.startTrace("auth_sign_in");
        auth.signInWithCredential(credential)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        syncUserToFirestore(task.getResult().getUser(), callback);
                    } else {
                        callback.onResult(new Result.Error<>(task.getException()));
                    }
                    FirebaseLogger.stopTrace(trace);
                });
    }

    private void syncUserToFirestore(FirebaseUser firebaseUser, AuthCallback callback) {
        if (firebaseUser == null) {
            callback.onResult(new Result.Error<>(new Exception("FirebaseUser is null after successful sign in")));
            return;
        }

        Trace trace = FirebaseLogger.startTrace("auth_sync_user");
        FirebaseLogger.setUserId(firebaseUser.getUid());

        db.collection(FirestorePaths.USERS).document(firebaseUser.getUid()).get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        DocumentSnapshot document = task.getResult();
                        if (document.exists()) {
                            User user = document.toObject(User.class);
                            callback.onResult(new Result.Success<>(user));
                        } else {
                            // Create new user
                            User newUser = new User(
                                    firebaseUser.getUid(),
                                    firebaseUser.getDisplayName(),
                                    firebaseUser.getEmail(),
                                    firebaseUser.getPhotoUrl() != null ? firebaseUser.getPhotoUrl().toString() : null,
                                    null, // familyId null initially
                                    Timestamp.now()
                            );
                            db.collection(FirestorePaths.USERS).document(newUser.getUid()).set(newUser)
                                    .addOnSuccessListener(aVoid -> callback.onResult(new Result.Success<>(newUser)))
                                    .addOnFailureListener(e -> callback.onResult(new Result.Error<>(e)));
                        }
                    } else {
                        callback.onResult(new Result.Error<>(task.getException()));
                    }
                    FirebaseLogger.stopTrace(trace);
                });
    }

    public void signOut() {
        runPreSignOutCleanup();
        FirebaseLogger.setUserId("");
        auth.signOut();
    }

    public void getUser(String uid, AuthCallback callback) {
        db.collection(FirestorePaths.USERS).document(uid).get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        DocumentSnapshot document = task.getResult();
                        if (document != null && document.exists()) {
                            User user = document.toObject(User.class);
                            callback.onResult(new Result.Success<>(user));
                        } else {
                            callback.onResult(new Result.Error<>(new Exception("User not found")));
                        }
                    } else {
                        callback.onResult(new Result.Error<>(task.getException()));
                    }
                });
    }

    public void deleteAccount(FamilyRepository familyRepository, AuthCallback callback) {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser == null) {
            callback.onResult(new Result.Error<>(new Exception("User not authenticated")));
            return;
        }

        Trace trace = FirebaseLogger.startTrace("auth_delete_account");
        String uid = firebaseUser.getUid();

        // Desconectamos todos los listeners activos antes de empezar a borrar/abandonar
        // familias, ya que ese proceso puede ir dejando al usuario sin permisos sobre
        // documentos que algún listener siga escuchando.
        runPreSignOutCleanup();

        // Phase 7 bis: Iterate all memberships and leave each family
        db.collection(FirestorePaths.getMembershipsPath(uid)).get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        List<DocumentSnapshot> memberships = task.getResult().getDocuments();
                        if (memberships.isEmpty()) {
                            proceedWithDeletion(firebaseUser, callback, trace);
                        } else {
                            leaveFamiliesSequentially(memberships, 0, familyRepository, firebaseUser, callback, trace);
                        }
                    } else {
                        // User doc or memberships might not exist, proceed with deletion
                        proceedWithDeletion(firebaseUser, callback, trace);
                    }
                });
    }

    private void leaveFamiliesSequentially(List<DocumentSnapshot> memberships, int index, FamilyRepository familyRepository, FirebaseUser firebaseUser, AuthCallback callback, Trace trace) {
        if (index >= memberships.size()) {
            proceedWithDeletion(firebaseUser, callback, trace);
            return;
        }

        String familyId = memberships.get(index).getId();
        familyRepository.leaveFamily(familyId, result -> {
            // We continue even if one fails to ensure maximum cleanup
            leaveFamiliesSequentially(memberships, index + 1, familyRepository, firebaseUser, callback, trace);
        });
    }

    private void proceedWithDeletion(FirebaseUser firebaseUser, AuthCallback callback, Trace trace) {
        String uid = firebaseUser.getUid();

        // 3. Delete from Firestore
        db.collection(FirestorePaths.USERS).document(uid).delete()
                .addOnCompleteListener(task -> {
                    // 4. Delete from Auth
                    firebaseUser.delete()
                            .addOnCompleteListener(authTask -> {
                                if (authTask.isSuccessful()) {
                                    callback.onResult(new Result.Success<>(null));
                                } else {
                                    callback.onResult(new Result.Error<>(authTask.getException()));
                                }
                                FirebaseLogger.stopTrace(trace);
                            });
                });
    }

    public interface AuthCallback {
        void onResult(Result<User> result);
    }

    public interface ExportCallback {
        void onResult(Result<String> result);
    }

    public void exportUserData(ExportCallback callback) {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser == null) {
            callback.onResult(new Result.Error<>(new Exception("User not authenticated")));
            return;
        }

        Trace trace = FirebaseLogger.startTrace("auth_export_data");
        String uid = firebaseUser.getUid();

        db.runTransaction(transaction -> {
            DocumentSnapshot userDoc = transaction.get(db.collection(FirestorePaths.USERS).document(uid));
            if (!userDoc.exists()) {
                throw new RuntimeException("Documento de usuario no existe en Firestore");
            }
            return userDoc.toObject(User.class);
        }).addOnSuccessListener(user -> {
            android.util.Log.d("AuthRepository", "User profile fetched, now fetching memberships...");
            fetchMembershipsAndTransactions(user, callback, trace);
        }).addOnFailureListener(e -> {
            android.util.Log.e("AuthRepository", "Error in export transaction", e);
            callback.onResult(new Result.Error<>(e));
            FirebaseLogger.stopTrace(trace);
        });
    }

    private void fetchMembershipsAndTransactions(User user, ExportCallback callback, Trace trace) {
        String uid = user.getUid();
        db.collection(FirestorePaths.getMembershipsPath(uid)).get().addOnSuccessListener(membershipsSnapshot -> {
            List<DocumentSnapshot> memberships = membershipsSnapshot.getDocuments();
            android.util.Log.d("AuthRepository", "Memberships fetched: " + memberships.size() + ", now fetching transactions family by family...");

            List<DocumentSnapshot> allUserTransactions = new ArrayList<>();
            if (memberships.isEmpty()) {
                generateAndPostJson(user, memberships, allUserTransactions, callback, trace);
                return;
            }

            fetchTransactionsIteratively(user, memberships, 0, allUserTransactions, callback, trace);
        }).addOnFailureListener(e -> {
            android.util.Log.e("AuthRepository", "Error fetching memberships", e);
            callback.onResult(new Result.Error<>(e));
            FirebaseLogger.stopTrace(trace);
        });
    }

    private void fetchTransactionsIteratively(User user, List<DocumentSnapshot> memberships, int index, List<DocumentSnapshot> collectedTransactions, ExportCallback callback, Trace trace) {
        if (index >= memberships.size()) {
            generateAndPostJson(user, memberships, collectedTransactions, callback, trace);
            return;
        }

        String familyId = memberships.get(index).getId();
        String uid = user.getUid();

        db.collection(FirestorePaths.getFamilyPath(familyId) + "/" + FirestorePaths.TRANSACTIONS)
                .whereEqualTo("createdBy", uid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    collectedTransactions.addAll(snapshot.getDocuments());
                    fetchTransactionsIteratively(user, memberships, index + 1, collectedTransactions, callback, trace);
                })
                .addOnFailureListener(e -> {
                    // Si falla una familia (ej. ya no tenemos acceso), continuamos con las demás
                    android.util.Log.w("AuthRepository", "Failed to fetch transactions for family: " + familyId, e);
                    fetchTransactionsIteratively(user, memberships, index + 1, collectedTransactions, callback, trace);
                });
    }

    private void generateAndPostJson(User user, List<DocumentSnapshot> memberships, List<DocumentSnapshot> transactions, ExportCallback callback, Trace trace) {
        android.util.Log.d("AuthRepository", "All data collected (Trans: " + transactions.size() + "), generating JSON...");
        try {
            String json = generateExportJson(user, memberships, transactions);
            callback.onResult(new Result.Success<>(json));
        } catch (Exception e) {
            android.util.Log.e("AuthRepository", "Error generating JSON", e);
            callback.onResult(new Result.Error<>(e));
        } finally {
            FirebaseLogger.stopTrace(trace);
        }
    }

    private String generateExportJson(User user, List<DocumentSnapshot> memberships, List<DocumentSnapshot> transactions) throws Exception {
        // Use a simple JSON builder approach (or a library if available, but let's assume no extra libs)
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"profile\": {\n");
        sb.append("    \"uid\": \"").append(user.getUid()).append("\",\n");
        sb.append("    \"displayName\": \"").append(user.getDisplayName()).append("\",\n");
        sb.append("    \"email\": \"").append(user.getEmail()).append("\"\n");
        sb.append("  },\n");

        sb.append("  \"memberships\": [\n");
        for (int i = 0; i < memberships.size(); i++) {
            DocumentSnapshot doc = memberships.get(i);
            sb.append("    {\n");
            sb.append("      \"familyId\": \"").append(doc.getId()).append("\",\n");
            sb.append("      \"familyName\": \"").append(doc.getString("familyName")).append("\",\n");
            sb.append("      \"role\": \"").append(doc.getString("role")).append("\"\n");
            sb.append("    }").append(i < memberships.size() - 1 ? "," : "").append("\n");
        }
        sb.append("  ],\n");

        sb.append("  \"my_transactions\": [\n");
        for (int i = 0; i < transactions.size(); i++) {
            DocumentSnapshot doc = transactions.get(i);
            sb.append("    {\n");
            sb.append("      \"id\": \"").append(doc.getId()).append("\",\n");
            sb.append("      \"amount\": ").append(doc.getDouble("amount")).append(",\n");
            sb.append("      \"type\": \"").append(doc.getString("type")).append("\",\n");
            sb.append("      \"description\": \"").append(doc.getString("description")).append("\",\n");
            com.google.firebase.Timestamp dateTs = doc.getTimestamp("date");
            sb.append("      \"date\": \"").append(dateTs != null ? dateTs.toDate().toString() : "null").append("\"\n");
            sb.append("    }").append(i < transactions.size() - 1 ? "," : "").append("\n");
        }
        sb.append("  ]\n");
        sb.append("}");
        return sb.toString();
    }
}