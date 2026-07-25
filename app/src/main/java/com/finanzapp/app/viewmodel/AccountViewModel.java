package com.finanzapp.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.finanzapp.app.data.model.Account;
import com.finanzapp.app.data.repository.AccountRepository;
import com.finanzapp.app.data.repository.AuthRepository;
import com.finanzapp.app.util.Result;
import com.finanzapp.app.util.SingleLiveEvent;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.finanzapp.app.data.firebase.FirestorePaths;

import java.util.List;

public class AccountViewModel extends ViewModel {
    private final AuthRepository authRepository;
    private final AccountRepository accountRepository;
    private final SingleLiveEvent<Result<Account>> createResult = new SingleLiveEvent<>();
    private final SingleLiveEvent<Result<Account>> updateResult = new SingleLiveEvent<>();
    private final SingleLiveEvent<Result<String>> archiveResult = new SingleLiveEvent<>();
    private final SingleLiveEvent<Result<String>> deleteResult = new SingleLiveEvent<>();
    private final MutableLiveData<Boolean> isAdmin = new MutableLiveData<>(false);
    private ListenerRegistration roleListener;

    // Referencia estable para poder registrar/desregistrar el mismo Runnable
    private final Runnable signOutCleanup = this::stopListening;

    public AccountViewModel(AuthRepository authRepository, AccountRepository accountRepository) {
        this.authRepository = authRepository;
        this.accountRepository = accountRepository;
        authRepository.registerPreSignOutCleanup(signOutCleanup);
    }

    private void stopListening() {
        if (roleListener != null) {
            roleListener.remove();
            roleListener = null;
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        authRepository.unregisterPreSignOutCleanup(signOutCleanup);
        stopListening();
    }

    public LiveData<Result<Account>> getCreateResult() { return createResult; }
    public LiveData<Result<Account>> getUpdateResult() { return updateResult; }
    public LiveData<Result<String>> getArchiveResult() { return archiveResult; }
    public LiveData<Result<String>> getDeleteResult() { return deleteResult; }
    public LiveData<Boolean> getIsAdmin() { return isAdmin; }

    public void checkAdminRole(String familyId) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null || familyId == null) return;

        if (roleListener != null) roleListener.remove();

        roleListener = FirebaseFirestore.getInstance()
                .collection(FirestorePaths.getFamilyPath(familyId) + "/members")
                .document(uid)
                .addSnapshotListener((doc, error) -> {
                    if (error != null || doc == null || !doc.exists()) {
                        // Avoid posting error if we are signing out (Permission Denied is expected)
                        return;
                    }
                    String role = doc.getString("role");
                    isAdmin.postValue("admin".equals(role) || "owner".equals(role));
                });
    }

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