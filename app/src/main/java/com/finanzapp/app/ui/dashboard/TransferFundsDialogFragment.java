package com.finanzapp.app.ui.dashboard;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
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
    private final List<Account> allActiveAccounts = new ArrayList<>();
    private List<Account> fromAccounts = new ArrayList<>();
    private List<Account> toAccounts = new ArrayList<>();
    private boolean isUpdating = false;

    private Spinner spinnerFrom;
    private Spinner spinnerTo;

    public static TransferFundsDialogFragment newInstance() {
        return new TransferFundsDialogFragment();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View view = getLayoutInflater().inflate(R.layout.dialog_transfer_funds, null);

        spinnerFrom = view.findViewById(R.id.spinner_from_account);
        spinnerTo = view.findViewById(R.id.spinner_to_account);
        TextInputLayout tilAmount = view.findViewById(R.id.til_amount);
        Button btnTransfer = view.findViewById(R.id.btn_transfer);
        Button btnCancel = view.findViewById(R.id.btn_cancel);

        FinanzAppApplication.AppContainer appContainer = ((FinanzAppApplication) requireActivity().getApplication()).getAppContainer();
        ViewModelFactory factory = new ViewModelFactory(appContainer);
        viewModel = new ViewModelProvider(requireParentFragment(), factory).get(DashboardViewModel.class);

        viewModel.getAccountsList().observe(this, accounts -> {
            allActiveAccounts.clear();
            for (Account account : accounts) {
                if (account.isActive()) {
                    allActiveAccounts.add(account);
                }
            }
            
            // Initial population
            setupSpinners();
        });

        spinnerFrom.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!isUpdating) {
                    updateToSpinner();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        spinnerTo.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!isUpdating) {
                    updateFromSpinner();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        btnTransfer.setOnClickListener(v -> {
            if (fromAccounts.isEmpty() || toAccounts.isEmpty()) return;

            int fromPos = spinnerFrom.getSelectedItemPosition();
            int toPos = spinnerTo.getSelectedItemPosition();

            if (fromPos < 0 || toPos < 0) return;

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

            String fromId = fromAccounts.get(fromPos).getId();
            String toId = toAccounts.get(toPos).getId();

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

    private void setupSpinners() {
        if (allActiveAccounts.size() < 2) {
            // Not enough accounts for a transfer
            ArrayAdapter<String> emptyAdapter = new ArrayAdapter<>(requireContext(),
                    android.R.layout.simple_spinner_item, new String[]{getString(R.string.no_accounts_available)});
            spinnerFrom.setAdapter(emptyAdapter);
            spinnerTo.setAdapter(emptyAdapter);
            return;
        }

        isUpdating = true;
        
        // Initial state: From has all, To has all except the first one selected in From
        fromAccounts = new ArrayList<>(allActiveAccounts);
        updateSpinnerAdapter(spinnerFrom, fromAccounts);
        
        Account firstFrom = fromAccounts.get(0);
        toAccounts = new ArrayList<>();
        for (Account a : allActiveAccounts) {
            if (!a.getId().equals(firstFrom.getId())) {
                toAccounts.add(a);
            }
        }
        updateSpinnerAdapter(spinnerTo, toAccounts);
        
        isUpdating = false;
    }

    private void updateFromSpinner() {
        if (allActiveAccounts.size() < 2) return;
        
        isUpdating = true;
        
        Account selectedTo = toAccounts.get(spinnerTo.getSelectedItemPosition());
        Account selectedFromBefore = spinnerFrom.getSelectedItemPosition() >= 0 ? 
                fromAccounts.get(spinnerFrom.getSelectedItemPosition()) : null;
        
        fromAccounts.clear();
        int newSelection = 0;
        for (Account a : allActiveAccounts) {
            if (!a.getId().equals(selectedTo.getId())) {
                fromAccounts.add(a);
                if (selectedFromBefore != null && a.getId().equals(selectedFromBefore.getId())) {
                    newSelection = fromAccounts.size() - 1;
                }
            }
        }
        
        updateSpinnerAdapter(spinnerFrom, fromAccounts);
        spinnerFrom.setSelection(newSelection);
        
        isUpdating = false;
    }

    private void updateToSpinner() {
        if (allActiveAccounts.size() < 2) return;
        
        isUpdating = true;
        
        Account selectedFrom = fromAccounts.get(spinnerFrom.getSelectedItemPosition());
        Account selectedToBefore = spinnerTo.getSelectedItemPosition() >= 0 ? 
                toAccounts.get(spinnerTo.getSelectedItemPosition()) : null;
        
        toAccounts.clear();
        int newSelection = 0;
        for (Account a : allActiveAccounts) {
            if (!a.getId().equals(selectedFrom.getId())) {
                toAccounts.add(a);
                if (selectedToBefore != null && a.getId().equals(selectedToBefore.getId())) {
                    newSelection = toAccounts.size() - 1;
                }
            }
        }
        
        updateSpinnerAdapter(spinnerTo, toAccounts);
        spinnerTo.setSelection(newSelection);
        
        isUpdating = false;
    }

    private void updateSpinnerAdapter(Spinner spinner, List<Account> accounts) {
        List<String> names = new ArrayList<>();
        for (Account a : accounts) names.add(a.getName());
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }
}
