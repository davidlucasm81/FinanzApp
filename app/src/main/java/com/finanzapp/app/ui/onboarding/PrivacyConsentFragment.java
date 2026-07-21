package com.finanzapp.app.ui.onboarding;

import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.finanzapp.app.FinanzAppApplication;
import com.finanzapp.app.R;
import com.finanzapp.app.databinding.FragmentPrivacyConsentBinding;
import com.finanzapp.app.util.Result;
import com.finanzapp.app.viewmodel.OnboardingViewModel;
import com.finanzapp.app.viewmodel.ViewModelFactory;

public class PrivacyConsentFragment extends Fragment {
    private FragmentPrivacyConsentBinding binding;
    private OnboardingViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentPrivacyConsentBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FinanzAppApplication.AppContainer appContainer = ((FinanzAppApplication) requireActivity().getApplication()).getAppContainer();
        ViewModelFactory factory = new ViewModelFactory(appContainer.getAuthRepository(), appContainer.getFamilyRepository());
        viewModel = new ViewModelProvider(this, factory).get(OnboardingViewModel.class);

        binding.tvPrivacyPolicy.setText(Html.fromHtml(getString(R.string.privacy_policy_text), Html.FROM_HTML_MODE_LEGACY));

        binding.cbAcceptPrivacy.setOnCheckedChangeListener((buttonView, isChecked) -> {
            binding.btnContinue.setEnabled(isChecked);
        });

        binding.btnContinue.setOnClickListener(v -> {
            viewModel.acceptPrivacyPolicy();
        });

        binding.btnSignOut.setOnClickListener(v -> {
            viewModel.signOut();
            android.content.Intent intent = new android.content.Intent(requireContext(), com.finanzapp.app.ui.SplashActivity.class);
            intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            requireActivity().finish();
        });

        viewModel.getPrivacyResult().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof Result.Success) {
                // Navegar de vuelta a SplashActivity limpiando el backstack
                // Esto forzará una nueva comprobación del estado del usuario
                android.content.Intent intent = new android.content.Intent(requireContext(), com.finanzapp.app.ui.SplashActivity.class);
                intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                requireActivity().finish();
            } else if (result instanceof Result.Error) {
                Toast.makeText(requireContext(), "Error al guardar consentimiento", Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
