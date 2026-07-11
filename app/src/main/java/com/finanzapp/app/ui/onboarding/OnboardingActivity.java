package com.finanzapp.app.ui.onboarding;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.finanzapp.app.databinding.ActivityOnboardingBinding;

public class OnboardingActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityOnboardingBinding binding = ActivityOnboardingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
    }
}
