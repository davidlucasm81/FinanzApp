package com.finanzapp.app.viewmodel;

import androidx.core.util.Pair;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.finanzapp.app.data.firebase.FirestorePaths;
import com.finanzapp.app.data.model.Account;
import com.finanzapp.app.data.model.Category;
import com.finanzapp.app.data.model.DashboardCategorySummary;
import com.finanzapp.app.data.model.Family;
import com.finanzapp.app.data.model.Transaction;
import com.finanzapp.app.data.model.User;
import com.finanzapp.app.data.repository.AccountRepository;
import com.finanzapp.app.data.repository.AuthRepository;
import com.finanzapp.app.data.repository.CategoryRepository;
import com.finanzapp.app.data.repository.FamilyRepository;
import com.finanzapp.app.data.repository.TransactionRepository;
import com.finanzapp.app.util.Result;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DashboardViewModel extends ViewModel {
    private final AuthRepository authRepository;
    private final FamilyRepository familyRepository;
    private final AccountRepository accountRepository = new AccountRepository();
    private final TransactionRepository transactionRepository = new TransactionRepository();
    private final CategoryRepository categoryRepository = new CategoryRepository();

    private final MutableLiveData<String> familyIdSource = new MutableLiveData<>();
    private final LiveData<Result<Family>> familyData;
    private final LiveData<List<Account>> accountsSource;
    private final LiveData<List<Category>> categoriesSource;
    private final LiveData<List<Transaction>> transactionsSource;

    private final MutableLiveData<Result<Boolean>> dataLoaded = new MutableLiveData<>(new Result.Loading<>());
    private final MutableLiveData<Result<User>> userData = new MutableLiveData<>();
    private final MutableLiveData<Pair<Long, Long>> dateRange = new MutableLiveData<>();
    private final MutableLiveData<Double> netBalance = new MutableLiveData<>(0.0);
    private final MutableLiveData<Double> totalIncome = new MutableLiveData<>(0.0);
    private final MutableLiveData<Double> totalExpense = new MutableLiveData<>(0.0);
    private final MutableLiveData<List<Account>> accountsList = new MutableLiveData<>();
    private final MutableLiveData<List<DashboardCategorySummary>> categoryBreakdown = new MutableLiveData<>();

    private ListenerRegistration userListener;

    // Último estado conocido de cada fuente async
    private Set<String> activeAccountIds = null;
    private List<Transaction> latestTransactions = new ArrayList<>();
    private List<Category> latestCategories = new ArrayList<>();

    public DashboardViewModel(AuthRepository authRepository, FamilyRepository familyRepository) {
        this.authRepository = authRepository;
        this.familyRepository = familyRepository;

        // Default range: current month (FIXED: using java.time for precision)
        LocalDate firstOfMonth = LocalDate.now().withDayOfMonth(1);
        LocalDate lastOfMonth = firstOfMonth.plusMonths(1).minusDays(1);

        long start = firstOfMonth.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long end = lastOfMonth.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        dateRange.setValue(new Pair<>(start, end));

        // Reactive architecture
        familyData = Transformations.switchMap(familyIdSource, id -> {
            MutableLiveData<Result<Family>> live = new MutableLiveData<>();
            familyRepository.getFamily(id, live::postValue);
            return live;
        });

        accountsSource = Transformations.switchMap(familyIdSource, accountRepository::getAccounts);
        categoriesSource = Transformations.switchMap(familyIdSource, categoryRepository::getCategories);
        transactionsSource = Transformations.switchMap(familyIdSource, id -> {
            Pair<Long, Long> range = dateRange.getValue();
            Timestamp startTs = null;
            Timestamp endTs = null;
            if (range != null) {
                startTs = new Timestamp(new Date(range.first));
                endTs = new Timestamp(new Date(range.second));
            }
            return transactionRepository.getTransactions(id, null, null, null, null, startTs, endTs);
        });

        setupObservers();
    }

    private void setupObservers() {
        accountsSource.observeForever(accounts -> {
            if (accounts != null) {
                accountsList.postValue(accounts);
                double total = 0;
                Set<String> activeIds = new HashSet<>();
                for (Account account : accounts) {
                    if (account.isActive()) {
                        total += account.getCurrentBalance();
                        if (account.getId() != null) {
                            activeIds.add(account.getId());
                        }
                    }
                }
                netBalance.postValue(total);
                activeAccountIds = activeIds;
                
                if (accounts.isEmpty()) {
                    dataLoaded.postValue(new Result.Success<>(true));
                }
                
                recomputeStatistics();
            }
        });

        categoriesSource.observeForever(categoryList -> {
            latestCategories = (categoryList != null) ? categoryList : new ArrayList<>();
            recomputeStatistics();
        });

        transactionsSource.observeForever(transactions -> {
            latestTransactions = (transactions != null) ? transactions : new ArrayList<>();
            recomputeStatistics();
        });
    }

    public LiveData<Result<Family>> getFamilyData() { return familyData; }
    public LiveData<Result<Boolean>> getDataLoaded() { return dataLoaded; }
    public LiveData<Result<User>> getUserData() { return userData; }
    public LiveData<Pair<Long, Long>> getDateRange() { return dateRange; }
    public LiveData<Double> getNetBalance() { return netBalance; }
    public LiveData<Double> getTotalIncome() { return totalIncome; }
    public LiveData<Double> getTotalExpense() { return totalExpense; }
    public LiveData<List<Account>> getAccountsList() { return accountsList; }
    public LiveData<List<DashboardCategorySummary>> getCategoryBreakdown() { return categoryBreakdown; }

    public void setDateRange(Long start, Long end) {
        if (start == null || end == null) {
            dateRange.setValue(null);
        } else {
            dateRange.setValue(new Pair<>(start, end));
        }
        // When range changes, we need to re-trigger the transactionsSource switchMap
        String currentId = familyIdSource.getValue();
        if (currentId != null) {
            familyIdSource.setValue(currentId);
        }
    }

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
                                userData.postValue(new Result.Error<>(error != null ? error : new Exception("User not found")));
                            }
                            return;
                        }
                        User user = value.toObject(User.class);
                        if (user != null) {
                            userData.postValue(new Result.Success<>(user));
                            if (user.getFamilyId() != null) {
                                familyIdSource.postValue(user.getFamilyId());
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

    private void recomputeStatistics() {
        if (activeAccountIds == null) {
            // Aún no ha llegado el primer snapshot de cuentas; no hay forma de saber qué
            // movimientos pertenecen a cuentas activas todavía.
            return;
        }

        List<Transaction> activeAccountTransactions = new ArrayList<>();
        for (Transaction t : latestTransactions) {
            if (t.getAccountId() != null && activeAccountIds.contains(t.getAccountId())) {
                activeAccountTransactions.add(t);
            }
        }

        processTransactions(activeAccountTransactions, latestCategories);
    }

    private void processTransactions(List<Transaction> transactions, List<Category> categories) {
        if (transactions == null) return;

        double income = 0;
        double expense = 0;
        Map<String, Double> categoryTotals = new HashMap<>();

        for (Transaction t : transactions) {
            if ("income".equals(t.getType())) {
                income += t.getAmount();
            } else {
                expense += t.getAmount();
                categoryTotals.put(t.getCategoryId(), categoryTotals.getOrDefault(t.getCategoryId(), 0.0) + t.getAmount());
            }
        }

        totalIncome.postValue(income);
        totalExpense.postValue(expense);

        // Build category summary list
        List<DashboardCategorySummary> summaries = new ArrayList<>();
        Map<String, Category> catMap = new HashMap<>();
        if (categories != null) {
            for (Category c : categories) catMap.put(c.getId(), c);
        }

        for (Map.Entry<String, Double> entry : categoryTotals.entrySet()) {
            Category cat = catMap.get(entry.getKey());
            String name = (cat != null) ? cat.getName() : "Sin categoría";
            String color = (cat != null) ? cat.getColor() : "#808080";
            double amount = entry.getValue();
            double percentage = (expense > 0) ? (amount / expense) * 100 : 0;
            summaries.add(new DashboardCategorySummary(entry.getKey(), name, color, amount, percentage));
        }

        // Sort by amount descending
        Collections.sort(summaries, (s1, s2) -> Double.compare(s2.getAmount(), s1.getAmount()));
        categoryBreakdown.postValue(summaries);
        
        dataLoaded.postValue(new Result.Success<>(true));
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