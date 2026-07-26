package com.finanzapp.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

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
    private String filterAccountId = null;
    private String filterCategoryId = null;
    private java.util.List<String> filterCategoryIds = null;
    private String filterType = null;
    private String filterMethod = null;
    private com.google.firebase.Timestamp filterStartDate = null;
    private com.google.firebase.Timestamp filterEndDate = null;

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
        // Handle multicategory filter if set
        java.util.List<String> catIds = null;
        if (categoryId != null && !categoryId.isEmpty()) {
            catIds = java.util.Collections.singletonList(categoryId);
        } else if (filterCategoryIds != null && !filterCategoryIds.isEmpty()) {
            catIds = filterCategoryIds;
        }

        return transactionRepository.getTransactions(familyId, accountId, catIds, type, paymentMethod, start, end);
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
}