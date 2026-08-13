package com.finanzapp.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.finanzapp.app.data.model.Family;
import com.finanzapp.app.data.model.User;
import com.finanzapp.app.data.repository.AuthRepository;
import com.finanzapp.app.data.repository.FamilyRepository;
import com.finanzapp.app.util.Result;
import com.google.firebase.auth.FirebaseUser;

public class MainViewModel extends ViewModel {
    private final AuthRepository authRepository;
    private final FamilyRepository familyRepository;
    private final LiveData<Result<Family>> activeFamily;

    public MainViewModel(AuthRepository authRepository, FamilyRepository familyRepository) {
        this.authRepository = authRepository;
        this.familyRepository = familyRepository;

        LiveData<FirebaseUser> firebaseUser = authRepository.getCurrentUser();
        
        // This is a bit simplified. In a real app we might need a more robust way to get familyId.
        // We'll use the user's familyId field.
        
        activeFamily = Transformations.switchMap(firebaseUser, user -> {
            if (user == null) return new MutableLiveData<>(null);
            
            MutableLiveData<Result<Family>> result = new MutableLiveData<>();
            // We need to fetch the user doc first to get familyId
            // Or we assume the repository has a way to get active family.
            
            // For now, let's just expose a method to fetch it when familyId changes.
            return result;
        });
    }

    public LiveData<Result<Family>> getActiveFamily(String familyId) {
        MutableLiveData<Result<Family>> result = new MutableLiveData<>();
        if (familyId != null) {
            familyRepository.getFamily(familyId, result::postValue);
        } else {
            result.setValue(null);
        }
        return result;
    }
}
