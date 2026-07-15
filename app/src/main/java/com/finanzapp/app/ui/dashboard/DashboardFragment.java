package com.finanzapp.app.ui.dashboard;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import androidx.navigation.fragment.NavHostFragment;

import com.bumptech.glide.Glide;
import com.finanzapp.app.FinanzAppApplication;
import com.finanzapp.app.data.model.Family;
import com.finanzapp.app.data.model.User;
import com.finanzapp.app.databinding.FragmentDashboardBinding;
import com.finanzapp.app.util.Result;
import com.finanzapp.app.viewmodel.DashboardViewModel;
import com.finanzapp.app.viewmodel.ViewModelFactory;

import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;

public class DashboardFragment extends Fragment {
    private FragmentDashboardBinding binding;
    private DashboardViewModel viewModel;

    private String currentCurrencyCode = "EUR";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FinanzAppApplication.AppContainer appContainer = ((FinanzAppApplication) requireActivity().getApplication()).getAppContainer();
        ViewModelFactory factory = new ViewModelFactory(appContainer.getAuthRepository(), appContainer.getFamilyRepository());
        viewModel = new ViewModelProvider(this, factory).get(DashboardViewModel.class);

        binding.ivUserPhoto.setOnClickListener(v -> {
            NavHostFragment.findNavController(this).navigate(com.finanzapp.app.R.id.settingsFragment);
        });

        binding.btnTransactions.setOnClickListener(v -> {
            NavHostFragment.findNavController(this).navigate(com.finanzapp.app.R.id.action_dashboardFragment_to_transactionListFragment);
        });

        setupObservers();
        viewModel.fetchDashboardData();
    }

    private void setupObservers() {
        viewModel.getUserData().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof Result.Success) {
                User user = ((Result.Success<User>) result).getData();
                if (user.getPhotoUrl() != null) {
                    Glide.with(this)
                            .load(user.getPhotoUrl())
                            .placeholder(com.finanzapp.app.R.drawable.ic_user_placeholder)
                            .circleCrop()
                            .into(binding.ivUserPhoto);
                }
            }
        });

        viewModel.getFamilyData().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof Result.Success) {
                Family family = ((Result.Success<Family>) result).getData();
                binding.tvFamilyName.setText(family.getName());

                currentCurrencyCode = family.getCurrencyCode();
            } else if (result instanceof Result.Error) {
                binding.tvFamilyName.setText(com.finanzapp.app.R.string.family_label);
            }
        });

        viewModel.getNetBalance().observe(getViewLifecycleOwner(), total -> {
            binding.tvNetBalanceValue.setText(
                    formatCurrency(total, currentCurrencyCode)
            );
        });

    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private String formatCurrency(double amount, String currencyCode) {

        Locale locale;

        switch (currencyCode) {
            case "USD":
                locale = Locale.US;
                break;

            case "GBP":
                locale = Locale.UK;
                break;

            case "EUR":
            default:
                locale = new Locale("es", "ES");
                break;
        }

        NumberFormat format = NumberFormat.getCurrencyInstance(locale);
        return format.format(amount);
    }
}
