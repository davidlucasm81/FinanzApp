package com.finanzapp.app.ui.transactions;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.finanzapp.app.FinanzAppApplication;
import com.finanzapp.app.R;
import com.finanzapp.app.data.firebase.FirestorePaths;
import com.finanzapp.app.data.model.Account;
import com.finanzapp.app.data.model.Category;
import com.finanzapp.app.data.model.Member;
import com.finanzapp.app.data.model.Transaction;
import com.finanzapp.app.data.model.User;
import com.finanzapp.app.util.Result;
import com.finanzapp.app.viewmodel.TransactionViewModel;
import com.finanzapp.app.viewmodel.ViewModelFactory;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TransactionListFragment extends Fragment {
    private TransactionViewModel viewModel;
    private TransactionAdapter adapter;
    private String familyId;
    private final Map<String, String> categoryNames = new HashMap<>();
    private final Map<String, String> accountNames = new HashMap<>();
    private final Map<String, String> memberNames = new HashMap<>();

    private Spinner spinnerFilterAccount, spinnerFilterCategory, spinnerFilterType, spinnerFilterMethod;
    private View btnFilterDate, emptyState;
    private ImageButton btnClearFiltersTop;
    private View btnClearFiltersDrawer;
    private DrawerLayout drawerLayout;
    
    private String filterAccountId = null;
    private String filterCategoryId = null;
    private String filterType = null;
    private String filterMethod = null;
    private Calendar filterStartDate = null;
    private Calendar filterEndDate = null;

    private List<Account> allAccounts = new ArrayList<>();
    private List<Category> allCategories = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_transaction_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FinanzAppApplication.AppContainer appContainer = ((FinanzAppApplication) requireActivity().getApplication()).getAppContainer();
        ViewModelFactory factory = new ViewModelFactory(appContainer.getAuthRepository(), appContainer.getFamilyRepository(), 
                                                        appContainer.getAccountRepository(), appContainer.getCategoryRepository(),
                                                        appContainer.getTransactionRepository());
        viewModel = new ViewModelProvider(this, factory).get(TransactionViewModel.class);

        RecyclerView rvTransactions = view.findViewById(R.id.rv_transactions);
        FloatingActionButton fabAdd = view.findViewById(R.id.fab_add_transaction);
        
        spinnerFilterAccount = view.findViewById(R.id.spinner_filter_account);
        spinnerFilterCategory = view.findViewById(R.id.spinner_filter_category);
        spinnerFilterType = view.findViewById(R.id.spinner_filter_type);
        spinnerFilterMethod = view.findViewById(R.id.spinner_filter_method);
        btnFilterDate = view.findViewById(R.id.btn_filter_date);
        btnClearFiltersTop = view.findViewById(R.id.btn_clear_filters_top);
        btnClearFiltersDrawer = view.findViewById(R.id.btn_clear_filters_drawer);
        emptyState = view.findViewById(R.id.ll_empty_state);
        drawerLayout = view.findViewById(R.id.drawer_layout);
        View btnOpenFilters = view.findViewById(R.id.btn_open_filters);

        btnOpenFilters.setOnClickListener(v -> drawerLayout.openDrawer(androidx.core.view.GravityCompat.END));

        adapter = new TransactionAdapter(new ArrayList<>(), categoryNames, accountNames, memberNames, new TransactionAdapter.OnTransactionClickListener() {
            @Override
            public void onTransactionClick(Transaction t) {
                Bundle args = new Bundle();
                args.putString("familyId", familyId);
                args.putSerializable("transaction", t);
                Navigation.findNavController(requireView()).navigate(R.id.action_transactionListFragment_to_addEditTransactionFragment, args);
            }

            @Override
            public void onTransactionLongClick(Transaction t) {
                showDeleteConfirmation(t);
            }
        });
        rvTransactions.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvTransactions.setAdapter(adapter);

        fabAdd.setOnClickListener(v -> {
            if (familyId != null) {
                Bundle args = new Bundle();
                args.putString("familyId", familyId);
                Navigation.findNavController(v).navigate(R.id.action_transactionListFragment_to_addEditTransactionFragment, args);
            }
        });

        setupFilters();
        resolveFamilyId();
    }

    private final String[] paymentMethods = {
            "Tarjeta", "Efectivo", "Transferencia", "Bizum", 
            "Tarjeta restaurante", "Tarjeta transporte", "Domiciliación bancaria"
    };
    private final String[] paymentMethodValues = {
            "tarjeta", "efectivo", "transferencia", "bizum", 
            "tarjeta_restaurante", "tarjeta_transporte", "domiciliacion_bancaria"
    };

    private void setupFilters() {
        // Type Filter
        String[] types = {
                getString(R.string.filter_all_types),
                getString(R.string.filter_expenses),
                getString(R.string.filter_income)
        };
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, types);
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFilterType.setAdapter(typeAdapter);
        spinnerFilterType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) filterType = null;
                else if (position == 1) filterType = "expense";
                else filterType = "income";
                updateTransactions();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Method Filter
        List<String> methodsList = new ArrayList<>();
        methodsList.add(getString(R.string.filter_all_methods));
        for (String m : paymentMethods) methodsList.add(m);
        
        ArrayAdapter<String> methodAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, methodsList);
        methodAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFilterMethod.setAdapter(methodAdapter);
        spinnerFilterMethod.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                filterMethod = position == 0 ? null : paymentMethodValues[position - 1];
                updateTransactions();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        btnFilterDate.setOnClickListener(v -> showDateRangePicker());
        btnClearFiltersTop.setOnClickListener(v -> clearFilters());
        btnClearFiltersDrawer.setOnClickListener(v -> clearFilters());
    }

    private void showDateRangePicker() {
        Calendar now = Calendar.getInstance();
        
        DatePickerDialog startPicker = new DatePickerDialog(requireContext(), (view, year, month, day) -> {
            Calendar startCal = Calendar.getInstance();
            startCal.set(year, month, day, 0, 0, 0);
            startCal.set(Calendar.MILLISECOND, 0);
            
            DatePickerDialog endPicker = new DatePickerDialog(requireContext(), (view2, year2, month2, day2) -> {
                Calendar endCal = Calendar.getInstance();
                endCal.set(year2, month2, day2, 23, 59, 59);
                endCal.set(Calendar.MILLISECOND, 999);
                
                if (endCal.before(startCal)) {
                    Toast.makeText(requireContext(), "La fecha de fin debe ser posterior a la de inicio", Toast.LENGTH_LONG).show();
                    return;
                }
                
                filterStartDate = startCal;
                filterEndDate = endCal;
                updateTransactions();
            }, year, month, day);
            
            endPicker.setTitle("Selecciona fecha de fin");
            endPicker.show();
            
        }, filterStartDate != null ? filterStartDate.get(Calendar.YEAR) : now.get(Calendar.YEAR),
           filterStartDate != null ? filterStartDate.get(Calendar.MONTH) : now.get(Calendar.MONTH),
           filterStartDate != null ? filterStartDate.get(Calendar.DAY_OF_MONTH) : now.get(Calendar.DAY_OF_MONTH));

        startPicker.setTitle("Selecciona fecha de inicio");
        startPicker.show();
    }

    private void clearFilters() {
        spinnerFilterAccount.setSelection(0);
        spinnerFilterCategory.setSelection(0);
        spinnerFilterType.setSelection(0);
        spinnerFilterMethod.setSelection(0);
        filterStartDate = null;
        filterEndDate = null;
        updateTransactions();
    }

    private void showDeleteConfirmation(Transaction t) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete_transaction_title)
                .setMessage(R.string.delete_transaction_message)
                .setPositiveButton(R.string.delete_button, (dialog, which) -> {
                    viewModel.deleteTransaction(familyId, t);
                })
                .setNegativeButton(R.string.cancel_button, null)
                .show();
    }

    private void resolveFamilyId() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            FirebaseFirestore.getInstance().collection(FirestorePaths.USERS).document(uid).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        User user = documentSnapshot.toObject(User.class);
                        if (user != null && user.getFamilyId() != null) {
                            familyId = user.getFamilyId();
                            observeData();
                        }
                    });
        }
    }

    private void observeData() {
        viewModel.getCategories(familyId).observe(getViewLifecycleOwner(), categories -> {
            if (categories != null) {
                allCategories = categories;
                categoryNames.clear();
                List<String> names = new ArrayList<>();
                names.add(getString(R.string.filter_all_categories));
                for (Category c : categories) {
                    categoryNames.put(c.getId(), c.getName());
                    names.add(c.getName());
                }
                ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, names);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerFilterCategory.setAdapter(adapter);
                spinnerFilterCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        filterCategoryId = position == 0 ? null : allCategories.get(position - 1).getId();
                        updateTransactions();
                    }
                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {}
                });
            }
        });

        viewModel.getAccounts(familyId).observe(getViewLifecycleOwner(), accounts -> {
            if (accounts != null) {
                allAccounts = accounts;
                accountNames.clear();
                List<String> names = new ArrayList<>();
                names.add(getString(R.string.filter_all_accounts));
                for (Account a : accounts) {
                    accountNames.put(a.getId(), a.getName());
                    names.add(a.getName());
                }
                ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, names);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerFilterAccount.setAdapter(adapter);
                spinnerFilterAccount.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        filterAccountId = position == 0 ? null : allAccounts.get(position - 1).getId();
                        updateTransactions();
                    }
                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {}
                });
            }
        });

        viewModel.getMembers(familyId).observe(getViewLifecycleOwner(), members -> {
            if (members != null) {
                memberNames.clear();
                for (Member m : members) memberNames.put(m.getUid(), m.getDisplayName());
                adapter.notifyDataSetChanged();
            }
        });

        updateTransactions();
        
        viewModel.getOperationResult().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof Result.Success) {
                Toast.makeText(requireContext(), getString(R.string.operation_success), Toast.LENGTH_SHORT).show();
            } else if (result instanceof Result.Error) {
                Toast.makeText(requireContext(), "Error: " + ((Result.Error<?>) result).getException().getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updateTransactions() {
        if (familyId == null) return;

        Timestamp start = filterStartDate != null ? new Timestamp(filterStartDate.getTime()) : null;
        Timestamp end = filterEndDate != null ? new Timestamp(filterEndDate.getTime()) : null;
        
        viewModel.getFilteredTransactions(familyId, filterAccountId, filterCategoryId, filterType, filterMethod, start, end)
                .observe(getViewLifecycleOwner(), transactions -> {
                    if (transactions != null) {
                        adapter.updateTransactions(transactions);
                        emptyState.setVisibility(transactions.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                });
        
        boolean hasFilters = filterAccountId != null || filterCategoryId != null || filterType != null || filterMethod != null || filterStartDate != null;
        btnClearFiltersTop.setVisibility(hasFilters ? View.VISIBLE : View.GONE);
        btnClearFiltersDrawer.setVisibility(hasFilters ? View.VISIBLE : View.GONE);

        if (filterStartDate != null && filterEndDate != null) {
            SimpleDateFormat btnFormat = new SimpleDateFormat("dd/MM", Locale.getDefault());
            ((android.widget.Button)btnFilterDate).setText(btnFormat.format(filterStartDate.getTime()) + " - " + btnFormat.format(filterEndDate.getTime()));
        } else {
            ((android.widget.Button)btnFilterDate).setText(R.string.filter_dates);
        }
    }

    private static class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.ViewHolder> {
        interface OnTransactionClickListener {
            void onTransactionClick(Transaction t);
            void onTransactionLongClick(Transaction t);
        }

        private final List<Transaction> transactions;
        private final Map<String, String> categoryNames;
        private final Map<String, String> accountNames;
        private final Map<String, String> memberNames;
        private final OnTransactionClickListener listener;
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("es", "ES"));

        TransactionAdapter(List<Transaction> transactions, Map<String, String> categoryNames, 
                           Map<String, String> accountNames, Map<String, String> memberNames,
                           OnTransactionClickListener listener) {
            this.transactions = transactions;
            this.categoryNames = categoryNames;
            this.accountNames = accountNames;
            this.memberNames = memberNames;
            this.listener = listener;
        }

        void updateTransactions(List<Transaction> newTransactions) {
            this.transactions.clear();
            this.transactions.addAll(newTransactions);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transaction, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Transaction t = transactions.get(position);
            
            holder.tvDate.setText(t.getDate() != null ? dateFormat.format(t.getDate().toDate()) : "");
            holder.tvCategory.setText(categoryNames.getOrDefault(t.getCategoryId(), "Categoría"));
            holder.tvDescription.setText(t.getDescription());
            holder.tvAccount.setText(accountNames.getOrDefault(t.getAccountId(), "Cuenta"));
            
            String creatorName = memberNames.getOrDefault(t.getCreatedBy(), "Usuario");
            holder.tvCreator.setText(holder.itemView.getContext().getString(R.string.by_user, creatorName));

            boolean isIncome = "income".equals(t.getType());
            double amount = t.getAmount();
            String amountStr = (isIncome ? "+" : "-") + currencyFormat.format(amount);
            holder.tvAmount.setText(amountStr);
            holder.tvAmount.setTextColor(isIncome ? 0xFF2E7D32 : 0xFFB00020);

            holder.itemView.setOnClickListener(v -> listener.onTransactionClick(t));
            holder.itemView.setOnLongClickListener(v -> {
                listener.onTransactionLongClick(t);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return transactions.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvDate, tvCategory, tvDescription, tvAmount, tvAccount, tvCreator;

            ViewHolder(View itemView) {
                super(itemView);
                tvDate = itemView.findViewById(R.id.tv_date);
                tvCategory = itemView.findViewById(R.id.tv_category);
                tvDescription = itemView.findViewById(R.id.tv_description);
                tvAmount = itemView.findViewById(R.id.tv_amount);
                tvAccount = itemView.findViewById(R.id.tv_account);
                tvCreator = itemView.findViewById(R.id.tv_creator);
            }
        }
    }
}
