package com.finanzapp.app.ui.onboarding;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.finanzapp.app.FinanzAppApplication;
import com.finanzapp.app.MainActivity;
import com.finanzapp.app.data.model.Account;
import com.finanzapp.app.databinding.FragmentAddInitialAccountsBinding;
import com.finanzapp.app.databinding.ItemInitialAccountBinding;
import com.finanzapp.app.viewmodel.AccountViewModel;
import com.finanzapp.app.viewmodel.ViewModelFactory;
import com.finanzapp.app.util.Result;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AddInitialAccountsFragment extends Fragment {
    private FragmentAddInitialAccountsBinding binding;
    private AccountViewModel viewModel;
    private AccountsAdapter adapter;
    private String familyId;
    private String currencyCode = "€";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentAddInitialAccountsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() != null) {
            familyId = getArguments().getString("familyId");
            String argCurrency = getArguments().getString("currencyCode");
            if (argCurrency != null && !argCurrency.isEmpty()) {
                currencyCode = argCurrency;
            }
        }

        FinanzAppApplication.AppContainer appContainer = ((FinanzAppApplication) requireActivity().getApplication()).getAppContainer();
        ViewModelFactory factory = new ViewModelFactory(appContainer.getAuthRepository(), appContainer.getFamilyRepository(), appContainer.getAccountRepository());
        viewModel = new ViewModelProvider(this, factory).get(AccountViewModel.class);

        setupRecycler();
        setupObservers();

        binding.btnAddAccount.setOnClickListener(v -> onAddAccount());
        binding.btnContinue.setOnClickListener(v -> navigateToMain());
    }

    private void setupRecycler() {
        adapter = new AccountsAdapter(new ArrayList<>(), this::onEditAccount, this::onDeleteAccount);
        binding.rvAccounts.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvAccounts.setAdapter(adapter);
    }

    private void setupObservers() {
        // Creación de cuenta: solo gestiona el formulario y el feedback; la lista se
        // actualiza siempre a través del listener en tiempo real de getAccounts().
        viewModel.getCreateResult().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof Result.Loading) {
                binding.btnAddAccount.setEnabled(false);
            } else if (result instanceof Result.Success) {
                binding.btnAddAccount.setEnabled(true);
                binding.etAccountName.setText("");
                binding.etInitialBalance.setText("");
                Toast.makeText(requireContext(), "Cuenta añadida", Toast.LENGTH_SHORT).show();
            } else if (result instanceof Result.Error) {
                binding.btnAddAccount.setEnabled(true);
                Exception e = ((Result.Error<?>) result).getException();
                Toast.makeText(requireContext(), "Error: " + (e != null ? e.getMessage() : ""), Toast.LENGTH_LONG).show();
            }
        });

        viewModel.getUpdateResult().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof Result.Success) {
                Toast.makeText(requireContext(), "Cuenta actualizada", Toast.LENGTH_SHORT).show();
            } else if (result instanceof Result.Error) {
                Exception e = ((Result.Error<?>) result).getException();
                Toast.makeText(requireContext(), "Error al actualizar: " + (e != null ? e.getMessage() : ""), Toast.LENGTH_LONG).show();
            }
        });

        viewModel.getDeleteResult().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof Result.Success) {
                Toast.makeText(requireContext(), "Cuenta eliminada", Toast.LENGTH_SHORT).show();
            } else if (result instanceof Result.Error) {
                Exception e = ((Result.Error<?>) result).getException();
                Toast.makeText(requireContext(), "Error al eliminar: " + (e != null ? e.getMessage() : ""), Toast.LENGTH_LONG).show();
            }
        });

        if (familyId != null) {
            viewModel.getAccounts(familyId).observe(getViewLifecycleOwner(), accounts -> {
                List<Account> list = accounts != null ? accounts : new ArrayList<>();
                adapter.setItems(list);
                updateSummary(list);
                updateEmptyState(list.isEmpty());
            });
        }
    }

    private void updateSummary(List<Account> accounts) {
        double total = 0.0;
        for (Account a : accounts) {
            total += a.getInitialBalance();
        }
        binding.tvInitialNetPositionValue.setText(
                String.format(Locale.getDefault(), "%,.2f %s", total, currencyCode));

        int count = accounts.size();
        if (count == 0) {
            binding.tvAccountsCount.setText("Aún no has añadido ninguna cuenta");
        } else if (count == 1) {
            binding.tvAccountsCount.setText("1 cuenta añadida");
        } else {
            binding.tvAccountsCount.setText(count + " cuentas añadidas");
        }
    }

    private void updateEmptyState(boolean isEmpty) {
        binding.rvAccounts.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        binding.tvEmptyAccounts.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
    }

    private void onAddAccount() {
        String name = binding.etAccountName.getText() != null ? binding.etAccountName.getText().toString().trim() : "";
        String initialStr = binding.etInitialBalance.getText() != null ? binding.etInitialBalance.getText().toString().trim() : "";

        if (name.isEmpty()) {
            binding.tilAccountName.setError("Nombre obligatorio");
            return;
        } else {
            binding.tilAccountName.setError(null);
        }

        double initial = 0.0;
        try {
            if (!initialStr.isEmpty()) initial = Double.parseDouble(initialStr);
        } catch (NumberFormatException e) {
            binding.tilInitialBalance.setError("Formato inválido");
            return;
        }
        binding.tilInitialBalance.setError(null);

        if (familyId == null) {
            Toast.makeText(requireContext(), "Familia no encontrada", Toast.LENGTH_LONG).show();
            return;
        }

        Account account = new Account();
        account.setName(name);
        account.setInitialBalance(initial);
        account.setCurrentBalance(initial);

        viewModel.createAccount(familyId, account);
    }

    private void onEditAccount(Account account) {
        if (familyId == null) return;

        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding, padding, padding);

        EditText etName = new EditText(requireContext());
        etName.setHint("Nombre de la cuenta");
        etName.setText(account.getName());
        container.addView(etName);

        EditText etBalance = new EditText(requireContext());
        etBalance.setHint("Saldo inicial");
        etBalance.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        etBalance.setText(String.format(Locale.getDefault(), "%.2f", account.getInitialBalance()));
        container.addView(etBalance);

        new AlertDialog.Builder(requireContext())
                .setTitle("Editar cuenta")
                .setView(container)
                .setPositiveButton("Guardar", (dialog, which) -> {
                    String newName = etName.getText().toString().trim();
                    String newBalanceStr = etBalance.getText().toString().trim();

                    if (newName.isEmpty()) {
                        Toast.makeText(requireContext(), "El nombre es obligatorio", Toast.LENGTH_LONG).show();
                        return;
                    }
                    double newBalance;
                    try {
                        newBalance = newBalanceStr.isEmpty() ? 0.0 : Double.parseDouble(newBalanceStr);
                    } catch (NumberFormatException e) {
                        Toast.makeText(requireContext(), "Saldo inicial no válido", Toast.LENGTH_LONG).show();
                        return;
                    }

                    account.setName(newName);
                    account.setInitialBalance(newBalance);
                    viewModel.updateAccount(familyId, account);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void onDeleteAccount(Account account) {
        if (familyId == null || account.getId() == null) return;

        new AlertDialog.Builder(requireContext())
                .setTitle("Eliminar cuenta")
                .setMessage("¿Seguro que quieres eliminar \"" + account.getName() + "\"?")
                .setPositiveButton("Eliminar", (dialog, which) -> viewModel.deleteAccount(familyId, account.getId()))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void navigateToMain() {
        Intent intent = new Intent(requireContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private interface OnAccountActionListener {
        void onAction(Account account);
    }

    // Adaptador con soporte de edición y borrado por fila
    private static class AccountsAdapter extends RecyclerView.Adapter<AccountsViewHolder> {
        private final List<Account> items;
        private final OnAccountActionListener onEdit;
        private final OnAccountActionListener onDelete;

        AccountsAdapter(List<Account> items, OnAccountActionListener onEdit, OnAccountActionListener onDelete) {
            this.items = items;
            this.onEdit = onEdit;
            this.onDelete = onDelete;
        }

        void setItems(List<Account> newItems) {
            items.clear();
            items.addAll(newItems);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public AccountsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemInitialAccountBinding itemBinding = ItemInitialAccountBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new AccountsViewHolder(itemBinding);
        }

        @Override
        public void onBindViewHolder(@NonNull AccountsViewHolder holder, int position) {
            Account a = items.get(position);
            holder.bind(a, onEdit, onDelete);
        }

        @Override
        public int getItemCount() { return items.size(); }
    }

    private static class AccountsViewHolder extends RecyclerView.ViewHolder {
        private final ItemInitialAccountBinding binding;

        AccountsViewHolder(@NonNull ItemInitialAccountBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Account a, OnAccountActionListener onEdit, OnAccountActionListener onDelete) {
            binding.tvItemName.setText(a.getName());
            binding.tvItemBalance.setText(String.format(Locale.getDefault(), "%,.2f", a.getCurrentBalance()));
            binding.btnItemEdit.setOnClickListener(v -> onEdit.onAction(a));
            binding.btnItemDelete.setOnClickListener(v -> onDelete.onAction(a));
        }
    }
}