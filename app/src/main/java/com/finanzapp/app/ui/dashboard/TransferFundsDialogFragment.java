package com.finanzapp.app.ui.dashboard;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.finanzapp.app.FinanzAppApplication;
import com.finanzapp.app.R;
import com.finanzapp.app.data.model.Account;
import com.finanzapp.app.util.Result;
import com.finanzapp.app.viewmodel.DashboardViewModel;
import com.finanzapp.app.viewmodel.ViewModelFactory;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

public class TransferFundsDialogFragment extends DialogFragment {
    private DashboardViewModel viewModel;
    private List<Account> activeAccounts = new ArrayList<>();

    public static TransferFundsDialogFragment newInstance() {
        return new TransferFundsDialogFragment();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View view = getLayoutInflater().inflate(R.layout.dialog_transfer_funds, null);

        Spinner spinnerFrom = view.findViewById(R.id.spinner_from_account);
        Spinner spinnerTo = view.findViewById(R.id.spinner_to_account);
        TextInputLayout tilAmount = view.findViewById(R.id.til_amount);
        Button btnTransfer = view.findViewById(R.id.btn_transfer);
        Button btnCancel = view.findViewById(R.id.btn_cancel);

        FinanzAppApplication.AppContainer appContainer = ((FinanzAppApplication) requireActivity().getApplication()).getAppContainer();
        ViewModelFactory factory = new ViewModelFactory(appContainer);
        viewModel = new ViewModelProvider(requireParentFragment(), factory).get(DashboardViewModel.class);

        viewModel.getAccountsList().observe(this, accounts -> {
            activeAccounts.clear();
            List<String> accountNames = new ArrayList<>();
            for (Account account : accounts) {
                if (account.isActive()) {
                    activeAccounts.add(account);
                    accountNames.add(account.getName());
                }
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                    android.R.layout.simple_spinner_item, accountNames);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerFrom.setAdapter(adapter);
            spinnerTo.setAdapter(adapter);
            
            // Default: second account as destination if exists
            if (activeAccounts.size() > 1) {
                spinnerTo.setSelection(1);
            }
        });

        btnTransfer.setOnClickListener(v -> {
            if (activeAccounts.isEmpty()) return;

            int fromPos = spinnerFrom.getSelectedItemPosition();
            int toPos = spinnerTo.getSelectedItemPosition();

            if (fromPos == toPos) {
                Toast.makeText(requireContext(), R.string.error_same_accounts, Toast.LENGTH_SHORT).show();
                return;
            }

            String amountStr = tilAmount.getEditText() != null ? tilAmount.getEditText().getText().toString() : "";
            if (amountStr.isEmpty()) {
                tilAmount.setError(getString(R.string.error_amount));
                return;
            }

            double amount;
            try {
                amount = Double.parseDouble(amountStr);
            } catch (NumberFormatException e) {
                tilAmount.setError(getString(R.string.error_invalid_format));
                return;
            }

            if (amount <= 0) {
                tilAmount.setError(getString(R.string.error_amount));
                return;
            }

            String fromId = activeAccounts.get(fromPos).getId();
            String toId = activeAccounts.get(toPos).getId();

            viewModel.transferFunds(fromId, toId, amount);
        });

        btnCancel.setOnClickListener(v -> dismiss());

        viewModel.getTransferResult().observe(this, result -> {
            if (result instanceof Result.Success) {
                Toast.makeText(requireContext(), R.string.operation_success, Toast.LENGTH_SHORT).show();
                dismiss();
            } else if (result instanceof Result.Error) {
                String errorMsg = ((Result.Error<?>) result).getException().getMessage();
                Toast.makeText(requireContext(), errorMsg != null ? errorMsg : getString(R.string.error_generic), Toast.LENGTH_SHORT).show();
            }
        });

        builder.setView(view);
        return builder.create();
    }
}
