package com.finanzapp.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import android.util.Log;

import com.finanzapp.app.data.model.Account;
import com.finanzapp.app.data.model.Category;
import com.finanzapp.app.data.model.Family;
import com.finanzapp.app.data.model.Member;
import com.finanzapp.app.data.model.Transaction;
import com.finanzapp.app.data.repository.AccountRepository;
import com.finanzapp.app.data.repository.AuthRepository;
import com.finanzapp.app.data.repository.CategoryRepository;
import com.finanzapp.app.data.repository.FamilyRepository;
import com.finanzapp.app.data.repository.TransactionRepository;
import com.finanzapp.app.util.Result;
import com.finanzapp.app.util.SingleLiveEvent;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.List;

public class TransactionViewModel extends ViewModel {
    private final AuthRepository authRepository;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final FamilyRepository familyRepository;

    private final SingleLiveEvent<Result<Boolean>> operationResult = new SingleLiveEvent<>();
    private final MutableLiveData<List<com.finanzapp.app.data.model.Member>> members = new MutableLiveData<>();
    private ListenerRegistration membersListener;

    // Filter state
    public static class TransactionFilters {
        public final String familyId;
        public final String accountId;
        public final String categoryId;
        public final java.util.List<String> categoryIds;
        public final String type;
        public final String method;
        public final com.google.firebase.Timestamp startDate;
        public final com.google.firebase.Timestamp endDate;

        public TransactionFilters(String familyId, String accountId, String categoryId, java.util.List<String> categoryIds,
                                  String type, String method, com.google.firebase.Timestamp startDate, com.google.firebase.Timestamp endDate) {
            this.familyId = familyId;
            this.accountId = accountId;
            this.categoryId = categoryId;
            this.categoryIds = categoryIds;
            this.type = type;
            this.method = method;
            this.startDate = startDate;
            this.endDate = endDate;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TransactionFilters that = (TransactionFilters) o;
            return java.util.Objects.equals(familyId, that.familyId) &&
                    java.util.Objects.equals(accountId, that.accountId) &&
                    java.util.Objects.equals(categoryId, that.categoryId) &&
                    java.util.Objects.equals(categoryIds, that.categoryIds) &&
                    java.util.Objects.equals(type, that.type) &&
                    java.util.Objects.equals(method, that.method) &&
                    java.util.Objects.equals(startDate, that.startDate) &&
                    java.util.Objects.equals(endDate, that.endDate);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(familyId, accountId, categoryId, categoryIds, type, method, startDate, endDate);
        }
    }

    private final MutableLiveData<TransactionFilters> filters = new MutableLiveData<>();
    private final LiveData<List<Transaction>> transactions;
    private final MediatorLiveData<List<Transaction>> visibleTransactions = new MediatorLiveData<>();

    private String filterAccountId = null;
    private String filterCategoryId = null;
    private java.util.List<String> filterCategoryIds = null;
    private String filterType = null;
    private String filterMethod = null;
    private com.google.firebase.Timestamp filterStartDate = null;
    private com.google.firebase.Timestamp filterEndDate = null;
    private String preselectedMemberUid = null;
    private java.util.Set<String> archivedAccountIds = new java.util.HashSet<>();

    private final MutableLiveData<Boolean> isPrivacyModeEnabled = new MutableLiveData<>(false);

    // Referencia estable para poder registrar/desregistrar el mismo Runnable
    private final Runnable signOutCleanup = this::stopListening;

    public TransactionViewModel(AuthRepository authRepository,
                                TransactionRepository transactionRepository,
                                AccountRepository accountRepository,
                                CategoryRepository categoryRepository,
                                FamilyRepository familyRepository) {
        this.authRepository = authRepository;
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
        this.familyRepository = familyRepository;
        authRepository.registerPreSignOutCleanup(signOutCleanup);

        transactions = Transformations.switchMap(filters, f -> {
            if (f.familyId == null) return new MutableLiveData<>(new java.util.ArrayList<>());
            java.util.List<String> catIds = null;
            if (f.categoryId != null && !f.categoryId.isEmpty()) {
                catIds = java.util.Collections.singletonList(f.categoryId);
            } else if (f.categoryIds != null && !f.categoryIds.isEmpty()) {
                catIds = f.categoryIds;
            }
            return transactionRepository.getTransactions(f.familyId, f.accountId, catIds, f.type, f.method, f.startDate, f.endDate);
        });

        visibleTransactions.addSource(transactions, this::computeVisibleTransactions);
    }

    private void computeVisibleTransactions(List<Transaction> allTransactions) {
        if (allTransactions == null) {
            visibleTransactions.setValue(null);
            return;
        }
        java.util.List<Transaction> filtered = new java.util.ArrayList<>();
        for (Transaction t : allTransactions) {
            if (!archivedAccountIds.contains(t.getAccountId())) {
                if (preselectedMemberUid != null && !preselectedMemberUid.equals(t.getCreatedBy())) {
                    continue;
                }
                filtered.add(t);
            }
        }
        visibleTransactions.setValue(filtered);
    }

    public void updateFilters(String familyId) {
        TransactionFilters newFilters = new TransactionFilters(familyId, filterAccountId, filterCategoryId, filterCategoryIds, filterType, filterMethod, filterStartDate, filterEndDate);
        TransactionFilters currentFilters = filters.getValue();
        
        if (newFilters.equals(currentFilters)) {
            Log.d("TransactionList", "Filters identical, skipping update");
        } else {
            Log.d("TransactionList", "Filters changed, posting new value");
            filters.setValue(newFilters);
        }
    }

    public LiveData<List<Transaction>> getVisibleTransactions() {
        return visibleTransactions;
    }

    public void setArchivedAccountIds(java.util.Set<String> ids) {
        this.archivedAccountIds = ids;
        computeVisibleTransactions(transactions.getValue());
    }

    public void setPreselectedMemberUid(String uid) {
        this.preselectedMemberUid = uid;
        computeVisibleTransactions(transactions.getValue());
    }

    private void stopListening() {
        if (membersListener != null) {
            membersListener.remove();
            membersListener = null;
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        authRepository.unregisterPreSignOutCleanup(signOutCleanup);
        stopListening();
    }

    public LiveData<List<Transaction>> getFilteredTransactions(String familyId, String accountId, String categoryId, String type, String paymentMethod, com.google.firebase.Timestamp start, com.google.firebase.Timestamp end) {
        // Redirigir a la nueva lógica reactiva para asegurar coherencia
        this.filterAccountId = accountId;
        this.filterCategoryId = categoryId;
        this.filterType = type;
        this.filterMethod = paymentMethod;
        this.filterStartDate = start;
        this.filterEndDate = end;
        updateFilters(familyId);
        return visibleTransactions;
    }

    public LiveData<List<Member>> getMembers(String familyId) {
        if (membersListener != null) membersListener.remove();
        membersListener = familyRepository.getMembers(familyId, result -> {
            if (result instanceof Result.Success) {
                members.setValue(((Result.Success<List<Member>>) result).getData());
            }
        });
        return members;
    }

    public LiveData<List<Account>> getAccounts(String familyId) {
        return accountRepository.getAccounts(familyId);
    }

    public LiveData<List<Category>> getCategories(String familyId) {
        return categoryRepository.getCategories(familyId);
    }

    public LiveData<Result<Family>> getFamilyData(String familyId) {
        MutableLiveData<Result<Family>> result = new MutableLiveData<>();
        familyRepository.getFamily(familyId, result::postValue);
        return result;
    }

    public LiveData<Result<Boolean>> getOperationResult() {
        return operationResult;
    }

    public LiveData<Boolean> isPrivacyModeEnabled() {
        return isPrivacyModeEnabled;
    }

    public TransactionFilters getFiltersValue() {
        return filters.getValue();
    }

    public void initPrivacyMode(android.content.Context context) {
        isPrivacyModeEnabled.setValue(com.finanzapp.app.util.PreferenceUtils.isPrivacyModeEnabled(context));
    }

    public void addTransaction(String familyId, Transaction transaction) {
        transactionRepository.addTransaction(familyId, transaction, operationResult::setValue);
    }

    public void updateTransaction(String familyId, Transaction oldTransaction, Transaction newTransaction) {
        transactionRepository.updateTransaction(familyId, oldTransaction, newTransaction, operationResult::setValue);
    }

    public void deleteTransaction(String familyId, Transaction transaction) {
        transactionRepository.deleteTransaction(familyId, transaction, operationResult::setValue);
    }

    // Filter getters and setters
    public String getFilterAccountId() { return filterAccountId; }
    public void setFilterAccountId(String filterAccountId) { this.filterAccountId = filterAccountId; }

    public String getFilterCategoryId() { return filterCategoryId; }
    public void setFilterCategoryId(String filterCategoryId) { 
        this.filterCategoryId = filterCategoryId; 
        if (filterCategoryId != null) this.filterCategoryIds = null; // Clear multi if single set
    }

    public void setFilterCategoryIds(java.util.List<String> filterCategoryIds) {
        this.filterCategoryIds = filterCategoryIds; 
        if (filterCategoryIds != null) this.filterCategoryId = null; // Clear single if multi set
    }

    public String getFilterType() { return filterType; }
    public void setFilterType(String filterType) { this.filterType = filterType; }

    public String getFilterMethod() { return filterMethod; }
    public void setFilterMethod(String filterMethod) { this.filterMethod = filterMethod; }

    public com.google.firebase.Timestamp getFilterStartDate() { return filterStartDate; }
    public void setFilterStartDate(com.google.firebase.Timestamp filterStartDate) { this.filterStartDate = filterStartDate; }

    public com.google.firebase.Timestamp getFilterEndDate() { return filterEndDate; }
    public void setFilterEndDate(com.google.firebase.Timestamp filterEndDate) { this.filterEndDate = filterEndDate; }

    public String getCurrentUserId() {
        return authRepository.isLoggedIn() && authRepository.getCurrentUser().getValue() != null 
                ? authRepository.getCurrentUser().getValue().getUid() : null;
    }
}