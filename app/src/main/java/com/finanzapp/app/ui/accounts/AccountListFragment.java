package com.finanzapp.app.ui.accounts;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.finanzapp.app.R;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.finanzapp.app.FinanzAppApplication;
import com.finanzapp.app.data.model.Account;
import com.finanzapp.app.databinding.FragmentAccountListBinding;
import com.finanzapp.app.databinding.ItemAccountBinding;
import com.finanzapp.app.util.Result;
import com.finanzapp.app.viewmodel.AccountViewModel;
import com.finanzapp.app.viewmodel.FamilyViewModel;
import com.finanzapp.app.viewmodel.ViewModelFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AccountListFragment extends Fragment {
    private FragmentAccountListBinding binding;
    private AccountViewModel viewModel;
    private FamilyViewModel familyViewModel;
    private AccountsAdapter adapter;
    private String familyId;
    private String currencyCode = "€";
    // Solo admin/owner pueden archivar o eliminar cuentas (ver AGENTS.md sección 5).
    // Por defecto false (más restrictivo) hasta confirmar el rol real desde Firestore.
    private boolean isAdmin = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentAccountListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FinanzAppApplication.AppContainer appContainer = ((FinanzAppApplication) requireActivity().getApplication()).getAppContainer();
        ViewModelFactory factory = new ViewModelFactory(appContainer);
        viewModel = new ViewModelProvider(this, factory).get(AccountViewModel.class);
        familyViewModel = new ViewModelProvider(requireActivity(), factory).get(FamilyViewModel.class);

        setupRecycler();
        setupObservers();

        binding.fabAdd.setOnClickListener(v -> showAddEditDialog(null));

        resolveFamilyId();
        viewModel.initPrivacyMode(requireContext());
        binding.pbLoading.setVisibility(View.VISIBLE);
    }

    private void resolveFamilyId() {
        if (familyId != null) {
            familyViewModel.fetchFamily(familyId);
            return;
        }

        String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection(com.finanzapp.app.data.firebase.FirestorePaths.USERS)
                    .document(uid).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        com.finanzapp.app.data.model.User user = documentSnapshot.toObject(com.finanzapp.app.data.model.User.class);
                        if (user != null && user.getFamilyId() != null) {
                            familyId = user.getFamilyId();
                            familyViewModel.fetchFamily(familyId);
                        }
                    });
        }
    }

    private void setupRecycler() {
        adapter = new AccountsAdapter(new ArrayList<>(), this::onEdit, this::onArchive, this::onDelete);
        binding.rvAccounts.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvAccounts.setAdapter(adapter);
    }

    private void setupObservers() {
        // Using getFamilyData from FamilyViewModel (which we saw in Dashboard)
        familyViewModel.getFamilyData().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof Result.Loading) {
                binding.pbLoading.setVisibility(View.VISIBLE);
            } else if (result instanceof Result.Success) {
                com.finanzapp.app.data.model.Family family = ((Result.Success<com.finanzapp.app.data.model.Family>) result).getData();
                familyId = family.getId();
                currencyCode = family.getCurrencyCode();
                viewModel.checkAdminRole(familyId);

                // Phase 18: Hide FAB for shared expenses mode (only one joint account allowed)
                boolean isSharedExpenses = "shared_expenses".equals(family.getMode());
                binding.fabAdd.setVisibility(isSharedExpenses ? View.GONE : View.VISIBLE);

                viewModel.getAccounts(familyId).observe(getViewLifecycleOwner(), accounts -> {
                    binding.pbLoading.setVisibility(View.GONE);
                    if (accounts != null) {
                        adapter.setItems(accounts, currencyCode);
                        adapter.setIsSharedExpenses(isSharedExpenses);
                        binding.tvEmpty.setVisibility(accounts.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                });
            } else if (result instanceof Result.Error) {
                binding.pbLoading.setVisibility(View.GONE);
            }
        });

        viewModel.getIsAdmin().observe(getViewLifecycleOwner(), admin -> {
            isAdmin = admin;
            if (adapter != null) adapter.setIsAdmin(isAdmin);
        });

        viewModel.isPrivacyModeEnabled().observe(getViewLifecycleOwner(), enabled -> {
            if (adapter != null) adapter.setPrivacyModeEnabled(enabled);
        });

        viewModel.getCreateResult().observe(getViewLifecycleOwner(), result -> handleResult(result, "Cuenta creada"));
        viewModel.getUpdateResult().observe(getViewLifecycleOwner(), result -> handleResult(result, "Cuenta actualizada"));
        viewModel.getArchiveResult().observe(getViewLifecycleOwner(), result -> handleResult(result, "Estado de cuenta actualizado"));
        viewModel.getDeleteResult().observe(getViewLifecycleOwner(), result -> handleResult(result, "Cuenta eliminada"));
    }

    private void handleResult(Result<?> result, String successMsg) {
        if (result instanceof Result.Success) {
            Toast.makeText(requireContext(), successMsg, Toast.LENGTH_SHORT).show();
        } else if (result instanceof Result.Error) {
            Exception e = ((Result.Error<?>) result).getException();
            String errorMsg = e != null ? e.getMessage() : getString(R.string.error_unknown);
            Toast.makeText(requireContext(), getString(R.string.error_with_message, errorMsg), Toast.LENGTH_LONG).show();
        }
    }

    private void showAddEditDialog(@Nullable Account account) {
        if (familyId == null) {
            Toast.makeText(requireContext(), R.string.error_identify_family, Toast.LENGTH_LONG).show();
            return;
        }
        AddEditAccountFragment.newInstance(familyId, account)
                .show(getChildFragmentManager(), "add_edit_account");
    }

    private void onEdit(Account account) {
        showAddEditDialog(account);
    }

    private void onArchive(Account account) {
        if (familyId == null) return;
        viewModel.archiveAccount(familyId, account.getId(), !account.isActive());
    }

    private void onDelete(Account account) {
        if (familyId == null) return;
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.dialog_delete_account))
                .setMessage(getString(R.string.msg_confirm_delete_account, account.getName()))
                .setPositiveButton(R.string.label_borrar, (dialog, which) -> viewModel.deleteAccount(familyId, account.getId()))
                .setNegativeButton(R.string.cancel_button, null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private interface OnAccountActionListener {
        void onAction(Account account);
    }

    private static class AccountsAdapter extends RecyclerView.Adapter<AccountsViewHolder> {
        private final List<Account> items = new ArrayList<>();
        private final OnAccountActionListener onEdit;
        private final OnAccountActionListener onArchive;
        private final OnAccountActionListener onDelete;
        private String currency = "€";
        private boolean isAdmin = false;
        private boolean isPrivacyModeEnabled = false;
        private boolean isSharedExpenses = false;

        AccountsAdapter(List<Account> items, OnAccountActionListener onEdit, OnAccountActionListener onArchive, OnAccountActionListener onDelete) {
            this.items.addAll(items);
            this.onEdit = onEdit;
            this.onArchive = onArchive;
            this.onDelete = onDelete;
        }

        void setItems(List<Account> newItems, String currency) {
            this.currency = currency;
            items.clear();
            items.addAll(newItems);
            notifyDataSetChanged();
        }

        void setIsAdmin(boolean isAdmin) {
            this.isAdmin = isAdmin;
            notifyDataSetChanged();
        }

        void setPrivacyModeEnabled(boolean enabled) {
            if (this.isPrivacyModeEnabled != enabled) {
                this.isPrivacyModeEnabled = enabled;
                notifyDataSetChanged();
            }
        }

        void setIsSharedExpenses(boolean sharedExpenses) {
            if (this.isSharedExpenses != sharedExpenses) {
                this.isSharedExpenses = sharedExpenses;
                notifyDataSetChanged();
            }
        }

        @NonNull
        @Override
        public AccountsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemAccountBinding b = ItemAccountBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new AccountsViewHolder(b);
        }

        @Override
        public void onBindViewHolder(@NonNull AccountsViewHolder holder, int position) {
            holder.bind(items.get(position), currency, isAdmin, isPrivacyModeEnabled, isSharedExpenses, onEdit, onArchive, onDelete);
        }

        @Override
        public int getItemCount() { return items.size(); }
    }

    private static class AccountsViewHolder extends RecyclerView.ViewHolder {
        private final ItemAccountBinding binding;

        AccountsViewHolder(ItemAccountBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Account a, String currency, boolean isAdmin, boolean isPrivacyModeEnabled, boolean isSharedExpenses, OnAccountActionListener onEdit, OnAccountActionListener onArchive, OnAccountActionListener onDelete) {
            binding.tvItemName.setText(a.getName());
            if (isPrivacyModeEnabled) {
                binding.tvItemBalance.setText("****");
            } else {
                binding.tvItemBalance.setText(String.format(Locale.getDefault(), "%,.2f %s", a.getCurrentBalance(), currency));
            }
            binding.tvItemStatus.setVisibility(a.isActive() ? View.GONE : View.VISIBLE);

            // Icon for archive/unarchive (using standard android icons)
            binding.btnItemArchive.setImageResource(a.isActive() ? android.R.drawable.ic_menu_save : android.R.drawable.ic_menu_view);

            // Phase 18: In shared expenses mode, hide all management buttons (only one immutable joint account)
            if (isSharedExpenses) {
                binding.btnItemEdit.setVisibility(View.GONE);
                binding.btnItemArchive.setVisibility(View.GONE);
                binding.btnItemDelete.setVisibility(View.GONE);
            } else {
                // Normal mode rules
                boolean hasMovements = a.isHasTransactions();
                binding.btnItemEdit.setVisibility(isAdmin ? View.VISIBLE : View.GONE);
                binding.btnItemArchive.setVisibility(isAdmin && hasMovements ? View.VISIBLE : View.GONE);
                binding.btnItemDelete.setVisibility(isAdmin && !hasMovements ? View.VISIBLE : View.GONE);
            }

            binding.btnItemEdit.setOnClickListener(v -> onEdit.onAction(a));
            binding.btnItemArchive.setOnClickListener(v -> onArchive.onAction(a));
            binding.btnItemDelete.setOnClickListener(v -> onDelete.onAction(a));

            // Visual feedback for archived accounts
            binding.getRoot().setAlpha(a.isActive() ? 1.0f : 0.6f);
        }
    }
}