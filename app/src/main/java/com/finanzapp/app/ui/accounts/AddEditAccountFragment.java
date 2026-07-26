package com.finanzapp.app.ui.accounts;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.finanzapp.app.FinanzAppApplication;
import com.finanzapp.app.R;
import com.finanzapp.app.data.model.Account;

import com.finanzapp.app.databinding.DialogAddEditAccountBinding;
import com.finanzapp.app.viewmodel.AccountViewModel;
import com.finanzapp.app.viewmodel.ViewModelFactory;

import java.util.Objects;

public class AddEditAccountFragment extends DialogFragment {
    private DialogAddEditAccountBinding binding;
    private AccountViewModel viewModel;
    private String familyId;
    private Account accountToEdit;

    public static AddEditAccountFragment newInstance(String familyId, @Nullable Account account) {
        AddEditAccountFragment fragment = new AddEditAccountFragment();
        Bundle args = new Bundle();
        args.putString("familyId", familyId);
        if (account != null) {
            args.putString("accountId", account.getId());
            args.putString("name", account.getName());
            args.putDouble("initialBalance", account.getInitialBalance());
        }
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        if (getArguments() != null) {
            familyId = getArguments().getString("familyId");
            String accountId = getArguments().getString("accountId");
            if (accountId != null) {
                accountToEdit = new Account();
                accountToEdit.setId(accountId);
                accountToEdit.setName(getArguments().getString("name"));
                accountToEdit.setInitialBalance(getArguments().getDouble("initialBalance"));
            }
        }

        binding = DialogAddEditAccountBinding.inflate(getLayoutInflater());

        FinanzAppApplication.AppContainer appContainer = ((FinanzAppApplication) requireActivity().getApplication()).getAppContainer();
        ViewModelFactory factory = new ViewModelFactory(appContainer);
        // Use requireParentFragment() to share the ViewModel with AccountListFragment
        viewModel = new ViewModelProvider(requireParentFragment(), factory).get(AccountViewModel.class);

        if (accountToEdit != null) {
            binding.etName.setText(accountToEdit.getName());
            binding.etInitialBalance.setText(String.valueOf(accountToEdit.getInitialBalance()));
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext())
                .setTitle(accountToEdit == null ? R.string.label_add_account_title : R.string.dialog_edit_account)
                .setView(binding.getRoot())
                .setPositiveButton(R.string.label_save_btn, null)
                .setNegativeButton(R.string.cancel_button, (dialog, which) -> dismiss());

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> onSave()));

        return dialog;
    }

    private void onSave() {
        String name = Objects.requireNonNull(Objects.requireNonNull(binding.etName.getText())).toString().trim();
        String balanceStr = Objects.requireNonNull(Objects.requireNonNull(binding.etInitialBalance.getText())).toString().trim();

        if (name.isEmpty()) {
            binding.tilName.setError(getString(R.string.error_field_required));
            return;
        }

        double balance = 0;
        try {
            if (!balanceStr.isEmpty()) balance = Double.parseDouble(balanceStr);
        } catch (NumberFormatException e) {
            binding.tilInitialBalance.setError(getString(R.string.error_invalid_format));
            return;
        }

        if (accountToEdit == null) {
            Account newAccount = new Account();
            newAccount.setName(name);
            newAccount.setInitialBalance(balance);
            viewModel.createAccount(familyId, newAccount);
        } else {
            accountToEdit.setName(name);
            accountToEdit.setInitialBalance(balance);
            viewModel.updateAccount(familyId, accountToEdit);
        }
        dismiss();
    }
}