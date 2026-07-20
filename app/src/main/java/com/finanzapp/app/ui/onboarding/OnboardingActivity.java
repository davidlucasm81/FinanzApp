package com.finanzapp.app.ui.onboarding;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import androidx.navigation.NavController;
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
        if (mode != null) {
            NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_onboarding);
            if (navHostFragment != null) {
                NavController navController = navHostFragment.getNavController();
                if ("create".equals(mode)) {
                    navController.navigate(R.id.createFamilyFragment);
                } else if ("join".equals(mode)) {
                    navController.navigate(R.id.joinByCodeFragment);
                }
            }
        }
    }
}
