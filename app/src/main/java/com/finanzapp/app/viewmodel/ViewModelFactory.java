package com.finanzapp.app.viewmodel;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.finanzapp.app.data.repository.AuthRepository;
import com.finanzapp.app.data.repository.FamilyRepository;
import com.finanzapp.app.data.repository.AccountRepository;

public class ViewModelFactory implements ViewModelProvider.Factory {
    private final AuthRepository authRepository;
    private final FamilyRepository familyRepository;
    private final AccountRepository accountRepository;

    public ViewModelFactory(AuthRepository authRepository, FamilyRepository familyRepository) {
        this(authRepository, familyRepository, null);
    }

    public ViewModelFactory(AuthRepository authRepository, FamilyRepository familyRepository, AccountRepository accountRepository) {
        this.authRepository = authRepository;
        this.familyRepository = familyRepository;
        this.accountRepository = accountRepository;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(AuthViewModel.class)) {
            return (T) new AuthViewModel(authRepository);
        } else if (modelClass.isAssignableFrom(OnboardingViewModel.class)) {
            return (T) new OnboardingViewModel(familyRepository);
        } else if (modelClass.isAssignableFrom(AccountViewModel.class)) {
            if (accountRepository == null) throw new IllegalArgumentException("AccountRepository not provided to ViewModelFactory");
            return (T) new AccountViewModel(accountRepository);
        } else if (modelClass.isAssignableFrom(FamilyViewModel.class)) {
            return (T) new FamilyViewModel(familyRepository);
        } else if (modelClass.isAssignableFrom(SettingsViewModel.class)) {
            return (T) new SettingsViewModel(authRepository);
        } else if (modelClass.isAssignableFrom(DashboardViewModel.class)) {
            return (T) new DashboardViewModel(authRepository, familyRepository);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
