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
import com.finanzapp.app.data.model.statistics.MonthlySummary;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class StatisticsViewModel extends ViewModel {
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
    
    private final MutableLiveData<Double> currentMonthIncome = new MutableLiveData<>(0.0);
    private final MutableLiveData<Double> currentMonthExpense = new MutableLiveData<>(0.0);
    private final MutableLiveData<Double> incomeVariationPercentage = new MutableLiveData<>(0.0);
    private final MutableLiveData<Double> variationPercentage = new MutableLiveData<>(0.0);
    private final MutableLiveData<String> currentPeriodLabel = new MutableLiveData<>("");
    
    private final MutableLiveData<List<MonthlySummary>> monthlyEvolution = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<DashboardCategorySummary>> categoryDistribution = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Category>> allCategories = new MutableLiveData<>(new ArrayList<>());

    private ListenerRegistration userListener;
    private Set<String> activeAccountIds = new HashSet<>();
    private List<Transaction> allTransactions = new ArrayList<>();
    private List<Category> latestCategories = new ArrayList<>();

    public StatisticsViewModel(AuthRepository authRepository, FamilyRepository familyRepository) {
        this.authRepository = authRepository;
        this.familyRepository = familyRepository;

        // Default range: current month
        LocalDate firstOfMonth = LocalDate.now().withDayOfMonth(1);
        LocalDate lastOfMonth = firstOfMonth.plusMonths(1).minusDays(1);

        long start = firstOfMonth.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long end = lastOfMonth.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        dateRange.setValue(new Pair<>(start, end));

        // Reactive architecture to avoid leaks and observeForever
        familyData = Transformations.switchMap(familyIdSource, id -> {
            MutableLiveData<Result<Family>> live = new MutableLiveData<>();
            familyRepository.getFamily(id, live::postValue);
            return live;
        });

        accountsSource = Transformations.switchMap(familyIdSource, accountRepository::getAccounts);
        categoriesSource = Transformations.switchMap(familyIdSource, categoryRepository::getCategories);
        transactionsSource = Transformations.switchMap(familyIdSource, id -> {
            Pair<Long, Long> range = dateRange.getValue();
            if (range == null) {
                // If no range, limit to last 12 months for evolution chart
                LocalDate startLimit = LocalDate.now().minusMonths(11).withDayOfMonth(1);
                ZonedDateTime zdt = startLimit.atStartOfDay(ZoneId.systemDefault());
                Timestamp timestamp = new Timestamp(Date.from(zdt.toInstant()));
                return transactionRepository.getTransactions(id, null, null, null, null, timestamp, null);
            } else {
                // Fetch current range PLUS enough for previous comparison period
                // We shift back by the same amount of time. Simplest is same duration or minusMonths(1)
                LocalDate startDate = Instant.ofEpochMilli(range.first).atZone(ZoneId.systemDefault()).toLocalDate();
                LocalDate compareStart = startDate.minusMonths(1).withDayOfMonth(1);
                
                ZonedDateTime zdt = compareStart.atStartOfDay(ZoneId.systemDefault());
                Timestamp timestamp = new Timestamp(Date.from(zdt.toInstant()));
                // We don't cap the end, or we can cap it at range.second
                return transactionRepository.getTransactions(id, null, null, null, null, timestamp, null);
            }
        });

        setupObservers();
    }

    private void setupObservers() {
        accountsSource.observeForever(accounts -> {
            if (accounts != null) {
                Set<String> activeIds = new HashSet<>();
                for (Account account : accounts) {
                    if (account.isActive() && account.getId() != null) {
                        activeIds.add(account.getId());
                    }
                }
                activeAccountIds = activeIds;
                recomputeStatistics();
            }
        });

        categoriesSource.observeForever(categories -> {
            latestCategories = categories != null ? categories : new ArrayList<>();
            allCategories.postValue(latestCategories);
            recomputeStatistics();
        });

        transactionsSource.observeForever(transactions -> {
            allTransactions = transactions != null ? transactions : new ArrayList<>();
            recomputeStatistics();
        });
    }

    public LiveData<Result<Family>> getFamilyData() { return familyData; }
    public LiveData<Result<Boolean>> getDataLoaded() { return dataLoaded; }
    public LiveData<Pair<Long, Long>> getDateRange() { return dateRange; }
    public LiveData<Double> getCurrentMonthIncome() { return currentMonthIncome; }
    public LiveData<Double> getCurrentMonthExpense() { return currentMonthExpense; }
    public LiveData<Double> getIncomeVariationPercentage() { return incomeVariationPercentage; }
    public LiveData<Double> getVariationPercentage() { return variationPercentage; }
    public LiveData<String> getCurrentPeriodLabel() { return currentPeriodLabel; }
    public LiveData<List<MonthlySummary>> getMonthlyEvolution() { return monthlyEvolution; }
    public LiveData<List<DashboardCategorySummary>> getCategoryDistribution() { return categoryDistribution; }
    public LiveData<List<Category>> getAllCategories() { return allCategories; }

    public void setDateRange(Long start, Long end) {
        if (start == null || end == null) {
            dateRange.setValue(null);
        } else {
            dateRange.setValue(new Pair<>(start, end));
        }
        // Re-trigger transactions fetch
        String currentId = familyIdSource.getValue();
        if (currentId != null) {
            familyIdSource.setValue(currentId);
        }
    }

    public void init() {
        FirebaseUser currentUser = authRepository.getCurrentUser().getValue();
        if (currentUser == null) return;

        if (userListener == null) {
            userListener = FirebaseFirestore.getInstance().collection(FirestorePaths.USERS).document(currentUser.getUid())
                    .addSnapshotListener((value, error) -> {
                        if (error != null || value == null) return;
                        User user = value.toObject(User.class);
                        if (user != null && user.getFamilyId() != null) {
                            userData.postValue(new Result.Success<>(user));
                            familyIdSource.postValue(user.getFamilyId());
                        }
                    });
        }
    }

    private void recomputeStatistics() {
        if (activeAccountIds.isEmpty()) {
            return;
        }

        List<Transaction> activeTransactions = new ArrayList<>();
        for (Transaction t : allTransactions) {
            if (activeAccountIds.contains(t.getAccountId())) {
                activeTransactions.add(t);
            }
        }

        Pair<Long, Long> range = dateRange.getValue();
        
        LocalDate rangeStart, rangeEnd, compareStart, compareEnd;
        boolean hasComparison = false;

        if (range != null) {
            rangeStart = Instant.ofEpochMilli(range.first).atZone(ZoneId.systemDefault()).toLocalDate();
            rangeEnd = Instant.ofEpochMilli(range.second).atZone(ZoneId.systemDefault()).toLocalDate();
            
            // Comparison period: shift back by 1 month (or same duration)
            // Let's use 1 month shift as it's common for monthly reviews
            compareStart = rangeStart.minusMonths(1);
            compareEnd = rangeEnd.minusMonths(1);
            hasComparison = true;
        } else {
            // Full history
            rangeStart = LocalDate.MIN;
            rangeEnd = LocalDate.MAX;
            compareStart = null;
            compareEnd = null;
        }

        double currentIncome = 0;
        double currentExpense = 0;
        double previousIncome = 0;
        double previousExpense = 0;

        Map<String, MonthlySummaryBuilder> monthlyMap = new TreeMap<>(); // Key: YYYY-MM
        Map<String, Double> currentCategoryTotals = new HashMap<>();

        for (Transaction t : activeTransactions) {
            LocalDate date = t.getDate().toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            
            // Current period totals
            if (!date.isBefore(rangeStart) && !date.isAfter(rangeEnd)) {
                if ("income".equals(t.getType())) {
                    currentIncome += t.getAmount();
                } else {
                    currentExpense += t.getAmount();
                    String categoryId = t.getCategoryId();
                    if (categoryId != null) {
                        currentCategoryTotals.put(categoryId, currentCategoryTotals.getOrDefault(categoryId, 0.0) + t.getAmount());
                    }
                }
            } 
            // Previous period totals for comparison
            else if (hasComparison && !date.isBefore(compareStart) && !date.isAfter(compareEnd)) {
                if ("income".equals(t.getType())) {
                    previousIncome += t.getAmount();
                } else {
                    previousExpense += t.getAmount();
                }
            }

            // Monthly Evolution (always based on month labels)
            String monthKey = date.getYear() + "-" + String.format("%02d", date.getMonthValue());
            String monthLabel = date.getMonth().getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " + (date.getYear() % 100);
            
            MonthlySummaryBuilder builder = monthlyMap.get(monthKey);
            if (builder == null) {
                builder = new MonthlySummaryBuilder(monthLabel);
                monthlyMap.put(monthKey, builder);
            }
            if ("income".equals(t.getType())) builder.income += t.getAmount();
            else builder.expense += t.getAmount();
        }

        currentMonthIncome.postValue(currentIncome);
        currentMonthExpense.postValue(currentExpense);
        
        // Variation labels
        if (hasComparison) {
            if (previousIncome > 0) {
                incomeVariationPercentage.postValue(((currentIncome - previousIncome) / previousIncome) * 100);
            } else {
                incomeVariationPercentage.postValue(0.0);
            }

            if (previousExpense > 0) {
                variationPercentage.postValue(((currentExpense - previousExpense) / previousExpense) * 100);
            } else {
                variationPercentage.postValue(0.0);
            }
        } else {
            incomeVariationPercentage.postValue(null);
            variationPercentage.postValue(null);
        }

        // Evolution list
        List<MonthlySummary> evolution = new ArrayList<>();
        for (MonthlySummaryBuilder b : monthlyMap.values()) {
            evolution.add(new MonthlySummary(b.label, b.income, b.expense));
        }
        monthlyEvolution.postValue(evolution);

        // Category distribution
        List<DashboardCategorySummary> distribution = new ArrayList<>();
        Map<String, Category> catMap = new HashMap<>();
        for (Category c : latestCategories) catMap.put(c.getId(), c);

        for (Map.Entry<String, Double> entry : currentCategoryTotals.entrySet()) {
            Category cat = catMap.get(entry.getKey());
            String name = cat != null ? cat.getName() : "Otros";
            String color = cat != null ? cat.getColor() : "#808080";
            double percentage = currentExpense > 0 ? (entry.getValue() / currentExpense) * 100 : 0;
            distribution.add(new DashboardCategorySummary(entry.getKey(), name, color, entry.getValue(), percentage));
        }
        Collections.sort(distribution, (s1, s2) -> Double.compare(s2.getAmount(), s1.getAmount()));
        categoryDistribution.postValue(distribution);

        dataLoaded.postValue(new Result.Success<>(true));
    }

    private static class MonthlySummaryBuilder {
        String label;
        double income = 0;
        double expense = 0;
        MonthlySummaryBuilder(String label) { this.label = label; }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (userListener != null) userListener.remove();
    }
}
