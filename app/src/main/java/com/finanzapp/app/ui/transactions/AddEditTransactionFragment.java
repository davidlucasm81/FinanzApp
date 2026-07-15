package com.finanzapp.app.ui.transactions;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
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
    
    private TextInputLayout tilAmount, tilDescription;
    private RadioGroup rgType;
    private RadioButton rbExpense, rbIncome;
    private Button btnDate, btnSave;
    private Spinner spinnerCategory, spinnerAccount, spinnerMethod;
    
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
        spinnerCategory = view.findViewById(R.id.spinner_category);
        spinnerAccount = view.findViewById(R.id.spinner_account);
        spinnerMethod = view.findViewById(R.id.spinner_method);

        updateDateButton();
        btnDate.setOnClickListener(v -> showDatePicker());
        
        rgType.setOnCheckedChangeListener((group, checkedId) -> filterCategories());

        setupMethodSpinner();
        setupObservers();
        
        btnSave.setOnClickListener(v -> saveTransaction());
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
            }
        });

        viewModel.getAccounts(familyId).observe(getViewLifecycleOwner(), accounts -> {
            if (accounts != null) {
                allAccounts = accounts;
                List<String> names = new ArrayList<>();
                for (Account a : accounts) if (a.isActive()) names.add(a.getName());
                ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, names);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerAccount.setAdapter(adapter);
            }
        });

        viewModel.getOperationResult().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof Result.Success) {
                Toast.makeText(requireContext(), "Movimiento guardado", Toast.LENGTH_SHORT).show();
                Navigation.findNavController(requireView()).popBackStack();
            } else if (result instanceof Result.Error) {
                Toast.makeText(requireContext(), "Error al guardar", Toast.LENGTH_SHORT).show();
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
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategory.setAdapter(adapter);
        spinnerCategory.setTag(filtered);
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
            tilAmount.setError("Introduce un importe");
            return;
        }
        double amount = Double.parseDouble(amountStr);
        String description = tilDescription.getEditText() != null ? tilDescription.getEditText().getText().toString().trim() : "";
        
        if (spinnerAccount.getSelectedItem() == null) {
            Toast.makeText(requireContext(), "Selecciona una cuenta", Toast.LENGTH_SHORT).show();
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

        List<Category> filtered = (List<Category>) spinnerCategory.getTag();
        if (filtered == null || spinnerCategory.getSelectedItem() == null) {
            Toast.makeText(requireContext(), "Selecciona una categoría", Toast.LENGTH_SHORT).show();
            return;
        }
        String categoryId = filtered.get(spinnerCategory.getSelectedItemPosition()).getId();
        
        String method = paymentMethodValues[spinnerMethod.getSelectedItemPosition()];
        String type = rbIncome.isChecked() ? "income" : "expense";

        Transaction t = new Transaction(
                null, accountId, new Timestamp(selectedDate.getTime()), 
                description, amount, type, categoryId, method, null, null
        );

        viewModel.addTransaction(familyId, t);
    }
}
