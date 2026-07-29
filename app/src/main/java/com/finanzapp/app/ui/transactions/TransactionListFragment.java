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
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.finanzapp.app.FinanzAppApplication;
import com.finanzapp.app.R;
import com.finanzapp.app.data.export.ExcelExporter;
import com.finanzapp.app.data.export.PdfExporter;
import com.finanzapp.app.data.firebase.FirestorePaths;
import com.finanzapp.app.data.model.Account;
import com.finanzapp.app.data.model.Category;
import com.finanzapp.app.data.model.Family;
import com.finanzapp.app.data.model.FamilyMembership;
import com.finanzapp.app.data.model.Member;
import com.finanzapp.app.data.model.Transaction;
import com.finanzapp.app.data.model.User;
import com.finanzapp.app.util.Result;
import com.finanzapp.app.viewmodel.TransactionViewModel;
import com.finanzapp.app.viewmodel.ViewModelFactory;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

import android.net.Uri;
import android.util.Log;
import android.content.Intent;
import androidx.core.content.FileProvider;

public class TransactionListFragment extends Fragment {
    private TransactionViewModel viewModel;
    private TransactionAdapter adapter;
    private String familyId;
    private final Map<String, String> categoryNames = new HashMap<>();
    private final Map<String, String> categoryColors = new HashMap<>();
    private final Map<String, String> accountNames = new HashMap<>();
    private final Map<String, String> memberNames = new HashMap<>();
    private final Map<String, String> paymentMethodLabels = new HashMap<>();

    private Spinner spinnerFilterAccount, spinnerFilterCategory, spinnerFilterType, spinnerFilterMethod;
    private View btnFilterDate, emptyState, btnImportCsv;
    private ImageButton btnClearFiltersTop;
    private View btnClearFiltersDrawer;
    private View btnExport;
    private DrawerLayout drawerLayout;
    private View progressBar;

    private String filterAccountId = null;
    private String filterCategoryId = null;
    private List<String> filterCategoryIds = null;
    private String filterType = null;
    private String filterMethod = null;
    private Calendar filterStartDate = null;
    private Calendar filterEndDate = null;

    private String familyName = "";
    private List<Transaction> currentTransactions = new ArrayList<>();

    private boolean isInitializing = false;
    private boolean isPreselectionApplied = false;
    private String preselectedCategoryId = null;
    private String preselectedMethod = null;
    private String preselectedType = null;
    private String preselectedMemberUid = null;
    private long preselectedStartMillis = -1L;
    private long preselectedEndMillis = -1L;

    private List<Account> allAccounts = new ArrayList<>();
    private List<Category> allCategories = new ArrayList<>();
    // Cuentas archivadas: se excluyen del spinner de filtro y de la lista de movimientos,
    // ya que la lista de movimientos debe mostrar exclusivamente movimientos de cuentas activas.
    private final Set<String> archivedAccountIds = new HashSet<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_transaction_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        isInitializing = true;

        if (getArguments() != null) {
            preselectedCategoryId = getArguments().getString("preselectedCategoryId");
            preselectedMethod = getArguments().getString("preselectedMethod");
            preselectedType = getArguments().getString("preselectedType");
            preselectedMemberUid = getArguments().getString("preselectedMemberUid");
            preselectedStartMillis = getArguments().getLong("preselectedStartDateMillis", -1L);
            preselectedEndMillis = getArguments().getLong("preselectedEndDateMillis", -1L);
            String[] ids = getArguments().getStringArray("preselectedCategoryIds");
            if (ids != null) {
                filterCategoryIds = java.util.Arrays.asList(ids);
            }
        }

        FinanzAppApplication.AppContainer appContainer = ((FinanzAppApplication) requireActivity().getApplication()).getAppContainer();
        ViewModelFactory factory = new ViewModelFactory(appContainer);
        viewModel = new ViewModelProvider(requireActivity(), factory).get(TransactionViewModel.class);

        RecyclerView rvTransactions = view.findViewById(R.id.rv_transactions);
        FloatingActionButton fabAdd = view.findViewById(R.id.fab_add_transaction);

        spinnerFilterAccount = view.findViewById(R.id.spinner_filter_account);
        spinnerFilterCategory = view.findViewById(R.id.spinner_filter_category);
        spinnerFilterType = view.findViewById(R.id.spinner_filter_type);
        spinnerFilterMethod = view.findViewById(R.id.spinner_filter_method);
        btnFilterDate = view.findViewById(R.id.btn_filter_date);
        btnClearFiltersTop = view.findViewById(R.id.btn_clear_filters_top);
        btnClearFiltersDrawer = view.findViewById(R.id.btn_clear_filters_drawer);
        btnImportCsv = view.findViewById(R.id.btn_import_csv);
        emptyState = view.findViewById(R.id.ll_empty_state);
        progressBar = view.findViewById(R.id.pb_loading);
        drawerLayout = view.findViewById(R.id.drawer_layout);
        View btnOpenFilters = view.findViewById(R.id.btn_open_filters);
        androidx.appcompat.widget.Toolbar toolbar = view.findViewById(R.id.toolbar);

        toolbar.setNavigationOnClickListener(v -> Navigation.findNavController(view).navigateUp());

        btnOpenFilters.setOnClickListener(v -> drawerLayout.openDrawer(androidx.core.view.GravityCompat.END));

        paymentMethodLabels.putAll(getPaymentMethodLabels());

        adapter = new TransactionAdapter(new ArrayList<>(), categoryNames, categoryColors, accountNames, memberNames, paymentMethodLabels, new TransactionAdapter.OnTransactionClickListener() {
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
        btnImportCsv.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putString("familyId", familyId);
            Navigation.findNavController(v).navigate(R.id.action_transactionListFragment_to_importTransactionsFragment, args);
        });
        
        btnExport = view.findViewById(R.id.btn_export);
        btnExport.setOnClickListener(v -> showExportOptions());

        restoreFiltersFromViewModel();
        resolveFamilyId();

        isInitializing = false;
        updateTransactions();
    }

    private void restoreFiltersFromViewModel() {
        filterAccountId = viewModel.getFilterAccountId();
        filterCategoryId = viewModel.getFilterCategoryId();
        filterType = viewModel.getFilterType();
        filterMethod = viewModel.getFilterMethod();
        
        com.google.firebase.Timestamp start = viewModel.getFilterStartDate();
        if (start != null) {
            filterStartDate = Calendar.getInstance();
            filterStartDate.setTime(start.toDate());
        }
        
        com.google.firebase.Timestamp end = viewModel.getFilterEndDate();
        if (end != null) {
            filterEndDate = Calendar.getInstance();
            filterEndDate.setTime(end.toDate());
        }
    }

    private final String[] paymentMethodValues = {
            "tarjeta", "efectivo", "transferencia", "bizum",
            "tarjeta_restaurante", "tarjeta_transporte", "domiciliacion_bancaria"
    };

    private Map<String, String> getPaymentMethodLabels() {
        Map<String, String> labels = new HashMap<>();
        labels.put("tarjeta", getString(R.string.method_card));
        labels.put("efectivo", getString(R.string.method_cash));
        labels.put("transferencia", getString(R.string.method_transfer));
        labels.put("bizum", getString(R.string.method_bizum));
        labels.put("tarjeta_restaurante", getString(R.string.method_restaurant_card));
        labels.put("tarjeta_transporte", getString(R.string.method_transport_card));
        labels.put("domiciliacion_bancaria", getString(R.string.method_direct_debit));
        return labels;
    }

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
        if (filterType == null) spinnerFilterType.setSelection(0);
        else if ("expense".equals(filterType)) spinnerFilterType.setSelection(1);
        else if ("income".equals(filterType)) spinnerFilterType.setSelection(2);

        spinnerFilterType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) filterType = null;
                else if (position == 1) filterType = "expense";
                else filterType = "income";
                if (!isInitializing) updateTransactions();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Method Filter
        List<String> methodsList = new ArrayList<>();
        methodsList.add(getString(R.string.filter_all_methods));
        
        Map<String, String> labels = getPaymentMethodLabels();
        for (String val : paymentMethodValues) {
            methodsList.add(labels.get(val));
        }

        ArrayAdapter<String> methodAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, methodsList);
        methodAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFilterMethod.setAdapter(methodAdapter);
        
        if (filterMethod == null) {
            spinnerFilterMethod.setSelection(0);
        } else {
            for (int i = 0; i < paymentMethodValues.length; i++) {
                if (paymentMethodValues[i].equals(filterMethod)) {
                    spinnerFilterMethod.setSelection(i + 1);
                    break;
                }
            }
        }

        spinnerFilterMethod.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                filterMethod = position == 0 ? null : paymentMethodValues[position - 1];
                if (!isInitializing) updateTransactions();
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
                    Toast.makeText(requireContext(), R.string.error_invalid_date_range, Toast.LENGTH_LONG).show();
                    return;
                }

                filterStartDate = startCal;
                filterEndDate = endCal;
                updateTransactions();
            }, year, month, day);

            endPicker.setTitle(getString(R.string.dialog_select_end_date));
            endPicker.show();

        }, filterStartDate != null ? filterStartDate.get(Calendar.YEAR) : now.get(Calendar.YEAR),
                filterStartDate != null ? filterStartDate.get(Calendar.MONTH) : now.get(Calendar.MONTH),
                filterStartDate != null ? filterStartDate.get(Calendar.DAY_OF_MONTH) : now.get(Calendar.DAY_OF_MONTH));

        startPicker.setTitle(getString(R.string.dialog_select_start_date));
        startPicker.show();
    }

    private void clearFilters() {
        spinnerFilterAccount.setSelection(0);
        spinnerFilterCategory.setSelection(0);
        spinnerFilterType.setSelection(0);
        spinnerFilterMethod.setSelection(0);
        filterStartDate = null;
        filterEndDate = null;
        filterCategoryId = null;
        filterCategoryIds = null;
        updateTransactions();
    }

    private void showDeleteConfirmation(Transaction t) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete_transaction_title)
                .setMessage(R.string.delete_transaction_message)
                .setPositiveButton(R.string.delete_button, (dialog, which) -> viewModel.deleteTransaction(familyId, t))
                .setNegativeButton(R.string.cancel_button, null)
                .show();
    }

    private void resolveFamilyId() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            // Buscamos el documento del usuario para saber su familia activa
            FirebaseFirestore.getInstance().collection(FirestorePaths.USERS).document(uid).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        User user = documentSnapshot.toObject(User.class);
                        if (user != null && user.getFamilyId() != null) {
                            familyId = user.getFamilyId();
                            
                            // En lugar de hacer otra consulta a red para el rol, usamos la subcolección 
                            // de memberships que se lee desde el cache de Firestore (está disponible tras login)
                            FirebaseFirestore.getInstance().collection(FirestorePaths.getMembershipsPath(uid))
                                    .document(familyId).get()
                                    .addOnSuccessListener(membershipDoc -> {
                                        if (membershipDoc.exists()) {
                                            FamilyMembership membership = membershipDoc.toObject(FamilyMembership.class);
                                            if (membership != null) {
                                                boolean isAdmin = "admin".equals(membership.getRole()) || "owner".equals(membership.getRole());
                                                btnImportCsv.setVisibility(isAdmin ? View.VISIBLE : View.GONE);
                                            }
                                        }
                                    });

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
                categoryColors.clear();
                List<String> names = new ArrayList<>();
                names.add(getString(R.string.filter_all_categories));
                for (Category c : categories) {
                    categoryNames.put(c.getId(), c.getName());
                    categoryColors.put(c.getId(), c.getColor());
                    names.add(c.getName());
                }
                
                if ("GROUPED_OTHERS".equals(preselectedCategoryId)) {
                    // Si venimos de la porción "Otros", añadimos una opción virtual al spinner
                    names.add(getString(R.string.category_others));
                }

                adapter.notifyDataSetChanged();
                ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, names);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerFilterCategory.setAdapter(adapter);

                if (!isPreselectionApplied && preselectedCategoryId != null) {
                    if ("GROUPED_OTHERS".equals(preselectedCategoryId)) {
                        spinnerFilterCategory.setSelection(names.size() - 1);
                        filterCategoryId = null;
                    } else {
                        filterCategoryId = preselectedCategoryId;
                        for (int i = 0; i < allCategories.size(); i++) {
                            if (allCategories.get(i).getId().equals(filterCategoryId)) {
                                spinnerFilterCategory.setSelection(i + 1);
                                break;
                            }
                        }
                    }
                }

                spinnerFilterCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        if (position == 0) {
                            filterCategoryId = null;
                            filterCategoryIds = null;
                        } else if ("GROUPED_OTHERS".equals(preselectedCategoryId) && position == names.size() - 1) {
                            filterCategoryId = null;
                            // filterCategoryIds ya debería estar seteado desde onViewCreated
                        } else {
                            filterCategoryId = allCategories.get(position - 1).getId();
                            filterCategoryIds = null;
                        }
                        if (!isInitializing) updateTransactions();
                    }
                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {}
                });

                applyPreselectionIfReady();
            }
        });

        viewModel.getAccounts(familyId).observe(getViewLifecycleOwner(), accounts -> {
            if (accounts != null) {
                accountNames.clear();
                archivedAccountIds.clear();
                // El repositorio devuelve tanto cuentas activas como archivadas (otras pantallas
                // necesitan verlas todas). Aquí nos quedamos solo con las activas para el spinner
                // de filtro, y guardamos las archivadas para poder excluir sus movimientos más abajo.
                List<Account> activeAccounts = new ArrayList<>();
                for (Account a : accounts) {
                    accountNames.put(a.getId(), a.getName());
                    if (a.isActive()) {
                        activeAccounts.add(a);
                    } else {
                        archivedAccountIds.add(a.getId());
                    }
                }
                allAccounts = activeAccounts;

                List<String> names = new ArrayList<>();
                names.add(getString(R.string.filter_all_accounts));
                for (Account a : activeAccounts) {
                    names.add(a.getName());
                }
                ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, names);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerFilterAccount.setAdapter(adapter);

                if (filterAccountId != null) {
                    for (int i = 0; i < allAccounts.size(); i++) {
                        if (allAccounts.get(i).getId().equals(filterAccountId)) {
                            spinnerFilterAccount.setSelection(i + 1);
                            break;
                        }
                    }
                }

                spinnerFilterAccount.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        filterAccountId = position == 0 ? null : allAccounts.get(position - 1).getId();
                        if (!isInitializing) updateTransactions();
                    }
                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {}
                });

                applyPreselectionIfReady();

                // Puede que ya hubiera movimientos pintados (sin filtrar por archivadas) antes de
                // que se resolviera esta llamada; recalculamos para aplicar el filtro correctamente.
                if (!isInitializing) updateTransactions();
            }
        });

        viewModel.getMembers(familyId).observe(getViewLifecycleOwner(), members -> {
            if (members != null) {
                memberNames.clear();
                for (Member m : members) memberNames.put(m.getUid(), m.getDisplayName());
                adapter.notifyDataSetChanged();
            }
        });

        viewModel.getFamilyData(familyId).observe(getViewLifecycleOwner(), result -> {
            if (result instanceof Result.Success) {
                Family family = ((Result.Success<Family>) result).getData();
                familyName = family.getName();
            }
        });

        if (!isInitializing) updateTransactions();

        viewModel.getOperationResult().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof Result.Success) {
                Toast.makeText(requireContext(), getString(R.string.operation_success), Toast.LENGTH_SHORT).show();
            } else if (result instanceof Result.Error) {
                Exception e = ((Result.Error<?>) result).getException();
            String msg = e != null ? e.getMessage() : "";
            Toast.makeText(requireContext(), getString(R.string.error_with_message, msg), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void applyPreselectionIfReady() {
        if (isPreselectionApplied) return;
        if (allCategories.isEmpty() || allAccounts.isEmpty()) return;

        if (preselectedStartMillis != -1L && preselectedEndMillis != -1L) {
            filterStartDate = Calendar.getInstance();
            filterStartDate.setTimeInMillis(preselectedStartMillis);
            filterEndDate = Calendar.getInstance();
            filterEndDate.setTimeInMillis(preselectedEndMillis);
        }

        if (preselectedCategoryId != null) {
            if ("GROUPED_OTHERS".equals(preselectedCategoryId)) {
                // Already handled in spinner adapter setup
            } else {
                filterCategoryId = preselectedCategoryId;
                for (int i = 0; i < allCategories.size(); i++) {
                    if (allCategories.get(i).getId().equals(preselectedCategoryId)) {
                        spinnerFilterCategory.setSelection(i + 1);
                        break;
                    }
                }
            }
        }

        if (preselectedMethod != null) {
            filterMethod = preselectedMethod;
            for (int i = 0; i < paymentMethodValues.length; i++) {
                if (paymentMethodValues[i].equals(preselectedMethod)) {
                    spinnerFilterMethod.setSelection(i + 1);
                    break;
                }
            }
        }

        if (preselectedType != null) {
            filterType = preselectedType;
            if ("expense".equals(preselectedType)) spinnerFilterType.setSelection(1);
            else if ("income".equals(preselectedType)) spinnerFilterType.setSelection(2);
        }

        isPreselectionApplied = true;
        if (!isInitializing) updateTransactions();
    }

    private void updateTransactions() {
        if (familyId == null) return;

        // Sync local filters to ViewModel
        viewModel.setFilterAccountId(filterAccountId);
        viewModel.setFilterCategoryId(filterCategoryId);
        viewModel.setFilterCategoryIds(filterCategoryIds);
        viewModel.setFilterType(filterType);
        viewModel.setFilterMethod(filterMethod);
        viewModel.setFilterStartDate(filterStartDate != null ? new com.google.firebase.Timestamp(filterStartDate.getTime()) : null);
        viewModel.setFilterEndDate(filterEndDate != null ? new com.google.firebase.Timestamp(filterEndDate.getTime()) : null);

        Timestamp start = filterStartDate != null ? new Timestamp(filterStartDate.getTime()) : null;
        Timestamp end = filterEndDate != null ? new Timestamp(filterEndDate.getTime()) : null;

        progressBar.setVisibility(View.VISIBLE);
        emptyState.setVisibility(View.GONE);

        viewModel.getFilteredTransactions(familyId, filterAccountId, filterCategoryId, filterType, filterMethod, start, end)
                .observe(getViewLifecycleOwner(), transactions -> {
                    progressBar.setVisibility(View.GONE);
                    if (transactions != null) {
                        // El listado de movimientos debe ser exclusivamente de cuentas activas:
                        // TransactionRepository no distingue cuentas archivadas (no tiene por qué
                        // conocer ese concepto), así que se descartan aquí los movimientos cuya
                        // cuenta esté en archivedAccountIds.
                        List<Transaction> visibleTransactions = new ArrayList<>();
                        for (Transaction t : transactions) {
                            if (!archivedAccountIds.contains(t.getAccountId())) {
                                if (preselectedMemberUid != null && !preselectedMemberUid.equals(t.getCreatedBy())) {
                                    continue;
                                }
                                visibleTransactions.add(t);
                            }
                        }
                        currentTransactions = visibleTransactions;
                        adapter.updateTransactions(visibleTransactions);
                        emptyState.setVisibility(visibleTransactions.isEmpty() ? View.VISIBLE : View.GONE);
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

    private void showExportOptions() {
        if (currentTransactions.isEmpty()) {
            Toast.makeText(requireContext(), R.string.export_error_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        String[] options = {
                getString(R.string.export_option_excel),
                getString(R.string.export_option_pdf)
        };

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.export_options_title)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        exportToExcel();
                    } else {
                        exportToPdf();
                    }
                })
                .show();
    }

    private void exportToExcel() {
        progressBar.setVisibility(View.VISIBLE);
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                File cachePath = new File(requireContext().getCacheDir(), "exports");
                if (!cachePath.exists() && !cachePath.mkdirs()) throw new IOException("Could not create cache");

                File file = new File(cachePath, "movimientos_" + System.currentTimeMillis() + ".xlsx");
                FileOutputStream outputStream = new FileOutputStream(file);

                ExcelExporter.exportTransactions(
                        currentTransactions,
                        categoryNames,
                        accountNames,
                        memberNames,
                        paymentMethodLabels,
                        outputStream
                );
                outputStream.close();

                requireActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    shareFile(file, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                });
            } catch (Exception e) {
                Log.e("TransactionList", "Excel export error", e);
                requireActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(requireContext(), R.string.export_error_generic, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void exportToPdf() {
        progressBar.setVisibility(View.VISIBLE);
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                File cachePath = new File(requireContext().getCacheDir(), "exports");
                if (!cachePath.exists() && !cachePath.mkdirs()) throw new IOException("Could not create cache");

                File file = new File(cachePath, "movimientos_" + System.currentTimeMillis() + ".pdf");
                FileOutputStream outputStream = new FileOutputStream(file);

                PdfExporter.exportTransactions(
                        currentTransactions,
                        familyName,
                        categoryNames,
                        accountNames,
                        memberNames,
                        paymentMethodLabels,
                        outputStream
                );
                outputStream.close();

                requireActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    shareFile(file, "application/pdf");
                });
            } catch (Exception e) {
                Log.e("TransactionList", "PDF export error", e);
                requireActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(requireContext(), R.string.export_error_generic, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void shareFile(File file, String mimeType) {
        Uri contentUri = FileProvider.getUriForFile(requireContext(), "com.finanzapp.app.fileprovider", file);
        if (contentUri != null) {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            shareIntent.setDataAndType(contentUri, mimeType);
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            shareIntent.setType(mimeType);
            startActivity(Intent.createChooser(shareIntent, getString(R.string.export_sharing_title)));
        }
    }


}