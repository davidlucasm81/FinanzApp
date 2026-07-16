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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DashboardViewModel extends ViewModel {
    private final AuthRepository authRepository;
    private final FamilyRepository familyRepository;
    private final AccountRepository accountRepository = new AccountRepository();
    private final TransactionRepository transactionRepository = new TransactionRepository();
    private final CategoryRepository categoryRepository = new CategoryRepository();

    private final MutableLiveData<Result<Family>> familyData = new MutableLiveData<>();
    private final MutableLiveData<Result<User>> userData = new MutableLiveData<>();
    private final MutableLiveData<Pair<Long, Long>> dateRange = new MutableLiveData<>();
    private final MutableLiveData<Double> netBalance = new MutableLiveData<>(0.0);
    private final MutableLiveData<Double> totalIncome = new MutableLiveData<>(0.0);
    private final MutableLiveData<Double> totalExpense = new MutableLiveData<>(0.0);
    private final MutableLiveData<List<Account>> accountsList = new MutableLiveData<>();
    private final MutableLiveData<List<DashboardCategorySummary>> categoryBreakdown = new MutableLiveData<>();

    private ListenerRegistration userListener;

    public DashboardViewModel(AuthRepository authRepository, FamilyRepository familyRepository) {
        this.authRepository = authRepository;
        this.familyRepository = familyRepository;
        
        // Default range: current month
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        long start = cal.getTimeInMillis();
        
        cal.add(Calendar.MONTH, 1);
        cal.add(Calendar.SECOND, -1);
        long end = cal.getTimeInMillis();
        
        dateRange.setValue(new Pair<>(start, end));
    }

    public LiveData<Result<Family>> getFamilyData() { return familyData; }
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
        fetchDashboardData();
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
                                fetchFamilyData(user.getFamilyId());
                            }
                        }
                    });
        } else {
            // Already listening to user, just refresh family-related data if we have familyId
            Result<User> currentResult = userData.getValue();
            if (currentResult instanceof Result.Success) {
                User user = ((Result.Success<User>) currentResult).getData();
                if (user.getFamilyId() != null) {
                    fetchFamilyData(user.getFamilyId());
                }
            }
        }
    }

    private void fetchFamilyData(String familyId) {
        familyRepository.getFamily(familyId, familyData::postValue);

        // Accounts list and net balance (always current)
        accountRepository.getAccounts(familyId).observeForever(accounts -> {
            if (accounts != null) {
                accountsList.postValue(accounts);
                double total = 0;
                for (Account account : accounts) {
                    if (account.isActive()) {
                        total += account.getCurrentBalance();
                    }
                }
                netBalance.postValue(total);
            }
        });

        // Transactions and breakdown for the selected range
        Pair<Long, Long> range = dateRange.getValue();
        Timestamp start = null;
        Timestamp end = null;
        
        if (range != null) {
            start = new Timestamp(new Date(range.first));
            end = new Timestamp(new Date(range.second));
        }

        LiveData<List<Transaction>> transactionsRange = transactionRepository.getTransactions(familyId, null, null, null, null, start, end);
        LiveData<List<Category>> categories = categoryRepository.getCategories(familyId);

        // Combine categories and transactions to build the breakdown
        categories.observeForever(categoryList -> {
            transactionsRange.observeForever(transactions -> {
                processTransactions(transactions, categoryList);
            });
        });
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
