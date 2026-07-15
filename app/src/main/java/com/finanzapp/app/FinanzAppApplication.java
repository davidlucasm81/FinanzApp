package com.finanzapp.app;

import android.app.Application;
import com.finanzapp.app.data.repository.AuthRepository;
import com.finanzapp.app.data.repository.FamilyRepository;
import com.finanzapp.app.data.repository.CategoryRepository;
import com.finanzapp.app.data.repository.AccountRepository;
import com.finanzapp.app.data.repository.TransactionRepository;

public class FinanzAppApplication extends Application {
    private AppContainer appContainer;

    @Override
    public void onCreate() {
        super.onCreate();
        appContainer = new AppContainer();
    }

    public AppContainer getAppContainer() {
        return appContainer;
    }

    public static class AppContainer {
        private final AuthRepository authRepository;
        private final FamilyRepository familyRepository;
        private final CategoryRepository categoryRepository;
        private final AccountRepository accountRepository;
        private final TransactionRepository transactionRepository;

        public AppContainer() {
            authRepository = new AuthRepository();
            familyRepository = new FamilyRepository();
            categoryRepository = new CategoryRepository();
            accountRepository = new AccountRepository();
            transactionRepository = new TransactionRepository();
        }

        public AuthRepository getAuthRepository() {
            return authRepository;
        }

        public FamilyRepository getFamilyRepository() {
            return familyRepository;
        }

        public CategoryRepository getCategoryRepository() {
            return categoryRepository;
        }

        public AccountRepository getAccountRepository() { return accountRepository; }

        public TransactionRepository getTransactionRepository() { return transactionRepository; }
    }
}
