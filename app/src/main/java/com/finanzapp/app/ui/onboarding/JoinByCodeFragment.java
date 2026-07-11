package com.finanzapp.app.ui.onboarding;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import androidx.navigation.Navigation;

import com.finanzapp.app.FinanzAppApplication;
import com.finanzapp.app.MainActivity;
import com.finanzapp.app.R;
import com.finanzapp.app.databinding.FragmentJoinByCodeBinding;
import com.finanzapp.app.util.Result;
import com.finanzapp.app.viewmodel.OnboardingViewModel;
import com.finanzapp.app.viewmodel.ViewModelFactory;

public class JoinByCodeFragment extends Fragment {
    private FragmentJoinByCodeBinding binding;
    private OnboardingViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentJoinByCodeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FinanzAppApplication.AppContainer appContainer = ((FinanzAppApplication) requireActivity().getApplication()).getAppContainer();
        ViewModelFactory factory = new ViewModelFactory(appContainer.getAuthRepository(), appContainer.getFamilyRepository());
        viewModel = new ViewModelProvider(this, factory).get(OnboardingViewModel.class);

        setupObservers();

        binding.btnJoin.setOnClickListener(v -> {
            if (binding.tilCode.getEditText() == null) return;
            String code = binding.tilCode.getEditText().getText().toString().trim().toUpperCase();
            
            if (code.length() != 6) {
                binding.tilCode.setError("El código debe tener 6 caracteres");
                return;
            }
            
            viewModel.joinByCode(code);
        });
    }

    private void setupObservers() {
        viewModel.getJoinByCodeResult().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof Result.Loading) {
                binding.btnJoin.setEnabled(false);
            } else if (result instanceof Result.Success) {
                Toast.makeText(requireContext(), "Solicitud enviada. Espera aprobación.", Toast.LENGTH_LONG).show();
                Navigation.findNavController(requireView()).navigate(R.id.action_joinByCodeFragment_to_waitingApprovalFragment);
            } else if (result instanceof Result.Error) {
                binding.btnJoin.setEnabled(true);
                Exception e = ((Result.Error<?>) result).getException();
                Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
