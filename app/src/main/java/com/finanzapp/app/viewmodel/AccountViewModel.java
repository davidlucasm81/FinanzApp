package com.finanzapp.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.finanzapp.app.data.model.Account;
import com.finanzapp.app.data.repository.AccountRepository;
import com.finanzapp.app.util.Result;
import com.finanzapp.app.util.SingleLiveEvent;

import java.util.List;

public class AccountViewModel extends ViewModel {
    private final AccountRepository accountRepository;
    private final SingleLiveEvent<Result<Account>> createResult = new SingleLiveEvent<>();
    private final SingleLiveEvent<Result<Account>> updateResult = new SingleLiveEvent<>();
    private final SingleLiveEvent<Result<String>> archiveResult = new SingleLiveEvent<>();
    private final SingleLiveEvent<Result<String>> deleteResult = new SingleLiveEvent<>();

    public AccountViewModel(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public LiveData<Result<Account>> getCreateResult() { return createResult; }
    public LiveData<Result<Account>> getUpdateResult() { return updateResult; }
    public LiveData<Result<String>> getArchiveResult() { return archiveResult; }
    public LiveData<Result<String>> getDeleteResult() { return deleteResult; }

    public void createAccount(String familyId, Account account) {
        createResult.setValue(new Result.Loading<>());
        accountRepository.createAccount(familyId, account, result -> createResult.postValue(result));
    }

    public void updateAccount(String familyId, Account account) {
        updateResult.setValue(new Result.Loading<>());
        accountRepository.updateAccount(familyId, account, result -> updateResult.postValue(result));
    }

    public void archiveAccount(String familyId, String accountId, boolean active) {
        archiveResult.setValue(new Result.Loading<>());
        accountRepository.archiveAccount(familyId, accountId, active, result -> archiveResult.postValue(result));
    }

    public void deleteAccount(String familyId, String accountId) {
        deleteResult.setValue(new Result.Loading<>());
        accountRepository.deleteAccount(familyId, accountId, result -> deleteResult.postValue(result));
    }

    private String lastAccountsFamilyId;
    private LiveData<List<Account>> lastAccountsLiveData;

    public LiveData<List<Account>> getAccounts(String familyId) {
        if (lastAccountsLiveData == null || !familyId.equals(lastAccountsFamilyId)) {
            lastAccountsFamilyId = familyId;
            lastAccountsLiveData = accountRepository.getAccountsWithTransactionStatus(familyId);
        }
        return lastAccountsLiveData;
    }
}