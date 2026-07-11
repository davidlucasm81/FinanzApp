package com.finanzapp.app.ui.onboarding;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.finanzapp.app.FinanzAppApplication;
import com.finanzapp.app.MainActivity;
import com.finanzapp.app.databinding.FragmentCreateFamilyBinding;
import com.finanzapp.app.util.Result;
import com.finanzapp.app.viewmodel.OnboardingViewModel;
import com.finanzapp.app.viewmodel.ViewModelFactory;

public class CreateFamilyFragment extends Fragment {
    private FragmentCreateFamilyBinding binding;
    private OnboardingViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentCreateFamilyBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FinanzAppApplication.AppContainer appContainer = ((FinanzAppApplication) requireActivity().getApplication()).getAppContainer();
        ViewModelFactory factory = new ViewModelFactory(appContainer.getAuthRepository(), appContainer.getFamilyRepository());
        viewModel = new ViewModelProvider(this, factory).get(OnboardingViewModel.class);

        setupCurrencyDropdown();
        setupObservers();
        
        binding.btnConfirm.setOnClickListener(v -> {
            if (binding.tilFamilyName.getEditText() == null) return;
            String name = binding.tilFamilyName.getEditText().getText().toString().trim();
            String currency = binding.actCurrency.getText().toString();
            
            if (name.isEmpty()) {
                binding.tilFamilyName.setError("El nombre es obligatorio");
                return;
            }
            
            viewModel.createFamily(name, currency);
        });
    }

    private void setupCurrencyDropdown() {
        String[] currencies = {"EUR", "USD", "GBP", "MXN", "ARS", "CLP", "COP"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, currencies);
        binding.actCurrency.setAdapter(adapter);
        binding.actCurrency.setText(currencies[0], false);
    }

    private void setupObservers() {
        viewModel.getCreateFamilyResult().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof Result.Loading) {
                binding.btnConfirm.setEnabled(false);
            } else if (result instanceof Result.Success) {
                Toast.makeText(requireContext(), "Familia creada con éxito", Toast.LENGTH_SHORT).show();
                navigateToMain();
            } else if (result instanceof Result.Error) {
                binding.btnConfirm.setEnabled(true);
                Exception e = ((Result.Error<?>) result).getException();
                Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void navigateToMain() {
        Intent intent = new Intent(requireContext(), MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
