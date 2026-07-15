package com.finanzapp.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.finanzapp.app.data.model.Account;
import com.finanzapp.app.data.model.Category;
import com.finanzapp.app.data.model.Transaction;
import com.finanzapp.app.data.repository.AccountRepository;
import com.finanzapp.app.data.repository.CategoryRepository;
import com.finanzapp.app.data.repository.TransactionRepository;
import com.finanzapp.app.util.Result;

import java.util.List;

public class TransactionViewModel extends ViewModel {
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    
    private final MutableLiveData<Result<Boolean>> operationResult = new MutableLiveData<>();

    public TransactionViewModel(TransactionRepository transactionRepository, 
                                AccountRepository accountRepository, 
                                CategoryRepository categoryRepository) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
    }

    public LiveData<List<Transaction>> getTransactions(String familyId) {
        return transactionRepository.getTransactions(familyId);
    }

    public LiveData<List<Account>> getAccounts(String familyId) {
        return accountRepository.getAccounts(familyId);
    }

    public LiveData<List<Category>> getCategories(String familyId) {
        return categoryRepository.getCategories(familyId);
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
}
