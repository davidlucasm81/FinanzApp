package com.finanzapp.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.finanzapp.app.data.model.Family;
import com.finanzapp.app.data.repository.FamilyRepository;
import com.finanzapp.app.util.Result;

public class OnboardingViewModel extends ViewModel {
    private final FamilyRepository familyRepository;
    private final MutableLiveData<Result<Family>> createFamilyResult = new MutableLiveData<>();
    private final MutableLiveData<Result<Boolean>> joinByCodeResult = new MutableLiveData<>();

    public OnboardingViewModel(FamilyRepository familyRepository) {
        this.familyRepository = familyRepository;
    }

    public LiveData<Result<Family>> getCreateFamilyResult() {
        return createFamilyResult;
    }

    public LiveData<Result<Boolean>> getJoinByCodeResult() {
        return joinByCodeResult;
    }

    public void createFamily(String name, String currencyCode) {
        createFamilyResult.setValue(new Result.Loading<>());
        familyRepository.createFamily(name, currencyCode, createFamilyResult::postValue);
    }

    public void joinByCode(String code) {
        joinByCodeResult.setValue(new Result.Loading<>());
        familyRepository.joinByCode(code, joinByCodeResult::postValue);
    }
}
