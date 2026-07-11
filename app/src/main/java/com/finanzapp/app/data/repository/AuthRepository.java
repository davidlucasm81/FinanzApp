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

    public interface AuthCallback {
        void onResult(Result<User> result);
    }
}
