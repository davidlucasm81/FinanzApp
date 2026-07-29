package com.finanzapp.app.viewmodel;

import android.annotation.SuppressLint;

import androidx.core.util.Pair;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.finanzapp.app.data.firebase.FirestorePaths;
import com.finanzapp.app.R;
import com.finanzapp.app.data.model.Account;
import com.finanzapp.app.data.model.Category;
import com.finanzapp.app.data.model.DashboardCategorySummary;
import com.finanzapp.app.data.model.Family;
import com.finanzapp.app.data.model.Member;
import com.finanzapp.app.data.model.MemberSummary;
import com.finanzapp.app.data.model.PaymentMethodSummary;
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

    private final MutableLiveData<String> familyIdSource = new MutableLiveData<>();
    private final LiveData<Result<Family>> familyData;
    private final LiveData<List<Account>> accountsSource;
    private final LiveData<List<Category>> categoriesSource;
    private final LiveData<List<Transaction>> transactionsSource;
    private final LiveData<List<Member>> membersSource;

    private final MutableLiveData<Result<Boolean>> dataLoaded = new MutableLiveData<>(new Result.Loading<>());
    private final MutableLiveData<Result<User>> userData = new MutableLiveData<>();
    private final MutableLiveData<Pair<Long, Long>> dateRange = new MutableLiveData<>();
    
    private final MutableLiveData<Double> currentMonthIncome = new MutableLiveData<>();
    private final MutableLiveData<Double> currentMonthExpense = new MutableLiveData<>();
    private final MutableLiveData<Double> incomeVariationPercentage = new MutableLiveData<>();
    private final MutableLiveData<Double> variationPercentage = new MutableLiveData<>();
    private final MutableLiveData<Double> savingsRate = new MutableLiveData<>();

    private final MutableLiveData<List<MonthlySummary>> monthlyEvolution = new MutableLiveData<>();
    private final MutableLiveData<List<DashboardCategorySummary>> categoryDistribution = new MutableLiveData<>();
    private final MutableLiveData<List<PaymentMethodSummary>> paymentMethodDistribution = new MutableLiveData<>();
    private final MutableLiveData<List<Transaction>> topExpenses = new MutableLiveData<>();
    private final MutableLiveData<List<MemberSummary>> memberExpenseDistribution = new MutableLiveData<>();
    private final MutableLiveData<List<MemberSummary>> memberIncomeDistribution = new MutableLiveData<>();
    private final MutableLiveData<List<Category>> allCategories = new MutableLiveData<>();

    private final MediatorLiveData<Void> statsMediator = new MediatorLiveData<>();
    private final androidx.lifecycle.Observer<Void> statsObserver = v -> {};

    private ListenerRegistration userListener;
    private Set<String> activeAccountIds = new HashSet<>();
    private List<Transaction> allTransactions = new ArrayList<>();
    private List<Category> latestCategories = new ArrayList<>();
    private List<Member> latestMembers = new ArrayList<>();

    private boolean accountsResolved = false;
    private boolean categoriesResolved = false;
    private boolean transactionsResolved = false;
    private boolean membersResolved = false;

    // Referencia estable para poder registrar/desregistrar el mismo Runnable
    private final Runnable signOutCleanup = this::stopListening;

    public StatisticsViewModel(AuthRepository authRepository, FamilyRepository familyRepository, 
                               AccountRepository accountRepository, CategoryRepository categoryRepository,
                               TransactionRepository transactionRepository) {
        this.authRepository = authRepository;
        authRepository.registerPreSignOutCleanup(signOutCleanup);

        // Default range: current month
        LocalDate firstOfMonth = LocalDate.now().withDayOfMonth(1);
        LocalDate lastOfMonth = firstOfMonth.plusMonths(1).minusDays(1);

        long start = firstOfMonth.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long end = lastOfMonth.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        dateRange.setValue(new Pair<>(start, end));

        // Reactive architecture to avoid leaks
        familyData = Transformations.switchMap(familyIdSource, id -> {
            MutableLiveData<Result<Family>> live = new MutableLiveData<>();
            familyRepository.getFamily(id, live::postValue);
            return live;
        });

        accountsSource = Transformations.switchMap(familyIdSource, accountRepository::getAccounts);
        categoriesSource = Transformations.switchMap(familyIdSource, categoryRepository::getCategories);
        membersSource = Transformations.switchMap(familyIdSource, id -> {
            MutableLiveData<List<Member>> live = new MutableLiveData<>();
            familyRepository.getMembers(id, result -> {
                if (result instanceof Result.Success) {
                    live.postValue(((Result.Success<List<Member>>) result).getData());
                }
            });
            return live;
        });
        transactionsSource = Transformations.switchMap(familyIdSource, id -> {
            Pair<Long, Long> range = dateRange.getValue();
            if (range == null) {
                LocalDate startLimit = LocalDate.now().minusMonths(11).withDayOfMonth(1);
                ZonedDateTime zdt = startLimit.atStartOfDay(ZoneId.systemDefault());
                Timestamp timestamp = new Timestamp(Date.from(zdt.toInstant()));
                return transactionRepository.getTransactions(id, null, null, null, null, timestamp, null);
            } else {
                LocalDate startDate = Instant.ofEpochMilli(range.first).atZone(ZoneId.systemDefault()).toLocalDate();
                LocalDate compareStart = startDate.minusMonths(1).withDayOfMonth(1);
                ZonedDateTime zdt = compareStart.atStartOfDay(ZoneId.systemDefault());
                Timestamp timestamp = new Timestamp(Date.from(zdt.toInstant()));
                return transactionRepository.getTransactions(id, null, null, null, null, timestamp, null);
            }
        });

        setupObservers();
    }

    private void setupObservers() {
        statsMediator.addSource(accountsSource, accounts -> {
            accountsResolved = true;
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

        statsMediator.addSource(categoriesSource, categories -> {
            categoriesResolved = true;
            latestCategories = categories != null ? categories : new ArrayList<>();
            allCategories.postValue(latestCategories);
            recomputeStatistics();
        });

        statsMediator.addSource(transactionsSource, transactions -> {
            transactionsResolved = true;
            allTransactions = transactions != null ? transactions : new ArrayList<>();
            recomputeStatistics();
        });

        statsMediator.addSource(membersSource, members -> {
            membersResolved = true;
            latestMembers = members != null ? members : new ArrayList<>();
            recomputeStatistics();
        });
        
        // MediatorLiveData must be observed to be active
        statsMediator.observeForever(statsObserver);
    }

    public LiveData<Result<User>> getUserData() { return userData; }
    public LiveData<Result<Family>> getFamilyData() { return familyData; }
    public LiveData<Result<Boolean>> getDataLoaded() { return dataLoaded; }
    public LiveData<Pair<Long, Long>> getDateRange() { return dateRange; }
    public LiveData<Double> getCurrentMonthIncome() { return currentMonthIncome; }
    public LiveData<Double> getCurrentMonthExpense() { return currentMonthExpense; }
    public LiveData<Double> getIncomeVariationPercentage() { return incomeVariationPercentage; }
    public LiveData<Double> getVariationPercentage() { return variationPercentage; }

    public LiveData<List<MonthlySummary>> getMonthlyEvolution() { return monthlyEvolution; }
    public LiveData<List<DashboardCategorySummary>> getCategoryDistribution() { return categoryDistribution; }

    public LiveData<List<Category>> getAllCategories() { return allCategories; }
    public LiveData<Double> getSavingsRate() { return savingsRate; }
    public LiveData<List<PaymentMethodSummary>> getPaymentMethodDistribution() { return paymentMethodDistribution; }
    public LiveData<List<Transaction>> getTopExpenses() { return topExpenses; }
    public LiveData<List<MemberSummary>> getMemberExpenseDistribution() { return memberExpenseDistribution; }
    public LiveData<List<MemberSummary>> getMemberIncomeDistribution() { return memberIncomeDistribution; }

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

    private void recomputeStatistics() {
        // Wait until all sources have emitted at least once
        if (!accountsResolved || !categoriesResolved || !transactionsResolved || !membersResolved) {
            return;
        }

        // If no active accounts, we can show success but with empty state
        if (activeAccountIds.isEmpty()) {
            dataLoaded.postValue(new Result.Success<>(true));
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
            
            compareStart = rangeStart.minusMonths(1);
            compareEnd = rangeEnd.minusMonths(1);
            hasComparison = true;
        } else {
            rangeStart = LocalDate.MIN;
            rangeEnd = LocalDate.MAX;
            compareStart = null;
            compareEnd = null;
        }

        double currentIncome = 0;
        double currentExpense = 0;
        double previousIncome = 0;
        double previousExpense = 0;

        Map<String, MonthlySummaryBuilder> monthlyMap = new TreeMap<>();
        Map<String, Double> currentCategoryTotals = new HashMap<>();
        Map<String, Double> currentMethodTotals = new HashMap<>();
        Map<String, Double> currentMemberExpenseTotals = new HashMap<>();
        Map<String, Double> currentMemberIncomeTotals = new HashMap<>();
        List<Transaction> currentExpenses = new ArrayList<>();

        for (Transaction t : activeTransactions) {
            LocalDate date = t.getDate().toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            
            if (!date.isBefore(rangeStart) && !date.isAfter(rangeEnd)) {
                if ("income".equals(t.getType())) {
                    currentIncome += t.getAmount();

                    // Member income distribution
                    String creator = t.getCreatedBy();
                    if (creator != null) {
                        currentMemberIncomeTotals.put(creator, currentMemberIncomeTotals.getOrDefault(creator, 0.0) + t.getAmount());
                    }
                } else {
                    currentExpense += t.getAmount();
                    currentExpenses.add(t);

                    // Category distribution
                    String categoryId = t.getCategoryId();
                    if (categoryId != null) {
                        currentCategoryTotals.put(categoryId, currentCategoryTotals.getOrDefault(categoryId, 0.0) + t.getAmount());
                    }

                    // Payment method distribution
                    String method = t.getPaymentMethod();
                    if (method != null) {
                        currentMethodTotals.put(method, currentMethodTotals.getOrDefault(method, 0.0) + t.getAmount());
                    }

                    // Member expense distribution
                    String creator = t.getCreatedBy();
                    if (creator != null) {
                        currentMemberExpenseTotals.put(creator, currentMemberExpenseTotals.getOrDefault(creator, 0.0) + t.getAmount());
                    }
                }
            } 
            else if (hasComparison && !date.isBefore(compareStart) && !date.isAfter(compareEnd)) {
                if ("income".equals(t.getType())) {
                    previousIncome += t.getAmount();
                } else {
                    previousExpense += t.getAmount();
                }
            }

            @SuppressLint("DefaultLocale") String monthKey = date.getYear() + "-" + String.format("%02d", date.getMonthValue());
            String monthLabel = date.getMonth().getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " + (date.getYear() % 100);

            MonthlySummaryBuilder builder = monthlyMap.computeIfAbsent(monthKey, k -> new MonthlySummaryBuilder(monthLabel));
            if ("income".equals(t.getType())) builder.income += t.getAmount();
            else builder.expense += t.getAmount();

            long millis = t.getDate().toDate().getTime();
            if (millis < builder.minMillis) builder.minMillis = millis;
            if (millis > builder.maxMillis) builder.maxMillis = millis;
        }

        currentMonthIncome.postValue(currentIncome);
        currentMonthExpense.postValue(currentExpense);
        
        // Savings rate
        if (currentIncome > 0) {
            savingsRate.postValue(((currentIncome - currentExpense) / currentIncome) * 100);
        } else {
            savingsRate.postValue(null);
        }

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

        List<MonthlySummary> evolution = new ArrayList<>();
        for (MonthlySummaryBuilder b : monthlyMap.values()) {
            evolution.add(new MonthlySummary(b.label, b.income, b.expense, b.minMillis, b.maxMillis));
        }
        monthlyEvolution.postValue(evolution);

        // Categories distribution
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
        distribution.sort((s1, s2) -> Double.compare(s2.getAmount(), s1.getAmount()));
        categoryDistribution.postValue(distribution);

        // Payment method distribution
        List<PaymentMethodSummary> methodDistribution = new ArrayList<>();
        String[] methodIds = {"tarjeta", "efectivo", "transferencia", "bizum", "tarjeta_restaurante", "tarjeta_transporte", "domiciliacion_bancaria"};
        Map<String, Integer> methodLabelRes = new HashMap<>();
        methodLabelRes.put("tarjeta", R.string.method_card);
        methodLabelRes.put("efectivo", R.string.method_cash);
        methodLabelRes.put("transferencia", R.string.method_transfer);
        methodLabelRes.put("bizum", R.string.method_bizum);
        methodLabelRes.put("tarjeta_restaurante", R.string.method_restaurant_card);
        methodLabelRes.put("tarjeta_transporte", R.string.method_transport_card);
        methodLabelRes.put("domiciliacion_bancaria", R.string.method_direct_debit);

        for (String mId : methodIds) {
            double amount = currentMethodTotals.getOrDefault(mId, 0.0);
            if (amount > 0) {
                double percentage = currentExpense > 0 ? (amount / currentExpense) * 100 : 0;
                // We use fixed labels here because getContext().getString() is not available in ViewModel
                // The Fragment will handle localization if needed, or we just pass the resId
                // For simplicity in this implementation, we'll store methodId and percentage
                methodDistribution.add(new PaymentMethodSummary(mId, "", amount, percentage));
            }
        }
        methodDistribution.sort((s1, s2) -> Double.compare(s2.getAmount(), s1.getAmount()));
        paymentMethodDistribution.postValue(methodDistribution);

        // Top Expenses
        currentExpenses.sort((t1, t2) -> Double.compare(t2.getAmount(), t1.getAmount()));
        int limit = Math.min(5, currentExpenses.size());
        topExpenses.postValue(new ArrayList<>(currentExpenses.subList(0, limit)));

        // Member distribution (Expense and Income)
        Map<String, String> memberNameMap = new HashMap<>();
        for (Member m : latestMembers) memberNameMap.put(m.getUid(), m.getDisplayName());

        List<MemberSummary> expenseDistribution = new ArrayList<>();
        for (Map.Entry<String, Double> entry : currentMemberExpenseTotals.entrySet()) {
            String name = memberNameMap.getOrDefault(entry.getKey(), "Usuario");
            double percentage = currentExpense > 0 ? (entry.getValue() / currentExpense) * 100 : 0;
            expenseDistribution.add(new MemberSummary(entry.getKey(), name, entry.getValue(), percentage));
        }
        expenseDistribution.sort((s1, s2) -> Double.compare(s2.getAmount(), s1.getAmount()));
        memberExpenseDistribution.postValue(expenseDistribution);

        List<MemberSummary> incomeDistribution = new ArrayList<>();
        for (Map.Entry<String, Double> entry : currentMemberIncomeTotals.entrySet()) {
            String name = memberNameMap.getOrDefault(entry.getKey(), "Usuario");
            double percentage = currentIncome > 0 ? (entry.getValue() / currentIncome) * 100 : 0;
            incomeDistribution.add(new MemberSummary(entry.getKey(), name, entry.getValue(), percentage));
        }
        incomeDistribution.sort((s1, s2) -> Double.compare(s2.getAmount(), s1.getAmount()));
        memberIncomeDistribution.postValue(incomeDistribution);

        dataLoaded.postValue(new Result.Success<>(true));
    }

    private static class MonthlySummaryBuilder {
        final String label;
        double income = 0;
        double expense = 0;
        long minMillis = Long.MAX_VALUE;
        long maxMillis = Long.MIN_VALUE;
        MonthlySummaryBuilder(String label) { this.label = label; }
    }

    public void init() {
        FirebaseUser currentUser = authRepository.getCurrentUser().getValue();
        if (currentUser == null) return;

        if (userListener == null) {
            // Force loading state on init
            dataLoaded.setValue(new Result.Loading<>());
            userListener = FirebaseFirestore.getInstance().collection(FirestorePaths.USERS).document(currentUser.getUid())
                    .addSnapshotListener((value, error) -> {
                        if (error != null || value == null) {
                            // Si falla por permisos al cerrar sesión, no propagamos error si ya no hay usuario
                            if (authRepository.getCurrentUser().getValue() != null) {
                                userData.postValue(new Result.Error<>(error != null ? error : new Exception("User not found")));
                            }
                            return;
                        }
                        User user = value.toObject(User.class);
                        if (user != null && user.getFamilyId() != null) {
                            userData.postValue(new Result.Success<>(user));
                            familyIdSource.postValue(user.getFamilyId());
                        }
                    });
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
        // Important: MediatorLiveData.observeForever must be removed
        statsMediator.removeObserver(statsObserver);
        statsMediator.removeSource(accountsSource);
        statsMediator.removeSource(categoriesSource);
        statsMediator.removeSource(transactionsSource);
        stopListening();
    }
}
