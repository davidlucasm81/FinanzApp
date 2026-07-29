package com.finanzapp.app.viewmodel;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.finanzapp.app.data.repository.AuthRepository;
import com.finanzapp.app.data.repository.FamilyRepository;
import com.finanzapp.app.data.repository.AccountRepository;
import com.finanzapp.app.data.repository.CategoryRepository;
import com.finanzapp.app.FinanzAppApplication;
import com.finanzapp.app.data.repository.NotificationRepository;
import com.finanzapp.app.data.repository.TransactionRepository;

public class ViewModelFactory implements ViewModelProvider.Factory {
    private final AuthRepository authRepository;
    private final FamilyRepository familyRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final NotificationRepository notificationRepository;

    public ViewModelFactory(FinanzAppApplication.AppContainer container) {
        this.authRepository = container.getAuthRepository();
        this.familyRepository = container.getFamilyRepository();
        this.accountRepository = container.getAccountRepository();
        this.categoryRepository = container.getCategoryRepository();
        this.transactionRepository = container.getTransactionRepository();
        this.notificationRepository = container.getNotificationRepository();
    }

    public ViewModelFactory(AuthRepository authRepository, FamilyRepository familyRepository) {
        this(authRepository, familyRepository, null, null, null, null);
    }

    public ViewModelFactory(AuthRepository authRepository, FamilyRepository familyRepository, AccountRepository accountRepository) {
        this(authRepository, familyRepository, accountRepository, null, null, null);
    }

    public ViewModelFactory(AuthRepository authRepository, FamilyRepository familyRepository,
                            AccountRepository accountRepository, CategoryRepository categoryRepository) {
        this(authRepository, familyRepository, accountRepository, categoryRepository, null, null);
    }

    public ViewModelFactory(AuthRepository authRepository, FamilyRepository familyRepository,
                            AccountRepository accountRepository, CategoryRepository categoryRepository,
                            TransactionRepository transactionRepository) {
        this(authRepository, familyRepository, accountRepository, categoryRepository, transactionRepository, null);
    }

    public ViewModelFactory(AuthRepository authRepository, FamilyRepository familyRepository,
                            AccountRepository accountRepository, CategoryRepository categoryRepository,
                            TransactionRepository transactionRepository, NotificationRepository notificationRepository) {
        this.authRepository = authRepository;
        this.familyRepository = familyRepository;
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
        this.notificationRepository = notificationRepository;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(AuthViewModel.class)) {
            return (T) new AuthViewModel(authRepository);
        } else if (modelClass.isAssignableFrom(OnboardingViewModel.class)) {
            return (T) new OnboardingViewModel(familyRepository, authRepository);
        } else if (modelClass.isAssignableFrom(AccountViewModel.class)) {
            if (accountRepository == null) throw new IllegalArgumentException("AccountRepository not provided to ViewModelFactory");
            return (T) new AccountViewModel(authRepository, accountRepository);
        } else if (modelClass.isAssignableFrom(FamilyViewModel.class)) {
            return (T) new FamilyViewModel(authRepository, familyRepository);
        } else if (modelClass.isAssignableFrom(SettingsViewModel.class)) {
            return (T) new SettingsViewModel(authRepository, familyRepository);
        } else if (modelClass.isAssignableFrom(DashboardViewModel.class)) {
            return (T) new DashboardViewModel(authRepository, familyRepository, accountRepository);
        } else if (modelClass.isAssignableFrom(CategoryViewModel.class)) {
            if (categoryRepository == null) throw new IllegalArgumentException("CategoryRepository not provided to ViewModelFactory");
            return (T) new CategoryViewModel(authRepository, categoryRepository);
        } else if (modelClass.isAssignableFrom(TransactionViewModel.class)) {
            if (transactionRepository == null) throw new IllegalArgumentException("TransactionRepository not provided to ViewModelFactory");
            return (T) new TransactionViewModel(authRepository, transactionRepository, accountRepository, categoryRepository, familyRepository);
        } else if (modelClass.isAssignableFrom(StatisticsViewModel.class)) {
            return (T) new StatisticsViewModel(authRepository, familyRepository, accountRepository, categoryRepository, transactionRepository);
        } else if (modelClass.isAssignableFrom(NotificationViewModel.class)) {
            return (T) new NotificationViewModel(authRepository, notificationRepository);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}