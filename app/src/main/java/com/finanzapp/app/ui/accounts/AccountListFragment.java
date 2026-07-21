package com.finanzapp.app.ui.accounts;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

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
        ViewModelFactory factory = new ViewModelFactory(appContainer.getAuthRepository(), appContainer.getFamilyRepository(), appContainer.getAccountRepository());
        viewModel = new ViewModelProvider(this, factory).get(AccountViewModel.class);
        familyViewModel = new ViewModelProvider(requireActivity(), factory).get(FamilyViewModel.class);

        setupRecycler();
        setupObservers();

        binding.fabAdd.setOnClickListener(v -> showAddEditDialog(null));

        resolveFamilyId();
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

    /**
     * Comprueba (en tiempo real) el rol del usuario actual dentro de la familia.
     * Solo un admin/owner puede archivar o eliminar cuentas (ver AGENTS.md sección 5 y las
     * reglas de seguridad de Firestore para families/{familyId}/accounts). Un member normal
     * solo debe ver la opción de editar el nombre; si viera los botones de archivar/eliminar
     * y los pulsara, Firestore rechazaría la escritura con PERMISSION_DENIED.
     */
    private void checkAdminRole(String familyId) {
        String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
        if (uid == null || familyId == null) return;

        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection(com.finanzapp.app.data.firebase.FirestorePaths.getFamilyPath(familyId) + "/members")
                .document(uid)
                .addSnapshotListener((doc, error) -> {
                    if (error != null || doc == null || !doc.exists()) return;
                    String role = doc.getString("role");
                    boolean admin = "admin".equals(role) || "owner".equals(role);
                    if (admin != isAdmin) {
                        isAdmin = admin;
                        if (adapter != null) adapter.setIsAdmin(isAdmin);
                    }
                });
    }

    private void setupRecycler() {
        adapter = new AccountsAdapter(new ArrayList<>(), this::onEdit, this::onArchive, this::onDelete);
        binding.rvAccounts.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvAccounts.setAdapter(adapter);
    }

    private void setupObservers() {
        // Using getFamilyData from FamilyViewModel (which we saw in Dashboard)
        familyViewModel.getFamilyData().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof Result.Success) {
                com.finanzapp.app.data.model.Family family = ((Result.Success<com.finanzapp.app.data.model.Family>) result).getData();
                familyId = family.getId();
                currencyCode = family.getCurrencyCode();
                checkAdminRole(familyId);

                viewModel.getAccounts(familyId).observe(getViewLifecycleOwner(), accounts -> {
                    if (accounts != null) {
                        adapter.setItems(accounts, currencyCode);
                        binding.tvEmpty.setVisibility(accounts.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                });
            }
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
            Toast.makeText(requireContext(), "Error: " + (e != null ? e.getMessage() : "Desconocido"), Toast.LENGTH_LONG).show();
        }
    }

    private void showAddEditDialog(@Nullable Account account) {
        if (familyId == null) {
            Toast.makeText(requireContext(), "Error: No se pudo identificar la familia", Toast.LENGTH_LONG).show();
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
                .setTitle("Eliminar cuenta")
                .setMessage("¿Seguro que quieres eliminar \"" + account.getName() + "\"?")
                .setPositiveButton("Eliminar", (dialog, which) -> viewModel.deleteAccount(familyId, account.getId()))
                .setNegativeButton("Cancelar", null)
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

        @NonNull
        @Override
        public AccountsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemAccountBinding b = ItemAccountBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new AccountsViewHolder(b);
        }

        @Override
        public void onBindViewHolder(@NonNull AccountsViewHolder holder, int position) {
            holder.bind(items.get(position), currency, isAdmin, onEdit, onArchive, onDelete);
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

        void bind(Account a, String currency, boolean isAdmin, OnAccountActionListener onEdit, OnAccountActionListener onArchive, OnAccountActionListener onDelete) {
            binding.tvItemName.setText(a.getName());
            binding.tvItemBalance.setText(String.format(Locale.getDefault(), "%,.2f %s", a.getCurrentBalance(), currency));
            binding.tvItemStatus.setVisibility(a.isActive() ? View.GONE : View.VISIBLE);

            // Icon for archive/unarchive (using standard android icons)
            binding.btnItemArchive.setImageResource(a.isActive() ? android.R.drawable.ic_menu_save : android.R.drawable.ic_menu_view);

            // Editar, Archivar y Eliminar están restringidos a admin/owner (ver AGENTS.md
            // sección 5 y las reglas de Firestore para accounts). Un member normal no puede
            // modificar la cuenta de ningún modo: solo la ve en modo lectura.
            // Archivar y Eliminar son además mutuamente excluyentes entre sí, según si la
            // cuenta tiene movimientos asociados.
            boolean hasMovements = a.isHasTransactions();
            binding.btnItemEdit.setVisibility(isAdmin ? View.VISIBLE : View.GONE);
            binding.btnItemArchive.setVisibility(isAdmin && hasMovements ? View.VISIBLE : View.GONE);
            binding.btnItemDelete.setVisibility(isAdmin && !hasMovements ? View.VISIBLE : View.GONE);

            binding.btnItemEdit.setOnClickListener(v -> onEdit.onAction(a));
            binding.btnItemArchive.setOnClickListener(v -> onArchive.onAction(a));
            binding.btnItemDelete.setOnClickListener(v -> onDelete.onAction(a));

            // Visual feedback for archived accounts
            binding.getRoot().setAlpha(a.isActive() ? 1.0f : 0.6f);
        }
    }
}