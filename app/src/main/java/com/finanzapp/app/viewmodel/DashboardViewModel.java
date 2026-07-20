package com.finanzapp.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.finanzapp.app.data.firebase.FirestorePaths;
import com.finanzapp.app.data.model.Account;
import com.finanzapp.app.data.model.Family;
import com.finanzapp.app.data.model.User;
import com.finanzapp.app.data.repository.AccountRepository;
import com.finanzapp.app.data.repository.AuthRepository;
import com.finanzapp.app.data.repository.FamilyRepository;
import com.finanzapp.app.util.Result;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.List;
import java.util.Objects;

public class DashboardViewModel extends ViewModel {
    private final AuthRepository authRepository;
    private final FamilyRepository familyRepository;

    private final MutableLiveData<String> familyIdSource = new MutableLiveData<>();
    private final LiveData<Result<Family>> familyData;
    private final LiveData<List<Account>> accountsSource;

    private final MutableLiveData<Result<Boolean>> dataLoaded = new MutableLiveData<>(new Result.Loading<>());
    private final MutableLiveData<Result<User>> userData = new MutableLiveData<>();
    private final MutableLiveData<Double> netBalance = new MutableLiveData<>(0.0);
    private final MutableLiveData<List<Account>> accountsList = new MutableLiveData<>();

    private ListenerRegistration userListener;

    private void setupObservers() {
        // We use Transformations.map to link the data update to the accountsSource lifecycle
    }

    private final LiveData<Boolean> accountsLoaded;

    public DashboardViewModel(AuthRepository authRepository, FamilyRepository familyRepository) {
        this.authRepository = authRepository;
        this.familyRepository = familyRepository;
        AccountRepository accountRepository = new AccountRepository();

        // Reactive architecture
        familyData = Transformations.switchMap(familyIdSource, id -> {
            MutableLiveData<Result<Family>> live = new MutableLiveData<>();
            this.familyRepository.getFamily(id, live::postValue);
            return live;
        });

        // Optimized account fetch for the dashboard (real value, no monthly filtering)
        accountsSource = Transformations.switchMap(familyIdSource, accountRepository::getAccounts);

        accountsLoaded = Transformations.map(accountsSource, accounts -> {
            if (accounts != null) {
                accountsList.postValue(accounts);
                double totalBalance = 0;
                for (Account account : accounts) {
                    if (account.isActive()) {
                        totalBalance += account.getCurrentBalance();
                    }
                }
                netBalance.postValue(totalBalance);
                dataLoaded.postValue(new Result.Success<>(true));
                return true;
            }
            return false;
        });

        setupObservers();
    }

    public LiveData<Boolean> getAccountsLoaded() { return accountsLoaded; }
    public LiveData<Result<Family>> getFamilyData() { return familyData; }
    public LiveData<Result<Boolean>> getDataLoaded() { return dataLoaded; }
    public LiveData<Result<User>> getUserData() { return userData; }
    public LiveData<Double> getNetBalance() { return netBalance; }
    public LiveData<List<Account>> getAccountsList() { return accountsList; }

    public void fetchDashboardData() {
        FirebaseUser currentUser = authRepository.getCurrentUser().getValue();
        if (currentUser == null) {
            userData.setValue(new Result.Error<>(new Exception("User not logged in")));
            return;
        }

        if (userListener == null) {
            userData.setValue(new Result.Loading<>());
            userListener = FirebaseFirestore.getInstance().collection(FirestorePaths.USERS).document(currentUser.getUid())
                    .addSnapshotListener((value, error) -> {
                        if (error != null || value == null) {
                            if (authRepository.getCurrentUser().getValue() != null) {
                                userData.postValue(new Result.Error<>(Objects.requireNonNullElseGet(error, () -> new Exception("User not found"))));
                            }
                            return;
                        }
                        User user = value.toObject(User.class);
                        if (user != null) {
                            userData.postValue(new Result.Success<>(user));
                            String newFamilyId = user.getFamilyId();
                            String currentFamilyId = familyIdSource.getValue();
                            
                            if (newFamilyId != null && !newFamilyId.equals(currentFamilyId)) {
                                familyIdSource.postValue(newFamilyId);
                            } else if (newFamilyId == null && currentFamilyId != null) {
                                familyIdSource.postValue(null);
                            }
                        }
                    });
        } else {
            // Already listening to user, just refresh if familyId source is set
            String currentId = familyIdSource.getValue();
            if (currentId != null) {
                familyIdSource.postValue(currentId);
            }
        }
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
