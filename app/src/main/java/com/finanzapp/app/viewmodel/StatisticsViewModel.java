package com.finanzapp.app.viewmodel;

import android.annotation.SuppressLint;

import androidx.core.util.Pair;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.finanzapp.app.R;
import com.finanzapp.app.data.firebase.FirestorePaths;
import com.finanzapp.app.data.model.Account;
import com.finanzapp.app.data.model.Category;
import com.finanzapp.app.data.model.DashboardCategorySummary;
import com.finanzapp.app.data.model.Family;
import com.finanzapp.app.data.model.Member;
import com.finanzapp.app.data.model.MemberSummary;
import com.finanzapp.app.data.model.PaymentMethodSummary;
import com.finanzapp.app.data.model.Transaction;
import com.finanzapp.app.data.model.User;
import com.finanzapp.app.data.model.statistics.Granularity;
import com.finanzapp.app.data.model.statistics.PeriodSummary;
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
import com.google.firebase.perf.FirebasePerformance;
import com.google.firebase.perf.metrics.Trace;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
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

    private final MutableLiveData<Granularity> granularity = new MutableLiveData<>(Granularity.MONTH);
    private final MutableLiveData<List<PeriodSummary>> periodEvolution = new MutableLiveData<>();
    private final MutableLiveData<List<DashboardCategorySummary>> categoryDistribution = new MutableLiveData<>();
    private final MutableLiveData<List<PaymentMethodSummary>> paymentMethodDistribution = new MutableLiveData<>();
    private final MutableLiveData<List<Transaction>> topExpenses = new MutableLiveData<>();
    private final MutableLiveData<List<MemberSummary>> memberExpenseDistribution = new MutableLiveData<>();
    private final MutableLiveData<List<MemberSummary>> memberIncomeDistribution = new MutableLiveData<>();
    private final MutableLiveData<List<Category>> allCategories = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isPrivacyModeEnabled = new MutableLiveData<>(false);

    private final MediatorLiveData<Void> statsMediator = new MediatorLiveData<>();
    private final Observer<Void> statsObserver = v -> {};

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

        // reactive architecture to avoid leaks
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
            // Fetch everything since 2000 to cover any selection including "Total"
            LocalDate startLimit = LocalDate.of(2000, 1, 1);
            ZonedDateTime zdt = startLimit.atStartOfDay(ZoneId.systemDefault());
            Timestamp timestamp = new Timestamp(Date.from(zdt.toInstant()));
            return transactionRepository.getTransactions(id, null, null, null, null, timestamp, null);
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

        statsMediator.addSource(granularity, g -> recomputeStatistics());
        
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

    public LiveData<Granularity> getGranularity() { return granularity; }
    public LiveData<List<PeriodSummary>> getPeriodEvolution() { return periodEvolution; }
    public LiveData<List<DashboardCategorySummary>> getCategoryDistribution() { return categoryDistribution; }

    public LiveData<List<Category>> getAllCategories() { return allCategories; }
    public LiveData<Double> getSavingsRate() { return savingsRate; }
    public LiveData<Boolean> isPrivacyModeEnabled() { return isPrivacyModeEnabled; }
    public LiveData<List<PaymentMethodSummary>> getPaymentMethodDistribution() { return paymentMethodDistribution; }
    public LiveData<List<Transaction>> getTopExpenses() { return topExpenses; }
    public LiveData<List<MemberSummary>> getMemberExpenseDistribution() { return memberExpenseDistribution; }
    public LiveData<List<MemberSummary>> getMemberIncomeDistribution() { return memberIncomeDistribution; }

    public void setGranularity(Granularity g) {
        if (g != null && g != granularity.getValue()) {
            dateRange.setValue(null); // Reset range when granularity changes
            granularity.setValue(g);
        }
    }

    public void setDateRange(long start, long end) {
        dateRange.setValue(new Pair<>(start, end));
        recomputeStatistics();
    }

    public void initPrivacyMode(android.content.Context context) {
        isPrivacyModeEnabled.setValue(com.finanzapp.app.util.PreferenceUtils.isPrivacyModeEnabled(context));
    }

    private void recomputeStatistics() {
        Trace trace = FirebasePerformance.getInstance().newTrace("statistics_recompute");
        trace.start();

        // Wait until all sources have emitted at least once
        if (!accountsResolved || !categoriesResolved || !transactionsResolved || !membersResolved) {
            trace.stop();
            return;
        }

        // If no active accounts, we can show success but with empty state
        if (activeAccountIds.isEmpty()) {
            dataLoaded.postValue(new Result.Success<>(true));
            trace.stop();
            return;
        }

        List<Transaction> activeTransactions = new ArrayList<>();
        for (Transaction t : allTransactions) {
            if (activeAccountIds.contains(t.getAccountId())) {
                activeTransactions.add(t);
            }
        }

        Granularity activeGranularity = granularity.getValue();
        if (activeGranularity == null) activeGranularity = Granularity.MONTH;

        LocalDate rangeStart, rangeEnd;
        LocalDate now = LocalDate.now();

        Pair<Long, Long> customRange = dateRange.getValue();
        if (customRange != null && customRange.first != null && customRange.second != null) {
            rangeStart = Instant.ofEpochMilli(customRange.first).atZone(ZoneId.systemDefault()).toLocalDate();
            rangeEnd = Instant.ofEpochMilli(customRange.second).atZone(ZoneId.systemDefault()).toLocalDate();
        } else {
            switch (activeGranularity) {
                case DAY:
                    rangeStart = now;
                    rangeEnd = now;
                    break;
                case WEEK:
                    rangeStart = now.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
                    rangeEnd = now.with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY));
                    break;
                case MONTH:
                    rangeStart = now.with(TemporalAdjusters.firstDayOfMonth());
                    rangeEnd = now.with(TemporalAdjusters.lastDayOfMonth());
                    break;
                case YEAR:
                    rangeStart = now.with(TemporalAdjusters.firstDayOfYear());
                    rangeEnd = now.with(TemporalAdjusters.lastDayOfYear());
                    break;
                case LUSTRUM:
                    int currentLustrumStart = (now.getYear() / 5) * 5;
                    rangeStart = LocalDate.of(currentLustrumStart, 1, 1);
                    rangeEnd = LocalDate.of(currentLustrumStart + 4, 12, 31);
                    break;
                case DECADE:
                    int currentDecadeStart = (now.getYear() / 10) * 10;
                    rangeStart = LocalDate.of(currentDecadeStart, 1, 1);
                    rangeEnd = LocalDate.of(currentDecadeStart + 9, 12, 31);
                    break;
                case TOTAL:
                    rangeStart = LocalDate.of(2000, 1, 1);
                    rangeEnd = LocalDate.of(2100, 12, 31);
                    break;
                default:
                    rangeStart = now.with(TemporalAdjusters.firstDayOfMonth());
                    rangeEnd = now.with(TemporalAdjusters.lastDayOfMonth());
                    break;
            }
            // Update the dateRange LiveData with default range
            long startMillis = rangeStart.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            long endMillis = rangeEnd.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            dateRange.postValue(new Pair<>(startMillis, endMillis));
        }

        Map<String, Double> currentCategoryTotals = new HashMap<>();
        Map<String, Double> currentMethodTotals = new HashMap<>();
        Map<String, Double> currentMemberExpenseTotals = new HashMap<>();
        Map<String, Double> currentMemberIncomeTotals = new HashMap<>();
        List<Transaction> currentExpenses = new ArrayList<>();

        // Evolution calculation
        Map<String, PeriodSummaryBuilder> evolutionMap = new TreeMap<>();
        LocalDate evolutionEnd, evolutionStart;
        Granularity evolutionGranularity = activeGranularity;

        if (customRange != null && customRange.first != null && customRange.second != null) {
            // If manual range, always compare month by month
            evolutionGranularity = Granularity.MONTH;
            evolutionStart = rangeStart.with(TemporalAdjusters.firstDayOfMonth());
            evolutionEnd = rangeEnd.with(TemporalAdjusters.lastDayOfMonth());
        } else {
            // Default behavior: Last 7 buckets
            evolutionEnd = now;
            switch (activeGranularity) {
                case DAY: evolutionStart = now.minusDays(6); break;
                case WEEK: evolutionStart = now.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).minusWeeks(6); break;
                case YEAR: evolutionStart = now.minusYears(6).with(TemporalAdjusters.firstDayOfYear()); break;
                case LUSTRUM: evolutionStart = now.minusYears(30).with(TemporalAdjusters.firstDayOfYear()); break;
                case DECADE: evolutionStart = now.minusYears(60).with(TemporalAdjusters.firstDayOfYear()); break;
                case TOTAL: evolutionStart = LocalDate.of(2000, 1, 1); break;
                case MONTH:
                default: evolutionStart = now.minusMonths(6).with(TemporalAdjusters.firstDayOfMonth()); break;
            }
        }

        for (Transaction t : activeTransactions) {
            LocalDate date = t.getDate().toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            
            // Bucket aggregation logic (for chart)
            String bucketKey;
            String bucketLabel;
            LocalDate bucketStart, bucketEnd;

            switch (evolutionGranularity) {
                case DAY:
                    bucketKey = date.toString();
                    bucketLabel = date.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM", Locale.getDefault()));
                    bucketStart = date;
                    bucketEnd = date;
                    break;
                case WEEK:
                    LocalDate monday = date.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
                    bucketKey = "W" + monday.toString();
                    bucketLabel = monday.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM", Locale.getDefault()));
                    bucketStart = monday;
                    bucketEnd = monday.plusDays(6);
                    break;
                case YEAR:
                    bucketKey = String.valueOf(date.getYear());
                    bucketLabel = bucketKey;
                    bucketStart = date.with(TemporalAdjusters.firstDayOfYear());
                    bucketEnd = date.with(TemporalAdjusters.lastDayOfYear());
                    break;
                case LUSTRUM:
                    int lustrumStart = (date.getYear() / 5) * 5;
                    bucketKey = "L" + lustrumStart;
                    bucketLabel = lustrumStart + "-" + (lustrumStart + 4);
                    bucketStart = LocalDate.of(lustrumStart, 1, 1);
                    bucketEnd = LocalDate.of(lustrumStart + 4, 12, 31);
                    break;
                case DECADE:
                    int decadeStart = (date.getYear() / 10) * 10;
                    bucketKey = "D" + decadeStart;
                    bucketLabel = decadeStart + "-" + (decadeStart + 9);
                    bucketStart = LocalDate.of(decadeStart, 1, 1);
                    bucketEnd = LocalDate.of(decadeStart + 9, 12, 31);
                    break;
                case TOTAL:
                    bucketKey = "TOTAL";
                    bucketLabel = "Total";
                    bucketStart = LocalDate.of(2000, 1, 1);
                    bucketEnd = LocalDate.of(2100, 12, 31);
                    break;
                case MONTH:
                default:
                    @SuppressLint("DefaultLocale") String monthKey = date.getYear() + "-" + String.format("%02d", date.getMonthValue());
                    bucketKey = monthKey;
                    bucketLabel = date.getMonth().getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " + (date.getYear() % 100);
                    bucketStart = date.with(TemporalAdjusters.firstDayOfMonth());
                    bucketEnd = date.with(TemporalAdjusters.lastDayOfMonth());
                    break;
            }

            // Fill evolution chart if within historical range
            if (!date.isBefore(evolutionStart) && !date.isAfter(evolutionEnd)) {
                PeriodSummaryBuilder builder = evolutionMap.computeIfAbsent(bucketKey, k -> new PeriodSummaryBuilder(bucketLabel));
                if ("income".equals(t.getType())) builder.income += t.getAmount();
                else builder.expense += t.getAmount();
                builder.minMillis = bucketStart.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
                builder.maxMillis = bucketEnd.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            }

            // Totals within the selected RANGE (for summary cards and distribution)
            if (!date.isBefore(rangeStart) && !date.isAfter(rangeEnd)) {
                if ("income".equals(t.getType())) {
                    String creator = t.getCreatedBy();
                    if (creator != null) {
                        currentMemberIncomeTotals.put(creator, currentMemberIncomeTotals.getOrDefault(creator, 0.0) + t.getAmount());
                    }
                } else {
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

                    // Member distribution
                    String creator = t.getCreatedBy();
                    if (creator != null) {
                        currentMemberExpenseTotals.put(creator, currentMemberExpenseTotals.getOrDefault(creator, 0.0) + t.getAmount());
                    }
                }
            }
        }

        // Totals of selected range
        double currentIncomeTotal = 0;
        double currentExpenseTotal = 0;
        for (Transaction t : activeTransactions) {
            LocalDate date = t.getDate().toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            if (!date.isBefore(rangeStart) && !date.isAfter(rangeEnd)) {
                if ("income".equals(t.getType())) currentIncomeTotal += t.getAmount();
                else currentExpenseTotal += t.getAmount();
            }
        }

        currentMonthIncome.postValue(currentIncomeTotal);
        currentMonthExpense.postValue(currentExpenseTotal);
        
        List<PeriodSummary> evolution = new ArrayList<>();
        List<String> sortedKeys = new ArrayList<>(evolutionMap.keySet());
        Collections.sort(sortedKeys);
        for (String k : sortedKeys) {
            PeriodSummaryBuilder b = evolutionMap.get(k);
            if (b != null) {
                evolution.add(new PeriodSummary(b.label, b.income, b.expense, b.minMillis, b.maxMillis));
            }
        }
        
        // Show evolution for all buckets in the selected range
        periodEvolution.postValue(evolution);

        if (activeGranularity == Granularity.TOTAL || evolution.isEmpty()) {
            incomeVariationPercentage.postValue(null);
            variationPercentage.postValue(null);
        } else {
            // Calculation of previous period for comparison
            LocalDate prevStart, prevEnd;
            long diffDays = java.time.temporal.ChronoUnit.DAYS.between(rangeStart, rangeEnd) + 1;
            prevStart = rangeStart.minusDays(diffDays);
            prevEnd = rangeEnd.minusDays(diffDays);

            double prevIncome = 0;
            double prevExpense = 0;

            for (Transaction t : activeTransactions) {
                LocalDate date = t.getDate().toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                if (!date.isBefore(prevStart) && !date.isAfter(prevEnd)) {
                    if ("income".equals(t.getType())) prevIncome += t.getAmount();
                    else prevExpense += t.getAmount();
                }
            }

            if (prevIncome > 0) {
                incomeVariationPercentage.postValue(((currentIncomeTotal - prevIncome) / prevIncome) * 100);
            } else {
                incomeVariationPercentage.postValue(currentIncomeTotal > 0 ? 100.0 : 0.0);
            }

            if (prevExpense > 0) {
                variationPercentage.postValue(((currentExpenseTotal - prevExpense) / prevExpense) * 100);
            } else {
                variationPercentage.postValue(currentExpenseTotal > 0 ? 100.0 : 0.0);
            }
        }

        // Savings rate based on selected range
        if (currentIncomeTotal > 0) {
            savingsRate.postValue(((currentIncomeTotal - currentExpenseTotal) / currentIncomeTotal) * 100);
        } else {
            savingsRate.postValue(null);
        }

        // Categories distribution
        List<DashboardCategorySummary> distribution = new ArrayList<>();
        Map<String, Category> catMap = new HashMap<>();
        for (Category c : latestCategories) catMap.put(c.getId(), c);

        for (Map.Entry<String, Double> entry : currentCategoryTotals.entrySet()) {
            Category cat = catMap.get(entry.getKey());
            String name = cat != null ? cat.getName() : "Otros";
            String color = cat != null ? cat.getColor() : "#808080";
            double percentage = currentExpenseTotal > 0 ? (entry.getValue() / currentExpenseTotal) * 100 : 0;
            distribution.add(new DashboardCategorySummary(entry.getKey(), name, color, entry.getValue(), percentage));
        }
        distribution.sort((s1, s2) -> Double.compare(s2.getAmount(), s1.getAmount()));
        categoryDistribution.postValue(distribution);

        // Payment method distribution
        List<PaymentMethodSummary> methodDistribution = new ArrayList<>();
        String[] methodIds = {"tarjeta", "efectivo", "transferencia", "bizum", "tarjeta_restaurante", "tarjeta_transporte", "domiciliacion_bancaria"};

        for (String mId : methodIds) {
            double amount = currentMethodTotals.getOrDefault(mId, 0.0);
            if (amount > 0) {
                double percentage = currentExpenseTotal > 0 ? (amount / currentExpenseTotal) * 100 : 0;
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
            double percentage = currentExpenseTotal > 0 ? (entry.getValue() / currentExpenseTotal) * 100 : 0;
            expenseDistribution.add(new MemberSummary(entry.getKey(), name, entry.getValue(), percentage));
        }
        expenseDistribution.sort((s1, s2) -> Double.compare(s2.getAmount(), s1.getAmount()));
        memberExpenseDistribution.postValue(expenseDistribution);

        List<MemberSummary> incomeDistribution = new ArrayList<>();
        for (Map.Entry<String, Double> entry : currentMemberIncomeTotals.entrySet()) {
            String name = memberNameMap.getOrDefault(entry.getKey(), "Usuario");
            double percentage = currentIncomeTotal > 0 ? (entry.getValue() / currentIncomeTotal) * 100 : 0;
            incomeDistribution.add(new MemberSummary(entry.getKey(), name, entry.getValue(), percentage));
        }
        incomeDistribution.sort((s1, s2) -> Double.compare(s2.getAmount(), s1.getAmount()));
        memberIncomeDistribution.postValue(incomeDistribution);

        dataLoaded.postValue(new Result.Success<>(true));
        trace.stop();
    }

    private static class PeriodSummaryBuilder {
        final String label;
        double income = 0;
        double expense = 0;
        long minMillis = Long.MAX_VALUE;
        long maxMillis = Long.MIN_VALUE;
        PeriodSummaryBuilder(String label) { this.label = label; }
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
        statsMediator.removeSource(membersSource);
        stopListening();
    }
}
