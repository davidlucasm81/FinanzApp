package com.finanzapp.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.finanzapp.app.data.model.User;
import com.finanzapp.app.data.repository.AuthRepository;
import com.finanzapp.app.util.Result;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.finanzapp.app.data.firebase.FirestorePaths;

public class SettingsViewModel extends ViewModel {
    private final AuthRepository authRepository;
    private final MutableLiveData<Result<User>> userData = new MutableLiveData<>();

    public SettingsViewModel(AuthRepository authRepository) {
        this.authRepository = authRepository;
    }

    public LiveData<Result<User>> getUserData() {
        return userData;
    }

    public void fetchUserData() {
        FirebaseUser currentUser = authRepository.getCurrentUser().getValue();
        if (currentUser == null) {
            userData.setValue(new Result.Error<>(new Exception("User not logged in")));
            return;
        }

        userData.setValue(new Result.Loading<>());
        FirebaseFirestore.getInstance().collection(FirestorePaths.USERS).document(currentUser.getUid())
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) {
                        userData.postValue(new Result.Error<>(error != null ? error : new Exception("User not found")));
                        return;
                    }
                    User user = value.toObject(User.class);
                    if (user != null) {
                        userData.postValue(new Result.Success<>(user));
                    } else {
                        userData.postValue(new Result.Error<>(new Exception("User not found")));
                    }
                });
    }

    public void signOut() {
        authRepository.signOut();
    }
}
