package com.finanzapp.app;

import android.app.Application;
import com.finanzapp.app.data.repository.AuthRepository;
import com.finanzapp.app.data.repository.FamilyRepository;
import com.finanzapp.app.data.repository.CategoryRepository;
import com.finanzapp.app.data.repository.AccountRepository;
import com.finanzapp.app.data.repository.NotificationRepository;
import com.finanzapp.app.data.repository.TransactionRepository;
import com.finanzapp.app.data.monetization.AdsRepository;
import com.finanzapp.app.data.monetization.BillingRepository;

public class FinanzAppApplication extends Application {
    private AppContainer appContainer;

    @Override
    public void onCreate() {
        super.onCreate();
        appContainer = new AppContainer(this);

        // Initialize Security Provider to avoid DynamiteModule/ProviderInstaller warnings
        // and ensure latest security patches are applied for network operations.
        com.google.android.gms.security.ProviderInstaller.installIfNeededAsync(this, new com.google.android.gms.security.ProviderInstaller.ProviderInstallListener() {
            @Override
            public void onProviderInstalled() {
                android.util.Log.d("FinanzApp", "Security provider installed successfully.");
            }

            @Override
            public void onProviderInstallFailed(int errorCode, android.content.Intent recoveryIntent) {
                android.util.Log.w("FinanzApp", "Security provider installation failed: " + errorCode);
            }
        });
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
        private final NotificationRepository notificationRepository;
        private final AdsRepository adsRepository;
        private final BillingRepository billingRepository;

        public AppContainer(android.content.Context context) {
            authRepository = new AuthRepository();
            familyRepository = new FamilyRepository(authRepository);
            categoryRepository = new CategoryRepository(authRepository);
            accountRepository = new AccountRepository(authRepository);
            notificationRepository = new NotificationRepository();
            transactionRepository = new TransactionRepository(context, authRepository);
            adsRepository = new AdsRepository();
            billingRepository = new BillingRepository(context, authRepository);
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

        public NotificationRepository getNotificationRepository() { return notificationRepository; }

        public AdsRepository getAdsRepository() { return adsRepository; }

        public BillingRepository getBillingRepository() { return billingRepository; }
    }
}
