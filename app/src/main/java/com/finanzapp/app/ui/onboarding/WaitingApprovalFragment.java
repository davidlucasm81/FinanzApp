package com.finanzapp.app.ui.onboarding;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.finanzapp.app.R;
import com.finanzapp.app.databinding.FragmentWaitingApprovalBinding;
import com.finanzapp.app.ui.auth.LoginActivity;
import com.google.firebase.auth.FirebaseAuth;

public class WaitingApprovalFragment extends Fragment {
    private FragmentWaitingApprovalBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentWaitingApprovalBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.btnBackToOnboarding.setOnClickListener(v -> {
            Navigation.findNavController(v).popBackStack(R.id.welcomeFragment, false);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
