package com.finanzapp.app.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.finanzapp.app.data.firebase.FirestorePaths;
import com.finanzapp.app.data.model.User;
import com.finanzapp.app.util.Result;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class AuthRepository {
    private final FirebaseAuth auth;
    private final FirebaseFirestore db;
    private final MutableLiveData<FirebaseUser> firebaseUserLiveData;

    public AuthRepository() {
        this.auth = FirebaseAuth.getInstance();
        this.db = FirebaseFirestore.getInstance();
        this.firebaseUserLiveData = new MutableLiveData<>(auth.getCurrentUser());
        
        auth.addAuthStateListener(firebaseAuth -> {
            firebaseUserLiveData.setValue(firebaseAuth.getCurrentUser());
        });
    }

    public LiveData<FirebaseUser> getCurrentUser() {
        return firebaseUserLiveData;
    }

    public boolean isLoggedIn() {
        return auth.getCurrentUser() != null;
    }

    public void signInWithCredential(AuthCredential credential, AuthCallback callback) {
        auth.signInWithCredential(credential)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        syncUserToFirestore(task.getResult().getUser(), callback);
                    } else {
                        callback.onResult(new Result.Error<>(task.getException()));
                    }
                });
    }

    private void syncUserToFirestore(FirebaseUser firebaseUser, AuthCallback callback) {
        if (firebaseUser == null) {
            callback.onResult(new Result.Error<>(new Exception("FirebaseUser is null after successful sign in")));
            return;
        }

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
                });
    }

    public void signOut() {
        auth.signOut();
    }

    public void deleteAccount(FamilyRepository familyRepository, AuthCallback callback) {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser == null) {
            callback.onResult(new Result.Error<>(new Exception("User not authenticated")));
            return;
        }

        String uid = firebaseUser.getUid();

        // 1. Fetch user data to check for familyId
        db.collection(FirestorePaths.USERS).document(uid).get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult().exists()) {
                        User user = task.getResult().toObject(User.class);
                        if (user != null && user.getFamilyId() != null) {
                            // 2. Leave family first
                            familyRepository.leaveFamily(user.getFamilyId(), result -> {
                                if (result instanceof Result.Success) {
                                    proceedWithDeletion(firebaseUser, callback);
                                } else {
                                    callback.onResult(new Result.Error<>(((Result.Error<?>) result).getException()));
                                }
                            });
                        } else {
                            proceedWithDeletion(firebaseUser, callback);
                        }
                    } else {
                        // User doc might not exist but we should still try to delete the auth user
                        proceedWithDeletion(firebaseUser, callback);
                    }
                });
    }

    private void proceedWithDeletion(FirebaseUser firebaseUser, AuthCallback callback) {
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
                            });
                });
    }

    public interface AuthCallback {
        void onResult(Result<User> result);
    }
}
