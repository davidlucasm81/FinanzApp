package com.finanzapp.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.finanzapp.app.data.model.User;
import com.finanzapp.app.data.repository.AuthRepository;
import com.finanzapp.app.util.Result;
import com.finanzapp.app.util.SingleLiveEvent;
import com.google.firebase.auth.AuthCredential;

public class AuthViewModel extends ViewModel {
    private final AuthRepository authRepository;
    private final SingleLiveEvent<Result<User>> authResult = new SingleLiveEvent<>();

    public AuthViewModel(AuthRepository authRepository) {
        this.authRepository = authRepository;
    }

    public LiveData<Result<User>> getAuthResult() {
        return authResult;
    }

    public void loginWithCredential(AuthCredential credential) {
        authResult.setValue(new Result.Loading<>());
        authRepository.signInWithCredential(credential, result -> {
            authResult.postValue(result);
        });
    }

    public boolean isLoggedIn() {
        return authRepository.isLoggedIn();
    }
}
