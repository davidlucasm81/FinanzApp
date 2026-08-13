package com.finanzapp.app.ui.transactions;

import android.app.DatePickerDialog;
import android.os.Bundle;
import androidx.core.os.BundleCompat;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.finanzapp.app.R;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.finanzapp.app.FinanzAppApplication;
import com.finanzapp.app.data.model.Account;
import com.finanzapp.app.data.model.Category;
import com.finanzapp.app.data.model.Transaction;
import com.finanzapp.app.util.Result;
import com.finanzapp.app.viewmodel.TransactionViewModel;
import com.finanzapp.app.viewmodel.ViewModelFactory;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AddEditTransactionFragment extends Fragment {
    private TransactionViewModel viewModel;
    private String familyId;
    private Transaction existingTransaction;
    
    private TextInputLayout tilAmount, tilDescription;
    private RadioButton rbExpense, rbIncome;
    private Button btnDate;
    private Spinner spinnerAccount, spinnerMethod, spinnerCreatedBy;
    private AutoCompleteTextView autoCategory;
    private Category selectedCategory;
    
    // Shared Expenses UI
    private View layoutSharedExpenses, layoutAccountSelector;
    private Spinner spinnerPaidBy;
    private ViewGroup layoutSplitMembers;
    private RadioGroup rgSplitMode;
    private View layoutCustomAmounts;
    private View layoutSplitSummary;
    private TextView tvCurrentSum, tvRemainingAmount;
    private Button btnDistributeRemaining;

    private final List<com.google.android.material.checkbox.MaterialCheckBox> splitCheckboxes = new ArrayList<>();
    private final Map<String, TextInputLayout> customAmountInputs = new HashMap<>();
    private final Map<String, Double> preservedCustomAmounts = new HashMap<>();
    private boolean isSharedExpensesMode = false;
    
    private final Calendar selectedDate = Calendar.getInstance();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
    
    private List<Category> allCategories = new ArrayList<>();
    private List<Account> allAccounts = new ArrayList<>();
    private List<com.finanzapp.app.data.model.Member> allMembers = new ArrayList<>();
    private final String[] paymentMethodValues = {
            "tarjeta", "efectivo", "transferencia", "bizum", 
            "tarjeta_restaurante", "tarjeta_transporte", "domiciliacion_bancaria"
    };

    private String[] getPaymentMethodLabels() {
        return new String[]{
                getString(R.string.method_card),
                getString(R.string.method_cash),
                getString(R.string.method_transfer),
                getString(R.string.method_bizum),
                getString(R.string.method_restaurant_card),
                getString(R.string.method_transport_card),
                getString(R.string.method_direct_debit)
        };
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_add_edit_transaction, container, false);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            familyId = getArguments().getString("familyId");
            existingTransaction = BundleCompat.getSerializable(getArguments(), "transaction", Transaction.class);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FinanzAppApplication.AppContainer appContainer = ((FinanzAppApplication) requireActivity().getApplication()).getAppContainer();
        ViewModelFactory factory = new ViewModelFactory(appContainer);
        viewModel = new ViewModelProvider(this, factory).get(TransactionViewModel.class);

        tilAmount = view.findViewById(R.id.til_amount);
        tilDescription = view.findViewById(R.id.til_description);
        RadioGroup rgType = view.findViewById(R.id.rg_type);
        rbExpense = view.findViewById(R.id.rb_expense);
        rbIncome = view.findViewById(R.id.rb_income);
        btnDate = view.findViewById(R.id.btn_date);
        Button btnSave = view.findViewById(R.id.btn_save);
        Button btnDelete = view.findViewById(R.id.btn_delete);
        autoCategory = view.findViewById(R.id.auto_category);
        spinnerAccount = view.findViewById(R.id.spinner_account);
        spinnerMethod = view.findViewById(R.id.spinner_method);
        spinnerCreatedBy = view.findViewById(R.id.spinner_created_by);
        
        layoutAccountSelector = view.findViewById(R.id.layout_account_selector);
        layoutSharedExpenses = view.findViewById(R.id.layout_shared_expenses);
        spinnerPaidBy = view.findViewById(R.id.spinner_paid_by);
        layoutSplitMembers = view.findViewById(R.id.layout_split_members);
        rgSplitMode = view.findViewById(R.id.rg_split_mode);
        layoutCustomAmounts = view.findViewById(R.id.layout_custom_amounts);
        layoutSplitSummary = view.findViewById(R.id.layout_split_summary);
        tvCurrentSum = view.findViewById(R.id.tv_current_sum);
        tvRemainingAmount = view.findViewById(R.id.tv_remaining_amount);
        btnDistributeRemaining = view.findViewById(R.id.btn_distribute_remaining);

        com.google.android.material.appbar.MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
        if (existingTransaction != null) {
            toolbar.setTitle(getString(R.string.dialog_edit_transaction));
        }
        toolbar.setNavigationOnClickListener(v -> Navigation.findNavController(v).popBackStack());

        updateDateButton();
        btnDate.setOnClickListener(v -> showDatePicker());
        
        rgType.setOnCheckedChangeListener((group, checkedId) -> filterCategories());

        setupMethodSpinner();
        setupObservers();
        
        btnSave.setOnClickListener(v -> saveTransaction());
        btnDistributeRemaining.setOnClickListener(v -> distributeRemaining());

        if (tilAmount.getEditText() != null) {
            tilAmount.getEditText().addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override
                public void afterTextChanged(Editable s) {
                    if (isSharedExpensesMode && rbExpense.isChecked() && rgSplitMode.getCheckedRadioButtonId() == R.id.rb_split_custom) {
                        updateSplitSummary();
                    }
                }
            });
        }
        
        if (existingTransaction != null) {
            populateFields();
            btnDelete.setVisibility(View.VISIBLE);
            btnDelete.setOnClickListener(v -> showDeleteConfirmation());
        }
    }

    private void populateFields() {
        if (tilAmount.getEditText() != null) tilAmount.getEditText().setText(String.valueOf(existingTransaction.getAmount()));
        if (tilDescription.getEditText() != null) tilDescription.getEditText().setText(existingTransaction.getDescription());
        
        if ("income".equals(existingTransaction.getType())) {
            rbIncome.setChecked(true);
        } else {
            rbExpense.setChecked(true);
        }

        selectedDate.setTime(existingTransaction.getDate().toDate());
        updateDateButton();

        for (int i = 0; i < paymentMethodValues.length; i++) {
            if (paymentMethodValues[i].equals(existingTransaction.getPaymentMethod())) {
                spinnerMethod.setSelection(i);
                break;
            }
        }
        
        // Populate category will be handled in observer after allCategories are loaded
    }

    private void showDeleteConfirmation() {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete_transaction_title)
                .setMessage(R.string.delete_transaction_message)
                .setPositiveButton(R.string.delete_button, (dialog, which) -> viewModel.deleteTransaction(familyId, existingTransaction))
                .setNegativeButton(R.string.cancel_button, null)
                .show();
    }

    private void setupMethodSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, getPaymentMethodLabels());
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMethod.setAdapter(adapter);
    }

    private void setupObservers() {
        viewModel.getCategories(familyId).observe(getViewLifecycleOwner(), categories -> {
            if (categories != null) {
                allCategories = categories;
                filterCategories();
                
                if (existingTransaction != null && selectedCategory == null) {
                    for (Category c : allCategories) {
                        if (c.getId().equals(existingTransaction.getCategoryId())) {
                            selectedCategory = c;
                            autoCategory.setText(c.getName(), false);
                            break;
                        }
                    }
                }
            }
        });

        viewModel.getAccounts(familyId).observe(getViewLifecycleOwner(), accounts -> {
            if (accounts != null) {
                allAccounts = accounts;
                List<String> names = new ArrayList<>();
                int selectedIndex = -1;
                for (int i = 0; i < accounts.size(); i++) {
                    Account a = accounts.get(i);
                    if (a.isActive()) {
                        names.add(a.getName());
                        if (existingTransaction != null && a.getId().equals(existingTransaction.getAccountId())) {
                            selectedIndex = names.size() - 1;
                        }
                    }
                }
                ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, names);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerAccount.setAdapter(adapter);
                if (selectedIndex != -1) spinnerAccount.setSelection(selectedIndex);
            }
        });

        viewModel.getMembers(familyId).observe(getViewLifecycleOwner(), members -> {
            if (members != null) {
                allMembers = members;
                setupMembersUI(members);
            }
        });

        viewModel.getOperationResult().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof Result.Success) {
                String msg = existingTransaction != null ? getString(R.string.transaction_updated) : getString(R.string.transaction_saved);
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
                Navigation.findNavController(requireView()).popBackStack();
            } else if (result instanceof Result.Error) {
                Toast.makeText(requireContext(), R.string.error_save_transaction, Toast.LENGTH_LONG).show();
            }
        });

        // Fetch family to check mode
        viewModel.getFamilyData(familyId).observe(getViewLifecycleOwner(), result -> {
            if (result instanceof Result.Success) {
                com.finanzapp.app.data.model.Family family = ((Result.Success<com.finanzapp.app.data.model.Family>) result).getData();
                isSharedExpensesMode = "shared_expenses".equals(family.getMode());
                updateSharedExpensesVisibility();
            }
        });

        rbExpense.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateSharedExpensesVisibility();
            if (isChecked && isSharedExpensesMode && rgSplitMode.getCheckedRadioButtonId() == R.id.rb_split_custom) {
                updateSplitSummary();
            }
        });
        rbIncome.setOnCheckedChangeListener((buttonView, isChecked) -> updateSharedExpensesVisibility());

        rgSplitMode.setOnCheckedChangeListener((group, checkedId) -> {
            boolean isCustom = checkedId == R.id.rb_split_custom;
            layoutCustomAmounts.setVisibility(isCustom ? View.VISIBLE : View.GONE);
            layoutSplitSummary.setVisibility(isCustom ? View.VISIBLE : View.GONE);
            if (isCustom) {
                updateCustomAmountInputs();
                updateSplitSummary();
            }
        });
    }

    private void setupMembersUI(List<com.finanzapp.app.data.model.Member> members) {
        List<String> names = new ArrayList<>();
        int selectedIndexCreatedBy = -1;
        int selectedIndexPaidBy = -1;
        String currentUserId = viewModel.getCurrentUserId();
        String targetUidCreatedBy = existingTransaction != null ? existingTransaction.getCreatedBy() : currentUserId;
        String targetUidPaidBy = existingTransaction != null ? existingTransaction.getPaidByUid() : currentUserId;

        layoutSplitMembers.removeAllViews();
        splitCheckboxes.clear();

        for (int i = 0; i < members.size(); i++) {
            com.finanzapp.app.data.model.Member m = members.get(i);
            String displayName = m.getDisplayName();
            if (m.getUid() != null && m.getUid().equals(currentUserId)) {
                displayName += " (" + getString(R.string.label_me) + ")";
            }
            names.add(displayName);
            if (targetUidCreatedBy != null && targetUidCreatedBy.equals(m.getUid())) {
                selectedIndexCreatedBy = i;
            }
            if (targetUidPaidBy != null && targetUidPaidBy.equals(m.getUid())) {
                selectedIndexPaidBy = i;
            }

            // Setup checkboxes for split
            com.google.android.material.checkbox.MaterialCheckBox cb = new com.google.android.material.checkbox.MaterialCheckBox(requireContext());
            cb.setText(displayName);
            cb.setTag(m.getUid());
            
            // Default check all if new transaction, or check those in splitAmongUids
            if (existingTransaction == null) {
                cb.setChecked(true);
            } else if (existingTransaction.getSplitAmongUids() != null) {
                cb.setChecked(existingTransaction.getSplitAmongUids().contains(m.getUid()));
            }

            cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (rgSplitMode.getCheckedRadioButtonId() == R.id.rb_split_custom) {
                    updateCustomAmountInputs();
                }
            });

            layoutSplitMembers.addView(cb);
            splitCheckboxes.add(cb);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        
        spinnerCreatedBy.setAdapter(adapter);
        if (selectedIndexCreatedBy != -1) spinnerCreatedBy.setSelection(selectedIndexCreatedBy);

        spinnerPaidBy.setAdapter(adapter);
        if (selectedIndexPaidBy != -1) spinnerPaidBy.setSelection(selectedIndexPaidBy);
        
        if (existingTransaction != null) {
            if ("custom".equals(existingTransaction.getSplitMode())) {
                rgSplitMode.check(R.id.rb_split_custom);
                layoutCustomAmounts.setVisibility(View.VISIBLE);
                updateCustomAmountInputs();
            } else {
                rgSplitMode.check(R.id.rb_split_equal);
            }
        }
    }

    private void updateCustomAmountInputs() {
        if (layoutCustomAmounts == null) return;
        
        // Preserve current values before clearing
        for (Map.Entry<String, TextInputLayout> entry : customAmountInputs.entrySet()) {
            if (entry.getValue().getEditText() != null) {
                String s = entry.getValue().getEditText().getText().toString().trim();
                if (!s.isEmpty()) {
                    try {
                        double val = Double.parseDouble(s.replace(',', '.'));
                        preservedCustomAmounts.put(entry.getKey(), val);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        ((ViewGroup) layoutCustomAmounts).removeAllViews();
        customAmountInputs.clear();

        TextWatcher splitWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                updateSplitSummary();
            }
        };

        for (com.google.android.material.checkbox.MaterialCheckBox cb : splitCheckboxes) {
            if (cb.isChecked()) {
                String uid = (String) cb.getTag();
                String name = cb.getText().toString();

                TextInputLayout til = new TextInputLayout(requireContext(), null, com.google.android.material.R.style.Widget_MaterialComponents_TextInputLayout_OutlinedBox);
                til.setHint(name);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, 8, 0, 8);
                til.setLayoutParams(lp);

                com.google.android.material.textfield.TextInputEditText et = new com.google.android.material.textfield.TextInputEditText(til.getContext());
                et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
                et.addTextChangedListener(splitWatcher);
                til.addView(et);

                ((ViewGroup) layoutCustomAmounts).addView(til);
                customAmountInputs.put(uid, til);
                
                if (preservedCustomAmounts.containsKey(uid)) {
                    et.setText(String.valueOf(preservedCustomAmounts.get(uid)));
                } else if (existingTransaction != null && existingTransaction.getSplitAmounts() != null && existingTransaction.getSplitAmounts().containsKey(uid)) {
                    et.setText(String.valueOf(existingTransaction.getSplitAmounts().get(uid)));
                }
            }
        }
    }

    private void updateSplitSummary() {
        if (layoutSplitSummary == null) return;

        double totalAmount = getEnteredAmount();
        double currentSum = 0;
        for (TextInputLayout til : customAmountInputs.values()) {
            if (til.getEditText() != null) {
                String s = til.getEditText().getText().toString().trim();
                if (!s.isEmpty()) {
                    try {
                        currentSum += Double.parseDouble(s.replace(',', '.'));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        double remaining = totalAmount - currentSum;
        
        tvCurrentSum.setText(getString(R.string.label_current_sum, String.format(Locale.getDefault(), "%.2f", currentSum)));
        
        if (Math.abs(remaining) < 0.01) {
            tvRemainingAmount.setText(R.string.label_total_match);
            tvRemainingAmount.setTextColor(getResources().getColor(R.color.success, null));
            btnDistributeRemaining.setVisibility(View.GONE);
        } else {
            tvRemainingAmount.setText(getString(R.string.label_remaining_amount, String.format(Locale.getDefault(), "%.2f", remaining)));
            tvRemainingAmount.setTextColor(getResources().getColor(R.color.error, null));
            btnDistributeRemaining.setVisibility(View.VISIBLE);
        }
    }

    private double getEnteredAmount() {
        String amountStr = tilAmount.getEditText() != null ? tilAmount.getEditText().getText().toString().trim() : "";
        if (amountStr.isEmpty()) return 0;
        try {
            return Math.abs(Double.parseDouble(amountStr.replace(',', '.')));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void distributeRemaining() {
        double totalAmount = getEnteredAmount();
        int count = customAmountInputs.size();
        if (count == 0) return;

        double currentSum = 0;
        // Map to store current values associated with their TextInputLayout
        Map<TextInputLayout, Double> currentValues = new HashMap<>();

        // 1. Read current values (treat empty as 0)
        for (TextInputLayout til : customAmountInputs.values()) {
            double val = 0;
            if (til.getEditText() != null) {
                String s = til.getEditText().getText().toString().trim();
                if (!s.isEmpty()) {
                    try {
                        val = Double.parseDouble(s.replace(',', '.'));
                    } catch (NumberFormatException ignored) {}
                }
            }
            currentValues.put(til, val);
            currentSum += val;
        }

        double amountToDistribute = totalAmount - currentSum;

        // 2. Distribute the REMAINING amount equitably among ALL selected members
        double perPerson = Math.floor((amountToDistribute / count) * 100) / 100;
        double remainder = amountToDistribute - (perPerson * count);

        // 3. ADD the share to each member's current value
        int i = 0;
        for (TextInputLayout til : customAmountInputs.values()) {
            if (til.getEditText() != null) {
                double add = perPerson + (i == 0 ? remainder : 0);
                Double oldValueObj = currentValues.get(til);
                double oldValue = oldValueObj != null ? oldValueObj : 0.0;
                double newValue = oldValue + add;
                // Ensure we don't go below 0 (though normally amountToDistribute would be positive)
                til.getEditText().setText(String.format(Locale.US, "%.2f", Math.max(0, newValue)));
            }
            i++;
        }
    }

    private void filterCategories() {
        if (allCategories.isEmpty()) return;
        
        String type = rbIncome.isChecked() ? "income" : "expense";
        List<Category> filtered = new ArrayList<>();
        
        for (Category c : allCategories) {
            if (c.getAppliesTo().equals(type) || c.getAppliesTo().equals("both")) {
                filtered.add(c);
            }
        }

        CategoryAdapter adapter = new CategoryAdapter(requireContext(), filtered);
        autoCategory.post(() -> {
            autoCategory.setAdapter(adapter);
            // Ensure the dropdown is shown when typing
            autoCategory.setThreshold(1);
        });
        
        autoCategory.setOnItemClickListener((parent, view, position, id) -> {
            selectedCategory = (Category) parent.getItemAtPosition(position);
        });

        // Clear selection if current selectedCategory is not in filtered list
        if (selectedCategory != null) {
            boolean found = false;
            for (Category c : filtered) {
                if (c.getId().equals(selectedCategory.getId())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                selectedCategory = null;
                autoCategory.setText("", false);
            }
        } else {
            // Also check if typed text matches something that is now filtered out
            String currentText = autoCategory.getText().toString().trim();
            if (!currentText.isEmpty()) {
                boolean found = false;
                for (Category c : filtered) {
                    if (c.getName().equalsIgnoreCase(currentText)) {
                        found = true;
                        selectedCategory = c;
                        break;
                    }
                }
                if (!found) {
                    autoCategory.setText("", false);
                }
            }
        }
    }

    private void showDatePicker() {
        new DatePickerDialog(requireContext(), (view, year, month, dayOfMonth) -> {
            selectedDate.set(year, month, dayOfMonth);
            updateDateButton();
        }, selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH), selectedDate.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void updateDateButton() {
        btnDate.setText(dateFormat.format(selectedDate.getTime()));
    }

    private void updateSharedExpensesVisibility() {
        if (layoutSharedExpenses != null) {
            layoutSharedExpenses.setVisibility(isSharedExpensesMode && rbExpense.isChecked() ? View.VISIBLE : View.GONE);
        }
        if (layoutAccountSelector != null) {
            layoutAccountSelector.setVisibility(isSharedExpensesMode ? View.GONE : View.VISIBLE);
        }
    }

    private void saveTransaction() {
        tilAmount.setError(null);
        String amountStr = tilAmount.getEditText() != null ? tilAmount.getEditText().getText().toString().trim() : "";
        if (amountStr.isEmpty()) {
            tilAmount.setError(getString(R.string.error_amount));
            return;
        }

        double amount;
        try {
            // Replace comma with dot for parsing and ensure positive amount
            amount = Math.abs(Double.parseDouble(amountStr.replace(',', '.')));
        } catch (NumberFormatException e) {
            tilAmount.setError(getString(R.string.error_invalid_format));
            return;
        }

        String description = tilDescription.getEditText() != null ? tilDescription.getEditText().getText().toString().trim() : "";
        
        if (!isSharedExpensesMode && spinnerAccount.getSelectedItem() == null) {
            Toast.makeText(requireContext(), getString(R.string.error_account), Toast.LENGTH_SHORT).show();
            return;
        }
        
        String accountId = null;
        if (isSharedExpensesMode) {
            // In shared expenses mode, we use the first active account (Joint Account)
            for (Account a : allAccounts) {
                if (a.isActive()) {
                    accountId = a.getId();
                    break;
                }
            }
        } else {
            String selectedAccountName = spinnerAccount.getSelectedItem().toString();
            for (Account a : allAccounts) {
                if (a.getName().equals(selectedAccountName)) {
                    accountId = a.getId();
                    break;
                }
            }
        }

        if (accountId == null) {
            Toast.makeText(requireContext(), getString(R.string.error_account), Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedCategory == null) {
            // Try to resolve by text if user typed but didn't click
            String currentText = autoCategory.getText().toString().trim();
            String type = rbIncome.isChecked() ? "income" : "expense";
            for (Category c : allCategories) {
                if (c.getName().equalsIgnoreCase(currentText) && 
                    (c.getAppliesTo().equals(type) || c.getAppliesTo().equals("both"))) {
                    selectedCategory = c;
                    break;
                }
            }
        }

        if (selectedCategory == null) {
            Toast.makeText(requireContext(), getString(R.string.error_category), Toast.LENGTH_SHORT).show();
            return;
        }
        String categoryId = selectedCategory.getId();
        
        String method = paymentMethodValues[spinnerMethod.getSelectedItemPosition()];
        String type = rbIncome.isChecked() ? "income" : "expense";
        
        String createdBy = null;
        if (spinnerCreatedBy.getSelectedItem() != null && !allMembers.isEmpty()) {
            createdBy = allMembers.get(spinnerCreatedBy.getSelectedItemPosition()).getUid();
        }

        // Shared Expenses data
        String paidByUid = null;
        List<String> splitAmongUids = null;
        String splitMode = null;
        Map<String, Double> splitAmounts = null;

        if (isSharedExpensesMode && "expense".equals(type)) {
            paidByUid = allMembers.get(spinnerPaidBy.getSelectedItemPosition()).getUid();
            splitAmongUids = new ArrayList<>();
            for (com.google.android.material.checkbox.MaterialCheckBox cb : splitCheckboxes) {
                if (cb.isChecked()) {
                    splitAmongUids.add((String) cb.getTag());
                }
            }

            if (!splitAmongUids.isEmpty()) {
                splitMode = rgSplitMode.getCheckedRadioButtonId() == R.id.rb_split_custom ? "custom" : "equal";
                if ("custom".equals(splitMode)) {
                    splitAmounts = new HashMap<>();
                    double totalSplit = 0;
                    for (String uid : splitAmongUids) {
                        TextInputLayout til = customAmountInputs.get(uid);
                        if (til != null && til.getEditText() != null) {
                            String s = til.getEditText().getText().toString().trim();
                            double val = 0;
                            if (!s.isEmpty()) {
                                try {
                                    val = Double.parseDouble(s.replace(',', '.'));
                                } catch (NumberFormatException ignored) {}
                            }
                            splitAmounts.put(uid, val);
                            totalSplit += val;
                        }
                    }

                    // Validate sum with a very small margin for floating point precision
                    if (Math.abs(totalSplit - amount) > 0.001) {
                        Toast.makeText(requireContext(), R.string.error_split_total, Toast.LENGTH_LONG).show();
                        return;
                    }
                }
            } else {
                // If nobody selected for split, it's a normal expense (null fields)
                paidByUid = null;
                splitAmongUids = null;
            }
        }

        Transaction t;
        if (existingTransaction != null) {
            t = new Transaction(
                    existingTransaction.getId(), accountId, new Timestamp(selectedDate.getTime()),
                    description, amount, type, categoryId, method, createdBy, existingTransaction.getCreatedAt()
            );
        } else {
            t = new Transaction(
                    null, accountId, new Timestamp(selectedDate.getTime()), 
                    description, amount, type, categoryId, method, createdBy, null
            );
        }
        
        t.setPaidByUid(paidByUid);
        t.setSplitAmongUids(splitAmongUids);
        t.setSplitMode(splitMode);
        t.setSplitAmounts(splitAmounts);

        if (existingTransaction != null) {
            viewModel.updateTransaction(familyId, existingTransaction, t);
        } else {
            viewModel.addTransaction(familyId, t);
        }
    }
}
