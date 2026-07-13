package com.finanzapp.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.finanzapp.app.data.model.Account;
import com.finanzapp.app.data.repository.AccountRepository;
import com.finanzapp.app.util.Result;

import java.util.List;

public class AccountViewModel extends ViewModel {
    private final AccountRepository accountRepository;
    private final MutableLiveData<Result<Account>> createResult = new MutableLiveData<>();
    private final MutableLiveData<Result<Account>> updateResult = new MutableLiveData<>();
    private final MutableLiveData<Result<String>> deleteResult = new MutableLiveData<>();

    public AccountViewModel(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public LiveData<Result<Account>> getCreateResult() { return createResult; }
    public LiveData<Result<Account>> getUpdateResult() { return updateResult; }
    public LiveData<Result<String>> getDeleteResult() { return deleteResult; }

    public void createAccount(String familyId, Account account) {
        createResult.setValue(new Result.Loading<>());
        accountRepository.createAccount(familyId, account, result -> createResult.postValue(result));
    }

    public void updateAccount(String familyId, Account account) {
        updateResult.setValue(new Result.Loading<>());
        accountRepository.updateAccount(familyId, account, result -> updateResult.postValue(result));
    }

    public void deleteAccount(String familyId, String accountId) {
        deleteResult.setValue(new Result.Loading<>());
        accountRepository.deleteAccount(familyId, accountId, result -> deleteResult.postValue(result));
    }

    public LiveData<List<Account>> getAccounts(String familyId) {
        return accountRepository.getAccounts(familyId);
    }
}