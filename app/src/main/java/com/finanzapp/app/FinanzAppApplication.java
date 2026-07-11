package com.finanzapp.app;

import android.app.Application;
import com.finanzapp.app.data.repository.AuthRepository;
import com.finanzapp.app.data.repository.FamilyRepository;
import com.finanzapp.app.data.repository.CategoryRepository;

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

        public AppContainer() {
            authRepository = new AuthRepository();
            familyRepository = new FamilyRepository();
            categoryRepository = new CategoryRepository();
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
    }
}
