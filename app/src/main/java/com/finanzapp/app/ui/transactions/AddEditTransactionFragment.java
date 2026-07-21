package com.finanzapp.app.ui.transactions;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.finanzapp.app.FinanzAppApplication;
import com.finanzapp.app.R;
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
import java.util.List;
import java.util.Locale;

public class AddEditTransactionFragment extends Fragment {
    private TransactionViewModel viewModel;
    private String familyId;
    private Transaction existingTransaction;
    
    private TextInputLayout tilAmount, tilDescription;
    private RadioGroup rgType;
    private RadioButton rbExpense, rbIncome;
    private Button btnDate, btnSave, btnDelete;
    private Spinner spinnerAccount, spinnerMethod;
    private AutoCompleteTextView autoCategory;
    private Category selectedCategory;
    
    private Calendar selectedDate = Calendar.getInstance();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
    
    private List<Category> allCategories = new ArrayList<>();
    private List<Account> allAccounts = new ArrayList<>();
    private final String[] paymentMethods = {
            "Tarjeta", "Efectivo", "Transferencia", "Bizum", 
            "Tarjeta restaurante", "Tarjeta transporte", "Domiciliación bancaria"
    };
    private final String[] paymentMethodValues = {
            "tarjeta", "efectivo", "transferencia", "bizum", 
            "tarjeta_restaurante", "tarjeta_transporte", "domiciliacion_bancaria"
    };

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
            existingTransaction = (Transaction) getArguments().getSerializable("transaction");
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FinanzAppApplication.AppContainer appContainer = ((FinanzAppApplication) requireActivity().getApplication()).getAppContainer();
        ViewModelFactory factory = new ViewModelFactory(appContainer.getAuthRepository(), appContainer.getFamilyRepository(), 
                                                        appContainer.getAccountRepository(), appContainer.getCategoryRepository(),
                                                        appContainer.getTransactionRepository());
        viewModel = new ViewModelProvider(this, factory).get(TransactionViewModel.class);

        tilAmount = view.findViewById(R.id.til_amount);
        tilDescription = view.findViewById(R.id.til_description);
        rgType = view.findViewById(R.id.rg_type);
        rbExpense = view.findViewById(R.id.rb_expense);
        rbIncome = view.findViewById(R.id.rb_income);
        btnDate = view.findViewById(R.id.btn_date);
        btnSave = view.findViewById(R.id.btn_save);
        btnDelete = view.findViewById(R.id.btn_delete);
        autoCategory = view.findViewById(R.id.auto_category);
        spinnerAccount = view.findViewById(R.id.spinner_account);
        spinnerMethod = view.findViewById(R.id.spinner_method);

        com.google.android.material.appbar.MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
        if (existingTransaction != null) {
            toolbar.setTitle("Editar Movimiento");
        }
        toolbar.setNavigationOnClickListener(v -> Navigation.findNavController(v).popBackStack());

        updateDateButton();
        btnDate.setOnClickListener(v -> showDatePicker());
        
        rgType.setOnCheckedChangeListener((group, checkedId) -> filterCategories());

        setupMethodSpinner();
        setupObservers();
        
        btnSave.setOnClickListener(v -> saveTransaction());
        
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
        new android.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete_transaction_title)
                .setMessage(R.string.delete_transaction_message)
                .setPositiveButton(R.string.delete_button, (dialog, which) -> {
                    viewModel.deleteTransaction(familyId, existingTransaction);
                })
                .setNegativeButton(R.string.cancel_button, null)
                .show();
    }

    private void setupMethodSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, paymentMethods);
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

        viewModel.getOperationResult().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof Result.Success) {
                String msg = existingTransaction != null ? getString(R.string.transaction_updated) : getString(R.string.transaction_saved);
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
                Navigation.findNavController(requireView()).popBackStack();
            } else if (result instanceof Result.Error) {
                Toast.makeText(requireContext(), "Error al guardar", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void filterCategories() {
        String type = rbIncome.isChecked() ? "income" : "expense";
        List<String> names = new ArrayList<>();
        List<Category> filtered = new ArrayList<>();
        
        for (Category c : allCategories) {
            if (c.getAppliesTo().equals(type) || c.getAppliesTo().equals("both")) {
                filtered.add(c);
                names.add(c.getName());
            }
        }
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, names);
        autoCategory.setAdapter(adapter);
        
        autoCategory.setOnItemClickListener((parent, view, position, id) -> {
            String selectedName = (String) parent.getItemAtPosition(position);
            for (Category c : allCategories) {
                if (c.getName().equals(selectedName)) {
                    selectedCategory = c;
                    break;
                }
            }
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
                autoCategory.setText("");
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

    private void saveTransaction() {
        String amountStr = tilAmount.getEditText() != null ? tilAmount.getEditText().getText().toString().trim() : "";
        if (amountStr.isEmpty()) {
            tilAmount.setError(getString(R.string.error_amount));
            return;
        }
        // Replace comma with dot for parsing
        double amount = Double.parseDouble(amountStr.replace(',', '.'));
        String description = tilDescription.getEditText() != null ? tilDescription.getEditText().getText().toString().trim() : "";
        
        if (spinnerAccount.getSelectedItem() == null) {
            Toast.makeText(requireContext(), getString(R.string.error_account), Toast.LENGTH_SHORT).show();
            return;
        }
        
        String accountId = null;
        String selectedAccountName = spinnerAccount.getSelectedItem().toString();
        for (Account a : allAccounts) {
            if (a.getName().equals(selectedAccountName)) {
                accountId = a.getId();
                break;
            }
        }

        if (selectedCategory == null) {
            // Try to resolve by text if user typed but didn't click
            String currentText = autoCategory.getText().toString().trim();
            for (Category c : allCategories) {
                if (c.getName().equalsIgnoreCase(currentText)) {
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

        if (existingTransaction != null) {
            Transaction updated = new Transaction(
                    existingTransaction.getId(), accountId, new Timestamp(selectedDate.getTime()),
                    description, amount, type, categoryId, method, existingTransaction.getCreatedBy(), existingTransaction.getCreatedAt()
            );
            viewModel.updateTransaction(familyId, existingTransaction, updated);
        } else {
            Transaction t = new Transaction(
                    null, accountId, new Timestamp(selectedDate.getTime()), 
                    description, amount, type, categoryId, method, null, null
            );
            viewModel.addTransaction(familyId, t);
        }
    }
}
