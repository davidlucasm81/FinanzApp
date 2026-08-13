package com.finanzapp.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.finanzapp.app.data.firebase.FirestorePaths;
import com.finanzapp.app.data.model.Account;
import com.finanzapp.app.data.model.Family;
import com.finanzapp.app.data.model.Member;
import com.finanzapp.app.data.model.User;
import com.finanzapp.app.data.repository.AccountRepository;
import com.finanzapp.app.data.repository.AuthRepository;
import com.finanzapp.app.data.repository.CategoryRepository;
import com.finanzapp.app.data.repository.FamilyRepository;
import com.finanzapp.app.data.repository.SettlementRepository;
import com.finanzapp.app.data.repository.TransactionRepository;
import com.finanzapp.app.data.model.Transaction;
import com.finanzapp.app.data.model.Settlement;
import com.finanzapp.app.data.sharedexpenses.BalanceCalculator;
import com.finanzapp.app.util.FirebaseLogger;
import com.finanzapp.app.util.Result;
import com.finanzapp.app.util.SingleLiveEvent;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.perf.metrics.Trace;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class DashboardViewModel extends ViewModel {
    private final AuthRepository authRepository;
    private final FamilyRepository familyRepository;

    private final MutableLiveData<String> familyIdSource = new MutableLiveData<>();
    private final LiveData<Result<Family>> familyData;

    private final MutableLiveData<Result<Boolean>> dataLoaded = new MutableLiveData<>(new Result.Loading<>());
    private final MutableLiveData<Result<User>> userData = new MutableLiveData<>();
    private final MutableLiveData<Double> netBalance = new MutableLiveData<>(0.0);
    private final MutableLiveData<List<Account>> accountsList = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isPrivacyModeEnabled = new MutableLiveData<>(false);
    private final SingleLiveEvent<Result<String>> transferResult = new SingleLiveEvent<>();

    private final Set<String> migratingAccounts = new HashSet<>();
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final SettlementRepository settlementRepository;
    private ListenerRegistration userListener;

    private LiveData<Boolean> accountsLoaded;
    private LiveData<List<Transaction>> recentTransactions;
    private LiveData<List<Member>> members;
    private LiveData<Result<List<Settlement>>> settlements;
    private final MediatorLiveData<Map<String, Double>> memberBalances = new MediatorLiveData<>();

    // Referencia estable para poder registrar/desregistrar el mismo Runnable
    private final Runnable signOutCleanup = this::stopListening;

    private void setupObservers() {
        // We use Transformations.map to link the data update to the accountsSource lifecycle
    }
    public DashboardViewModel(AuthRepository authRepository, FamilyRepository familyRepository, 
                            AccountRepository accountRepository, TransactionRepository transactionRepository,
                            CategoryRepository categoryRepository, SettlementRepository settlementRepository) {
        this.authRepository = authRepository;
        this.familyRepository = familyRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.settlementRepository = settlementRepository;
        authRepository.registerPreSignOutCleanup(signOutCleanup);

        // Load persisted privacy mode state
        // In a more complex architecture, context would be injected, but here we use the AppContainer's context concept if possible
        // or just rely on a way to get context if needed. DashboardFragment will pass the initial state if needed, 
        // but it's cleaner to have the VM initialize it.

        // Reactive architecture
        familyData = Transformations.switchMap(familyIdSource, id -> {
            MutableLiveData<Result<Family>> live = new MutableLiveData<>();
            this.familyRepository.getFamily(id, live::postValue);
            return live;
        });

        // Optimized account fetch for the dashboard (real value, no monthly filtering)
        LiveData<List<Account>> accountsSource = Transformations.switchMap(familyIdSource, accountRepository::getAccounts);

        this.accountsLoaded = Transformations.map(accountsSource, accounts -> {
            if (accounts != null) {
                accountsList.postValue(accounts);
                double totalBalance = 0;
                String familyId = familyIdSource.getValue();
                for (Account account : accounts) {
                    if (account.isActive()) {
                        totalBalance += account.getCurrentBalance();
                    }
                    // Self-healing migration for legacy data
                    if (account.getTransactionCount() == null && familyId != null && !migratingAccounts.contains(account.getId())) {
                        migratingAccounts.add(account.getId());
                        accountRepository.migrateAccountTransactionCount(familyId, account.getId());
                    }
                }
                netBalance.postValue(totalBalance);
                dataLoaded.postValue(new Result.Success<>(true));
                return true;
            }
            return false;
        });

        // Fetch recent transactions (e.g., last 20)
        this.recentTransactions = Transformations.switchMap(familyIdSource, id -> {
            if (id == null) return new MutableLiveData<>(new java.util.ArrayList<>());
            // Passing null filters to get all transactions, limit is handled by repo if supported or just get all
            return transactionRepository.getTransactions(id, null, null, null, null, null, null);
        });

        this.members = Transformations.switchMap(familyIdSource, id -> {
            if (id == null) return new MutableLiveData<>(new java.util.ArrayList<>());
            MutableLiveData<List<Member>> live = new MutableLiveData<>();
            familyRepository.getMembers(id, res -> {
                if (res instanceof Result.Success) {
                    live.postValue(((Result.Success<List<Member>>) res).getData());
                }
            });
            return live;
        });

        this.settlements = Transformations.switchMap(familyIdSource, id -> {
            if (id == null) return new MutableLiveData<>(new Result.Success<>(new java.util.ArrayList<>()));
            MutableLiveData<Result<List<Settlement>>> live = new MutableLiveData<>();
            settlementRepository.getSettlements(id, live::postValue);
            return live;
        });

        memberBalances.addSource(recentTransactions, t -> updateMemberBalances());
        memberBalances.addSource(settlements, s -> updateMemberBalances());

        setupObservers();
    }

    private void updateMemberBalances() {
        List<Transaction> tList = recentTransactions.getValue();
        Result<List<Settlement>> sResult = settlements.getValue();
        
        if (tList != null && sResult instanceof Result.Success) {
            List<Settlement> sList = ((Result.Success<List<Settlement>>) sResult).getData();
            Map<String, Double> balances = BalanceCalculator.calculateNetBalances(tList, sList);
            memberBalances.setValue(balances);
        }
    }

    public LiveData<Boolean> getAccountsLoaded() { return accountsLoaded; }
    public LiveData<List<Transaction>> getRecentTransactions() { return recentTransactions; }
    public LiveData<List<Member>> getMembers() { return members; }
    public LiveData<Map<String, Double>> getMemberBalances() { return memberBalances; }
    public LiveData<Result<Family>> getFamilyData() { return familyData; }
    public LiveData<Result<Boolean>> getDataLoaded() { return dataLoaded; }
    public LiveData<Result<User>> getUserData() { return userData; }
    public LiveData<Double> getNetBalance() { return netBalance; }
    public LiveData<List<Account>> getAccountsList() { return accountsList; }
    public LiveData<Boolean> isPrivacyModeEnabled() { return isPrivacyModeEnabled; }
    public LiveData<Result<String>> getTransferResult() { return transferResult; }

    public void togglePrivacyMode(android.content.Context context) {
        Boolean current = isPrivacyModeEnabled.getValue();
        boolean newValue = current == null || !current;
        isPrivacyModeEnabled.setValue(newValue);
        com.finanzapp.app.util.PreferenceUtils.setPrivacyModeEnabled(context, newValue);
    }

    public void initPrivacyMode(android.content.Context context) {
        boolean enabled = com.finanzapp.app.util.PreferenceUtils.isPrivacyModeEnabled(context);
        isPrivacyModeEnabled.setValue(enabled);
    }

    public void transferFunds(String fromAccountId, String toAccountId, double amount) {
        String familyId = familyIdSource.getValue();
        if (familyId == null) return;

        transferResult.setValue(new Result.Loading<>());
        accountRepository.transferFunds(familyId, fromAccountId, toAccountId, amount, result -> {
            transferResult.postValue(result);
        });
    }

    public String getCurrentUserId() {
        return authRepository.getUid();
    }

    public void fetchDashboardData() {
        Trace trace = FirebaseLogger.startTrace("dashboard_data_fetch");

        FirebaseUser currentUser = authRepository.getCurrentUser().getValue();
        if (currentUser == null) {
            userData.setValue(new Result.Error<>(new Exception("User not logged in")));
            FirebaseLogger.stopTrace(trace);
            return;
        }

        if (userListener == null) {
            userData.setValue(new Result.Loading<>());
            userListener = FirebaseFirestore.getInstance().collection(FirestorePaths.USERS).document(currentUser.getUid())
                    .addSnapshotListener((value, error) -> {
                        FirebaseLogger.stopTrace(trace);
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

    private void stopListening() {
        if (userListener != null) {
            userListener.remove();
            userListener = null;
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        authRepository.unregisterPreSignOutCleanup(signOutCleanup);
        stopListening();
    }
}