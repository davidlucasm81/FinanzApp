package com.finanzapp.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.finanzapp.app.data.model.User;
import com.finanzapp.app.data.repository.AuthRepository;
import com.finanzapp.app.util.Result;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.finanzapp.app.data.firebase.FirestorePaths;

public class SettingsViewModel extends ViewModel {
    private final AuthRepository authRepository;
    private final MutableLiveData<Result<User>> userData = new MutableLiveData<>();
    private final MutableLiveData<Result<Boolean>> deleteAccountResult = new MutableLiveData<>();
    private final com.finanzapp.app.util.SingleLiveEvent<Result<String>> exportResult = new com.finanzapp.app.util.SingleLiveEvent<>();
    private ListenerRegistration userListener;

    public SettingsViewModel(AuthRepository authRepository) {
        this.authRepository = authRepository;
    }

    public LiveData<Result<User>> getUserData() {
        return userData;
    }

    public LiveData<Result<Boolean>> getDeleteAccountResult() {
        return deleteAccountResult;
    }

    public LiveData<Result<String>> getExportResult() {
        return exportResult;
    }

    public void fetchUserData() {
        FirebaseUser currentUser = authRepository.getCurrentUser().getValue();
        if (currentUser == null) {
            userData.setValue(new Result.Error<>(new Exception("User not logged in")));
            return;
        }

        userData.setValue(new Result.Loading<>());
        userListener = FirebaseFirestore.getInstance().collection(FirestorePaths.USERS).document(currentUser.getUid())
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) {
                        // Avoid posting error if we are signing out (Permission Denied is expected)
                        if (authRepository.getCurrentUser().getValue() != null) {
                            userData.postValue(new Result.Error<>(error != null ? error : new Exception("User not found")));
                        }
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
        stopListening();
        authRepository.signOut();
    }

    private void stopListening() {
        if (userListener != null) {
            userListener.remove();
            userListener = null;
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        stopListening();
    }

    public void deleteAccount(com.finanzapp.app.data.repository.FamilyRepository familyRepository) {
        stopListening();
        deleteAccountResult.setValue(new Result.Loading<>());
        authRepository.deleteAccount(familyRepository, result -> {
            if (result instanceof Result.Success) {
                deleteAccountResult.postValue(new Result.Success<>(true));
            } else {
                deleteAccountResult.postValue(new Result.Error<>(((Result.Error<?>) result).getException()));
            }
        });
    }

    public void exportUserData() {
        exportResult.setValue(new Result.Loading<>());
        authRepository.exportUserData(result -> exportResult.postValue(result));
    }
}
