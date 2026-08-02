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
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.finanzapp.app.FinanzAppApplication;
import com.finanzapp.app.data.model.Family;
import com.finanzapp.app.data.model.User;
import com.finanzapp.app.databinding.FragmentDashboardBinding;
import com.finanzapp.app.ui.family.FamilySwitcherFragment;
import com.finanzapp.app.data.monetization.BillingRepository;
import com.finanzapp.app.util.Result;
import com.finanzapp.app.viewmodel.DashboardViewModel;
import com.finanzapp.app.viewmodel.ViewModelFactory;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;

import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;

public class DashboardFragment extends Fragment {
    private FragmentDashboardBinding binding;
    private DashboardViewModel viewModel;

    private String currentCurrencyCode = "EUR";
    private DashboardAccountAdapter accountAdapter;
    private String currentFamilyId;
    private AdView adView;
    private BillingRepository billingRepository;

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
        ViewModelFactory factory = new ViewModelFactory(appContainer);
        viewModel = new ViewModelProvider(this, factory).get(DashboardViewModel.class);
        billingRepository = appContainer.getBillingRepository();

        setupRecyclerViews();
        setupClickListeners();
        setupObservers();
        
        viewModel.initPrivacyMode(requireContext());
        viewModel.fetchDashboardData();
    }

    private void setupRecyclerViews() {
        accountAdapter = new DashboardAccountAdapter();
        binding.rvAccounts.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvAccounts.setAdapter(accountAdapter);
    }

    private void setupClickListeners() {
        binding.ivUserPhoto.setOnClickListener(v -> NavHostFragment.findNavController(this).navigate(com.finanzapp.app.R.id.settingsFragment));

        binding.btnTransactions.setOnClickListener(v -> NavHostFragment.findNavController(this).navigate(com.finanzapp.app.R.id.action_dashboardFragment_to_transactionListFragment));

        binding.btnTransfer.setOnClickListener(v -> {
            TransferFundsDialogFragment.newInstance()
                    .show(getChildFragmentManager(), "TransferFunds");
        });

        binding.llFamilySelector.setOnClickListener(v -> {
            if (currentFamilyId != null) {
                FamilySwitcherFragment.newInstance(currentFamilyId)
                        .show(getChildFragmentManager(), "FamilySwitcher");
            }
        });

        binding.ivPrivacyToggle.setOnClickListener(v -> viewModel.togglePrivacyMode(requireContext()));
    }

    private void setupObservers() {
        // Observe accountsLoaded to trigger the reactive chain
        viewModel.getAccountsLoaded().observe(getViewLifecycleOwner(), loaded -> {
            // This observer keeps the accountsSource alive
        });

        viewModel.getDataLoaded().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof Result.Success) {
                binding.progressBar.setVisibility(View.GONE);
                binding.scrollView.setVisibility(View.VISIBLE);
            } else if (result instanceof Result.Loading) {
                binding.progressBar.setVisibility(View.VISIBLE);
                binding.scrollView.setVisibility(View.GONE);
            }
        });

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
                currentFamilyId = family.getId();
            } else if (result instanceof Result.Error) {
                binding.tvFamilyName.setText(com.finanzapp.app.R.string.family_label);
            }
        });

        viewModel.isPrivacyModeEnabled().observe(getViewLifecycleOwner(), enabled -> {
            binding.ivPrivacyToggle.setImageResource(enabled ? com.finanzapp.app.R.drawable.ic_visibility_off : com.finanzapp.app.R.drawable.ic_visibility);
            accountAdapter.setPrivacyModeEnabled(enabled);
            Double total = viewModel.getNetBalance().getValue();
            if (total != null) {
                updateNetBalanceText(total, enabled);
            }
        });

        viewModel.getNetBalance().observe(getViewLifecycleOwner(), total -> {
            Boolean privacyEnabled = viewModel.isPrivacyModeEnabled().getValue();
            updateNetBalanceText(total, privacyEnabled != null && privacyEnabled);
        });

        viewModel.getAccountsList().observe(getViewLifecycleOwner(), accounts -> accountAdapter.setItems(accounts, currentCurrencyCode));

        billingRepository.getIsPremium().observe(getViewLifecycleOwner(), isPremium -> {
            if (isPremium == null) return;
            if (isPremium) {
                removeAds();
            } else {
                loadAds();
            }
        });
    }

    private void loadAds() {
        if (adView != null) return;

        adView = new AdView(requireContext());
        adView.setAdUnitId(com.finanzapp.app.BuildConfig.ADMOB_BANNER_FIXED_ID);
        adView.setAdSize(AdSize.BANNER);

        binding.adViewContainer.removeAllViews();
        binding.adViewContainer.addView(adView);
        binding.adViewContainer.setVisibility(View.VISIBLE);

        AdRequest adRequest = new AdRequest.Builder().build();
        adView.loadAd(adRequest);
    }

    private void removeAds() {
        if (adView != null) {
            adView.destroy();
            adView = null;
        }
        binding.adViewContainer.setVisibility(View.GONE);
    }

    private void updateNetBalanceText(double total, boolean isPrivacyEnabled) {
        if (isPrivacyEnabled) {
            binding.tvNetBalanceValue.setText(getString(com.finanzapp.app.R.string.privacy_mode_masked_value));
        } else {
            binding.tvNetBalanceValue.setText(formatCurrency(total, currentCurrencyCode));
        }
    }

    @Override
    public void onDestroyView() {
        if (adView != null) {
            adView.destroy();
        }
        super.onDestroyView();
        binding = null;
    }

    private String formatCurrency(double amount, String currencyCode) {
        Locale locale;
        switch (currencyCode) {
            case "USD": locale = Locale.US; break;
            case "GBP": locale = Locale.UK; break;
            default: locale = new Locale("es", "ES"); break;
        }
        NumberFormat format = NumberFormat.getCurrencyInstance(locale);
        try {
            format.setCurrency(Currency.getInstance(currencyCode));
        } catch (Exception ignored) {}
        return format.format(amount);
    }
}
