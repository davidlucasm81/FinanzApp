package com.finanzapp.app.ui.onboarding;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.finanzapp.app.R;
import com.finanzapp.app.databinding.ActivityOnboardingBinding;

public class OnboardingActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityOnboardingBinding binding = ActivityOnboardingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String mode = getIntent().getStringExtra("mode");
        boolean showPrivacyConsent = getIntent().getBooleanExtra("show_privacy_consent", false);

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_onboarding);
        if (navHostFragment != null) {
            NavController navController = navHostFragment.getNavController();

            // Si el usuario ya pertenece a una familia y entra aquí solo para
            // crear/unirse a otra, no debe quedar "welcome" debajo en el back stack:
            // así el back cierra la Activity en vez de mostrar el welcome.
            boolean skipStartDestination = "create".equals(mode) || "join".equals(mode);

            NavOptions navOptions = skipStartDestination
                    ? new NavOptions.Builder()
                    .setPopUpTo(navController.getGraph().getStartDestinationId(), true)
                    .build()
                    : null;

            if (showPrivacyConsent) {
                navController.navigate(R.id.privacyConsentFragment);
            } else if ("create".equals(mode)) {
                navController.navigate(R.id.createFamilyFragment, null, navOptions);
            } else if ("join".equals(mode)) {
                navController.navigate(R.id.joinByCodeFragment, null, navOptions);
            }
        }
    }
}