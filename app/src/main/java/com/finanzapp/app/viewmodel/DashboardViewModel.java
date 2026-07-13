package com.finanzapp.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.finanzapp.app.data.model.Family;
import com.finanzapp.app.data.model.User;
import com.finanzapp.app.data.repository.AuthRepository;
import com.finanzapp.app.data.repository.FamilyRepository;
import com.finanzapp.app.util.Result;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.finanzapp.app.data.firebase.FirestorePaths;
import androidx.lifecycle.MediatorLiveData;
import com.finanzapp.app.data.model.Account;
import com.finanzapp.app.data.repository.AccountRepository;

import java.util.List;

public class DashboardViewModel extends ViewModel {
    private final AuthRepository authRepository;
    private final FamilyRepository familyRepository;
    private final MutableLiveData<Result<Family>> familyData = new MutableLiveData<>();
    private final MutableLiveData<Result<User>> userData = new MutableLiveData<>();
    private ListenerRegistration userListener;

    private final AccountRepository accountRepository = new AccountRepository();

    private final MutableLiveData<Double> netBalance = new MutableLiveData<>(0.0);

    public LiveData<Double> getNetBalance() {
        return netBalance;
    }

    public DashboardViewModel(AuthRepository authRepository, FamilyRepository familyRepository) {
        this.authRepository = authRepository;
        this.familyRepository = familyRepository;
    }

    public LiveData<Result<Family>> getFamilyData() {
        return familyData;
    }

    public LiveData<Result<User>> getUserData() {
        return userData;
    }

    public void fetchDashboardData() {
        FirebaseUser currentUser = authRepository.getCurrentUser().getValue();
        if (currentUser == null) {
            userData.setValue(new Result.Error<>(new Exception("User not logged in")));
            return;
        }

        userData.setValue(new Result.Loading<>());
        userListener = FirebaseFirestore.getInstance().collection(FirestorePaths.USERS).document(currentUser.getUid())
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) {
                        // Avoid posting error if we are signing out
                        if (authRepository.getCurrentUser().getValue() != null) {
                            userData.postValue(new Result.Error<>(error != null ? error : new Exception("User not found")));
                        }
                        return;
                    }
                    User user = value.toObject(User.class);
                    if (user != null) {
                        userData.postValue(new Result.Success<>(user));
                        if (user.getFamilyId() != null) {
                            fetchFamily(user.getFamilyId());
                        }
                    } else {
                        userData.postValue(new Result.Error<>(new Exception("User not found")));
                    }
                });
    }

    private void fetchFamily(String familyId) {
        familyRepository.getFamily(familyId, familyData::postValue);

        accountRepository.getAccounts(familyId).observeForever(accounts -> {
            double total = 0;

            if (accounts != null) {
                for (Account account : accounts) {
                    if (account.isActive()) {
                        total += account.getCurrentBalance();
                    }
                }
            }

            netBalance.postValue(total);
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (userListener != null) {
            userListener.remove();
            userListener = null;
        }
    }
}
