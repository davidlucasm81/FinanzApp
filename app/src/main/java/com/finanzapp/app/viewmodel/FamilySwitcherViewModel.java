package com.finanzapp.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.finanzapp.app.data.model.FamilyMembership;
import com.finanzapp.app.data.repository.AuthRepository;
import com.finanzapp.app.data.repository.FamilyRepository;
import com.finanzapp.app.util.Result;
import com.finanzapp.app.util.SingleLiveEvent;

import java.util.List;

public class FamilySwitcherViewModel extends ViewModel {
    private final AuthRepository authRepository;
    private final FamilyRepository familyRepository;
    private final MutableLiveData<Result<List<FamilyMembership>>> memberships = new MutableLiveData<>();
    private final SingleLiveEvent<Result<Boolean>> switchResult = new SingleLiveEvent<>();
    private final SingleLiveEvent<Result<Boolean>> archiveResult = new SingleLiveEvent<>();
    private final MutableLiveData<String> currentFamilyId = new MutableLiveData<>();

    public FamilySwitcherViewModel(AuthRepository authRepository, FamilyRepository familyRepository) {
        this.authRepository = authRepository;
        this.familyRepository = familyRepository;
    }

    public LiveData<Result<List<FamilyMembership>>> getMemberships() {
        return memberships;
    }

    public LiveData<Result<Boolean>> getSwitchResult() {
        return switchResult;
    }

    public LiveData<Result<Boolean>> getArchiveResult() {
        return archiveResult;
    }

    public LiveData<String> getCurrentFamilyId() {
        return currentFamilyId;
    }

    public void fetchMemberships() {
        String uid = authRepository.getUid();
        if (uid == null) return;

        memberships.setValue(new Result.Loading<>());
        familyRepository.getUserFamilies(uid, memberships::postValue);
    }

    public void fetchCurrentFamilyId() {
        String uid = authRepository.getUid();
        if (uid == null) return;

        authRepository.getUser(uid, result -> {
            if (result instanceof Result.Success) {
                currentFamilyId.postValue(((Result.Success<com.finanzapp.app.data.model.User>) result).getData().getFamilyId());
            }
        });
    }

    public void switchFamily(String familyId) {
        String uid = authRepository.getUid();
        if (uid == null) return;

        switchResult.setValue(new Result.Loading<>());
        familyRepository.switchActiveFamily(uid, familyId, switchResult::postValue);
    }

    public void setArchived(String familyId, boolean archived) {
        String uid = authRepository.getUid();
        if (uid == null) return;

        archiveResult.setValue(new Result.Loading<>());
        familyRepository.setMembershipArchived(uid, familyId, archived, archiveResult::postValue);
    }
}
