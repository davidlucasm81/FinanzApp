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
import com.finanzapp.app.R;
import com.finanzapp.app.data.model.Family;
import com.finanzapp.app.data.model.User;
import com.finanzapp.app.data.model.Account;
import com.finanzapp.app.data.model.Member;
import com.finanzapp.app.databinding.FragmentDashboardBinding;
import com.finanzapp.app.ui.family.FamilySwitcherFragment;
import com.finanzapp.app.util.Result;
import com.finanzapp.app.viewmodel.DashboardViewModel;
import com.finanzapp.app.viewmodel.ViewModelFactory;

import java.text.NumberFormat;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DashboardFragment extends Fragment {
    private FragmentDashboardBinding binding;
    private DashboardViewModel viewModel;

    private String currentCurrencyCode = "EUR";
    private DashboardAccountAdapter accountAdapter;
    private DashboardMemberBalanceAdapter memberBalanceAdapter;
    private String currentFamilyId;
    private boolean isSharedExpenses = false;

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

        memberBalanceAdapter = new DashboardMemberBalanceAdapter();
        binding.rvMemberBalances.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvMemberBalances.setAdapter(memberBalanceAdapter);
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
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_dashboardFragment_to_familySwitcherFragment);
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
                
                String oldCurrency = currentCurrencyCode;
                currentCurrencyCode = family.getCurrencyCode();
                currentFamilyId = family.getId();
                
                isSharedExpenses = "shared_expenses".equals(family.getMode());
                binding.btnTransfer.setVisibility(isSharedExpenses ? View.GONE : View.VISIBLE);
                binding.btnTransactions.setVisibility(View.VISIBLE); // Re-added: always show button
                binding.layoutAccountsSection.setVisibility(isSharedExpenses ? View.GONE : View.VISIBLE);
                binding.layoutMemberBalancesSection.setVisibility(isSharedExpenses ? View.VISIBLE : View.GONE);
                
                binding.tvNetBalanceLabel.setText(isSharedExpenses ? 
                        getString(R.string.label_balances_title) : getString(R.string.net_position));

                // Refresh dependent UI if currency changed
                if (oldCurrency != null && !oldCurrency.equals(currentCurrencyCode)) {
                    Double total = viewModel.getNetBalance().getValue();
                    if (total != null) {
                        Boolean privacyEnabled = viewModel.isPrivacyModeEnabled().getValue();
                        updateNetBalanceText(total, privacyEnabled != null && privacyEnabled);
                    }
                    
                    List<Account> accounts = viewModel.getAccountsList().getValue();
                    if (accounts != null) {
                        accountAdapter.setItems(accounts, currentCurrencyCode);
                    }
                    
                    List<Member> members = viewModel.getMembers().getValue();
                    Map<String, Double> balances = viewModel.getMemberBalances().getValue();
                    if (members != null) {
                        memberBalanceAdapter.setItems(members, balances, currentCurrencyCode, viewModel.getCurrentUserId());
                    }
                }
            } else if (result instanceof Result.Error) {
                binding.tvFamilyName.setText(com.finanzapp.app.R.string.family_label);
            }
        });

        viewModel.isPrivacyModeEnabled().observe(getViewLifecycleOwner(), enabled -> {
            binding.ivPrivacyToggle.setImageResource(enabled ? com.finanzapp.app.R.drawable.ic_visibility_off : com.finanzapp.app.R.drawable.ic_visibility);
            accountAdapter.setPrivacyModeEnabled(enabled);
            memberBalanceAdapter.setPrivacyModeEnabled(enabled);
            Double total = viewModel.getNetBalance().getValue();
            if (total != null) {
                updateNetBalanceText(total, enabled);
            }
        });

        viewModel.getNetBalance().observe(getViewLifecycleOwner(), total -> {
            Boolean privacyEnabled = viewModel.isPrivacyModeEnabled().getValue();
            updateNetBalanceText(total, privacyEnabled != null && privacyEnabled);
        });

        viewModel.getAccountsList().observe(getViewLifecycleOwner(), accounts -> {
            accountAdapter.setItems(accounts, currentCurrencyCode);
        });

        viewModel.getMembers().observe(getViewLifecycleOwner(), members -> {
            if (members != null) {
                // Update member balances if available
                Map<String, Double> balances = viewModel.getMemberBalances().getValue();
                memberBalanceAdapter.setItems(members, balances, currentCurrencyCode, viewModel.getCurrentUserId());
            }
        });

        viewModel.getMemberBalances().observe(getViewLifecycleOwner(), balances -> {
            List<com.finanzapp.app.data.model.Member> members = viewModel.getMembers().getValue();
            if (members != null) {
                memberBalanceAdapter.setItems(members, balances, currentCurrencyCode, viewModel.getCurrentUserId());
            }
        });
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
