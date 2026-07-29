package com.finanzapp.app.ui.statistics;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.finanzapp.app.FinanzAppApplication;
import com.finanzapp.app.R;
import com.finanzapp.app.data.model.Category;
import com.finanzapp.app.data.model.DashboardCategorySummary;
import com.finanzapp.app.data.model.Member;
import com.finanzapp.app.data.model.MemberSummary;
import com.finanzapp.app.data.model.PaymentMethodSummary;
import com.finanzapp.app.data.model.Transaction;
import com.finanzapp.app.ui.transactions.TransactionAdapter;
import com.finanzapp.app.data.model.statistics.MonthlySummary;
import com.finanzapp.app.databinding.FragmentStatisticsBinding;
import com.finanzapp.app.util.ChartUtils;
import com.finanzapp.app.viewmodel.StatisticsViewModel;
import com.finanzapp.app.viewmodel.ViewModelFactory;
import com.github.mikephil.charting.charts.CombinedChart;
import com.github.mikephil.charting.charts.HorizontalBarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.CombinedData;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.PercentFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.google.android.material.datepicker.MaterialDatePicker;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Currency;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import androidx.navigation.Navigation;

public class StatisticsFragment extends Fragment implements OnChartValueSelectedListener {

    private FragmentStatisticsBinding binding;
    private StatisticsViewModel viewModel;
    private String currentCurrencyCode = "EUR";
    private LegendAdapter legendAdapter;
    private PaymentMethodLegendAdapter methodLegendAdapter;
    private MemberSummaryAdapter memberExpenseLegendAdapter;
    private MemberSummaryAdapter memberIncomeLegendAdapter;
    private TransactionAdapter topExpensesAdapter;
    private final Map<String, String> methodLabels = new HashMap<>();
    private final Map<String, String> categoryNames = new HashMap<>();
    private final Map<String, String> categoryColors = new HashMap<>();
    private final Map<String, String> accountNames = new HashMap<>();
    private final Map<String, String> memberNames = new HashMap<>();

    private List<MonthlySummary> monthlyDataList = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentStatisticsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Explicitly hide content and show loader before ANYTHING else
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.scrollView.setVisibility(View.GONE);
        binding.llEmptyState.setVisibility(View.GONE);

        FinanzAppApplication.AppContainer appContainer = ((FinanzAppApplication) requireActivity().getApplication()).getAppContainer();
        ViewModelFactory factory = new ViewModelFactory(appContainer);
        viewModel = new ViewModelProvider(this, factory).get(StatisticsViewModel.class);

        setupRecyclerView();
        setupCharts();
        setupClickListeners();
        setupObservers();

        viewModel.init();
    }

    private void setupRecyclerView() {
        legendAdapter = new LegendAdapter();
        binding.rvCategoryLegend.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvCategoryLegend.setAdapter(legendAdapter);

        methodLegendAdapter = new PaymentMethodLegendAdapter();
        binding.rvPaymentMethodLegend.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvPaymentMethodLegend.setAdapter(methodLegendAdapter);

        memberExpenseLegendAdapter = new MemberSummaryAdapter("expense");
        binding.rvMemberExpenses.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvMemberExpenses.setAdapter(memberExpenseLegendAdapter);

        memberIncomeLegendAdapter = new MemberSummaryAdapter("income");
        binding.rvMemberIncome.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvMemberIncome.setAdapter(memberIncomeLegendAdapter);

        methodLabels.put("tarjeta", getString(R.string.method_card));
        methodLabels.put("efectivo", getString(R.string.method_cash));
        methodLabels.put("transferencia", getString(R.string.method_transfer));
        methodLabels.put("bizum", getString(R.string.method_bizum));
        methodLabels.put("tarjeta_restaurante", getString(R.string.method_restaurant_card));
        methodLabels.put("tarjeta_transporte", getString(R.string.method_transport_card));
        methodLabels.put("domiciliacion_bancaria", getString(R.string.method_direct_debit));

        topExpensesAdapter = new TransactionAdapter(new ArrayList<>(), categoryNames, categoryColors, accountNames, memberNames, methodLabels, new TransactionAdapter.OnTransactionClickListener() {
            @Override
            public void onTransactionClick(Transaction t) {
                Bundle args = new Bundle();
                com.finanzapp.app.util.Result<com.finanzapp.app.data.model.User> userResult = viewModel.getUserData().getValue();
                String familyId = null;
                if (userResult instanceof com.finanzapp.app.util.Result.Success) {
                    familyId = ((com.finanzapp.app.util.Result.Success<com.finanzapp.app.data.model.User>) userResult).getData().getFamilyId();
                }
                args.putString("familyId", familyId);
                args.putSerializable("transaction", t);
                Navigation.findNavController(requireView()).navigate(R.id.action_statisticsFragment_to_addEditTransactionFragment, args);
            }

            @Override
            public void onTransactionLongClick(Transaction t) {}
        });
        binding.rvTopExpenses.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvTopExpenses.setAdapter(topExpensesAdapter);
    }

    private void setupCharts() {
        ChartUtils.setupBasicChart(binding.monthlyChart);
        ChartUtils.setupAxes(binding.monthlyChart);
        binding.monthlyChart.setDrawOrder(new CombinedChart.DrawOrder[]{
                CombinedChart.DrawOrder.BAR, CombinedChart.DrawOrder.LINE
        });
        binding.monthlyChart.getAxisLeft().setDrawLabels(true);
        binding.monthlyChart.getAxisLeft().setTextSize(10f);
        binding.monthlyChart.setExtraOffsets(10f, 20f, 10f, 10f); // Increase top offset for bar values

        ChartUtils.setupPieChart(binding.categoryPieChart);
        binding.categoryPieChart.setUsePercentValues(false); // Show currency values
        ChartUtils.setupPieChart(binding.paymentMethodPieChart);
        binding.paymentMethodPieChart.setUsePercentValues(true);
        
        binding.categoryPieChart.setOnChartValueSelectedListener(this);
        binding.paymentMethodPieChart.setOnChartValueSelectedListener(this);
        binding.monthlyChart.setOnChartValueSelectedListener(this);
    }

    private void setupClickListeners() {
        binding.cvDateRange.setOnClickListener(v -> showDateRangePicker());
        binding.btnClearDateRange.setOnClickListener(v -> viewModel.setDateRange(null, null));
    }

    private void showDateRangePicker() {
        MaterialDatePicker<Pair<Long, Long>> picker = MaterialDatePicker.Builder.dateRangePicker()
                .setTitleText(R.string.date_filter_select_period)
                .setSelection(viewModel.getDateRange().getValue())
                .build();

        picker.addOnPositiveButtonClickListener(selection -> {
            if (selection != null) {
                viewModel.setDateRange(selection.first, selection.second);
            }
        });

        picker.show(getChildFragmentManager(), "DATE_PICKER");
    }

    private void setupObservers() {
        viewModel.getDataLoaded().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof com.finanzapp.app.util.Result.Success) {
                binding.progressBar.setVisibility(View.GONE);
                binding.scrollView.setVisibility(View.VISIBLE);
            } else if (result instanceof com.finanzapp.app.util.Result.Loading) {
                binding.progressBar.setVisibility(View.VISIBLE);
                binding.scrollView.setVisibility(View.GONE);
                binding.llEmptyState.setVisibility(View.GONE);
            }
        });

        viewModel.getFamilyData().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof com.finanzapp.app.util.Result.Success) {
                com.finanzapp.app.data.model.Family family = ((com.finanzapp.app.util.Result.Success<com.finanzapp.app.data.model.Family>) result).getData();
                currentCurrencyCode = family.getCurrencyCode();
            }
        });

        viewModel.getDateRange().observe(getViewLifecycleOwner(), range -> {
            if (range == null) {
                binding.tvDateRange.setText(R.string.date_filter_all);
                binding.btnClearDateRange.setVisibility(View.GONE);
            } else {
                SimpleDateFormat sdf = new SimpleDateFormat("dd MMM", new Locale("es", "ES"));
                String text = sdf.format(new Date(range.first)) + " - " + sdf.format(new Date(range.second));
                binding.tvDateRange.setText(text);
                binding.btnClearDateRange.setVisibility(View.VISIBLE);
            }
        });

        viewModel.getCurrentMonthIncome().observe(getViewLifecycleOwner(), income -> 
                binding.tvTotalIncome.setText(formatCurrency(income, currentCurrencyCode, 2)));

        viewModel.getIncomeVariationPercentage().observe(getViewLifecycleOwner(), variation -> 
            updateVariationIndicator(binding.tvIncomeVariation, binding.ivIncomeVariationIcon, variation, true));
        
        viewModel.getCurrentMonthExpense().observe(getViewLifecycleOwner(), expense -> 
                binding.tvTotalExpense.setText(formatCurrency(expense, currentCurrencyCode, 2)));

        viewModel.getVariationPercentage().observe(getViewLifecycleOwner(), variation -> 
            updateVariationIndicator(binding.tvExpenseVariation, binding.ivExpenseVariationIcon, variation, false));

        viewModel.getMonthlyEvolution().observe(getViewLifecycleOwner(), evolution -> {
            if (evolution == null || evolution.isEmpty()) {
                binding.scrollView.setVisibility(View.GONE);
                binding.llEmptyState.setVisibility(View.VISIBLE);
            } else {
                binding.llEmptyState.setVisibility(View.GONE);
                binding.scrollView.setVisibility(View.VISIBLE);
                monthlyDataList = evolution;
                updateMonthlyChart(evolution);
            }
        });
        
        viewModel.getCategoryDistribution().observe(getViewLifecycleOwner(), this::updatePieChart);

        viewModel.getSavingsRate().observe(getViewLifecycleOwner(), this::updateSavingsRate);
        viewModel.getPaymentMethodDistribution().observe(getViewLifecycleOwner(), this::updatePaymentMethodChart);
        viewModel.getTopExpenses().observe(getViewLifecycleOwner(), expenses -> {
            if (expenses != null) {
                topExpensesAdapter.updateTransactions(expenses);
            }
        });
        viewModel.getMemberExpenseDistribution().observe(getViewLifecycleOwner(), distribution -> {
            if (distribution != null) {
                memberNames.clear();
                for (MemberSummary s : distribution) {
                    memberNames.put(s.getUid(), s.getDisplayName());
                }
                updateMemberExpenseChart(distribution);
                
                // Refresh top expenses adapter to show correct names
                List<Transaction> current = viewModel.getTopExpenses().getValue();
                if (current != null) {
                    topExpensesAdapter.notifyDataSetChanged();
                }
            }
        });

        viewModel.getMemberIncomeDistribution().observe(getViewLifecycleOwner(), distribution -> {
            if (distribution != null) {
                for (MemberSummary s : distribution) {
                    memberNames.put(s.getUid(), s.getDisplayName());
                }
                updateMemberIncomeChart(distribution);
            }
        });

        viewModel.getAllCategories().observe(getViewLifecycleOwner(), categories -> {
            if (categories != null) {
                categoryNames.clear();
                categoryColors.clear();
                for (Category c : categories) {
                    categoryNames.put(c.getId(), c.getName());
                    categoryColors.put(c.getId(), c.getColor());
                }
                List<Transaction> current = viewModel.getTopExpenses().getValue();
                if (current != null) {
                    topExpensesAdapter.updateTransactions(current);
                }
            }
        });
    }

    private void updateSavingsRate(Double rate) {
        if (rate == null) {
            binding.tvSavingsRate.setText("---");
            binding.progressSavingsRate.setProgress(0);
            binding.progressSavingsRate.setVisibility(View.INVISIBLE);
            return;
        }

        binding.tvSavingsRate.setText(String.format(Locale.getDefault(), "%.1f%%", rate));
        binding.progressSavingsRate.setVisibility(View.VISIBLE);
        
        int progress = (int) Math.max(0, Math.min(100, rate));
        binding.progressSavingsRate.setProgress(progress);

        int color;
        if (rate >= 20) {
            color = ContextCompat.getColor(requireContext(), R.color.success);
        } else if (rate >= 0) {
            color = ContextCompat.getColor(requireContext(), R.color.warning);
        } else {
            color = ContextCompat.getColor(requireContext(), R.color.error);
            binding.progressSavingsRate.setProgress(100); // Fill the bar for negative
        }
        binding.tvSavingsRate.setTextColor(color);
        binding.progressSavingsRate.setIndicatorColor(color);
    }

    private void updateVariationIndicator(TextView tvVariation, ImageView ivIcon, Double variation, boolean isIncome) {
        if (variation == null || variation == 0) {
            tvVariation.setVisibility(View.GONE);
            ivIcon.setVisibility(View.GONE);
            return;
        }

        tvVariation.setVisibility(View.VISIBLE);
        ivIcon.setVisibility(View.VISIBLE);

        String text = String.format(Locale.getDefault(), "%s%.1f%%", variation > 0 ? "+" : "", variation);
        tvVariation.setText(text);

        int successColor = ContextCompat.getColor(requireContext(), R.color.success);
        int errorColor = ContextCompat.getColor(requireContext(), R.color.error);

        if (isIncome) {
            if (variation > 0) {
                tvVariation.setTextColor(successColor);
                ivIcon.setImageResource(R.drawable.ic_expense);
                ivIcon.setColorFilter(successColor);
            } else {
                tvVariation.setTextColor(errorColor);
                ivIcon.setImageResource(R.drawable.ic_income);
                ivIcon.setColorFilter(errorColor);
            }
        } else {
            if (variation > 0) {
                tvVariation.setTextColor(errorColor);
                ivIcon.setImageResource(R.drawable.ic_expense);
                ivIcon.setColorFilter(errorColor);
            } else {
                tvVariation.setTextColor(successColor);
                ivIcon.setImageResource(R.drawable.ic_income);
                ivIcon.setColorFilter(successColor);
            }
        }
    }

    private void updateMonthlyChart(List<MonthlySummary> data) {
        if (data.isEmpty()) return;

        List<BarEntry> incomeEntries = new ArrayList<>();
        List<BarEntry> expenseEntries = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        for (int i = 0; i < data.size(); i++) {
            MonthlySummary summary = data.get(i);
            incomeEntries.add(new BarEntry(i, (float) summary.getIncome()));
            expenseEntries.add(new BarEntry(i, (float) summary.getExpense()));
            labels.add(summary.getMonthLabel());
        }

        int textColor = isDarkMode() ? Color.WHITE : Color.BLACK;

        BarDataSet incomeSet = new BarDataSet(incomeEntries, "Ingresos");
        incomeSet.setColor(ContextCompat.getColor(requireContext(), R.color.success));
        incomeSet.setValueTextColor(textColor);
        incomeSet.setValueTextSize(10f);
        incomeSet.setDrawValues(true);
        
        BarDataSet expenseSet = new BarDataSet(expenseEntries, "Gastos");
        expenseSet.setColor(ContextCompat.getColor(requireContext(), R.color.error));
        expenseSet.setValueTextColor(textColor);
        expenseSet.setValueTextSize(10f);
        expenseSet.setDrawValues(true);

        CombinedData combinedData = getCombinedData(data, incomeSet, expenseSet);
        binding.monthlyChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        binding.monthlyChart.setData(combinedData);
        binding.monthlyChart.invalidate();
    }

    private CombinedData getCombinedData(List<MonthlySummary> data, BarDataSet incomeSet, BarDataSet expenseSet) {
        BarData barData = new BarData(incomeSet, expenseSet);
        float groupSpace = 0.2f;
        float barSpace = 0.05f;
        float barWidth = 0.35f;

        barData.setBarWidth(barWidth);
        barData.groupBars(0f, groupSpace, barSpace);

        CombinedData combinedData = new CombinedData();
        combinedData.setData(barData);

        binding.monthlyChart.getXAxis().setAxisMinimum(0f);
        binding.monthlyChart.getXAxis().setAxisMaximum(data.size());
        binding.monthlyChart.getXAxis().setCenterAxisLabels(true);

        return combinedData;
    }

    private void updatePieChart(List<DashboardCategorySummary> data) {
        if (data.isEmpty()) {
            binding.categoryPieChart.clear();
            return;
        }

        List<PieEntry> entries = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();

        double totalAmount = 0;
        for (DashboardCategorySummary summary : data) {
            totalAmount += summary.getAmount();
        }

        final double MIN_PERCENTAGE = 3.0;
        final int MAX_SLICES = 8;
        
        double othersAmount = 0;
        List<String> othersCategoryIds = new ArrayList<>();
        List<DashboardCategorySummary> visibleCategories = new ArrayList<>();

        for (DashboardCategorySummary summary : data) {
            double percentage = (totalAmount > 0) ? (summary.getAmount() / totalAmount) * 100.0 : 0;
            
            if (percentage < MIN_PERCENTAGE) {
                othersAmount += summary.getAmount();
                othersCategoryIds.add(summary.getCategoryId());
            } else {
                visibleCategories.add(summary);
            }
        }

        while (visibleCategories.size() > (othersAmount > 0 ? MAX_SLICES - 1 : MAX_SLICES)) {
            DashboardCategorySummary smallest = visibleCategories.remove(visibleCategories.size() - 1);
            othersAmount += smallest.getAmount();
            othersCategoryIds.add(smallest.getCategoryId());
        }

        for (DashboardCategorySummary summary : visibleCategories) {
            String label = summary.getCategoryName();
            PieEntry entry = new PieEntry((float) summary.getAmount(), label);
            entry.setData(summary.getCategoryId());
            entries.add(entry);
            try {
                colors.add(Color.parseColor(summary.getCategoryColor()));
            } catch (Exception e) {
                colors.add(Color.GRAY);
            }
        }

        if (othersAmount > 0) {
            String label = getString(R.string.category_others);
            PieEntry entry = new PieEntry((float) othersAmount, label);
            entry.setData("GROUPED_OTHERS:" + String.join(",", othersCategoryIds)); 
            entries.add(entry);
            colors.add(isDarkMode() ? Color.DKGRAY : Color.LTGRAY);
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(colors);
        dataSet.setSliceSpace(3f);
        dataSet.setSelectionShift(5f);
        
        dataSet.setXValuePosition(PieDataSet.ValuePosition.INSIDE_SLICE);
        dataSet.setYValuePosition(PieDataSet.ValuePosition.INSIDE_SLICE);
        dataSet.setValueLineColor(isDarkMode() ? Color.WHITE : Color.BLACK);

        PieData pieData = getPieData(dataSet);

        binding.categoryPieChart.setData(pieData);
        binding.categoryPieChart.setMinAngleForSlices(0f);
        binding.categoryPieChart.invalidate();

        legendAdapter.updateData(data);
    }

    private void updatePaymentMethodChart(List<PaymentMethodSummary> data) {
        if (data == null || data.isEmpty()) {
            binding.paymentMethodPieChart.clear();
            return;
        }

        List<PieEntry> entries = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();

        for (int i = 0; i < data.size(); i++) {
            PaymentMethodSummary summary = data.get(i);
            String label = methodLabels.getOrDefault(summary.getMethodId(), summary.getMethodId());
            PieEntry entry = new PieEntry((float) summary.getAmount(), label);
            entry.setData("METHOD:" + summary.getMethodId());
            entries.add(entry);
            colors.add(getPaymentMethodColor(summary.getMethodId()));
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(colors);
        dataSet.setSliceSpace(3f);
        dataSet.setSelectionShift(5f);

        PieData pieData = new PieData(dataSet);
        pieData.setValueFormatter(new PercentFormatter(binding.paymentMethodPieChart));
        pieData.setValueTextSize(10f);
        pieData.setValueTextColor(isDarkMode() ? Color.WHITE : Color.BLACK);

        binding.paymentMethodPieChart.setData(pieData);
        binding.paymentMethodPieChart.invalidate();

        methodLegendAdapter.updateData(data);
    }

    private int getPaymentMethodColor(String methodId) {
        switch (methodId) {
            case "bizum": return Color.parseColor("#00CCFF");
            case "tarjeta": return Color.parseColor("#3F51B5");
            case "efectivo": return Color.parseColor("#4CAF50");
            case "transferencia": return Color.parseColor("#FF9800");
            case "tarjeta_restaurante": return Color.parseColor("#E91E63");
            case "tarjeta_transporte": return Color.parseColor("#9C27B0");
            case "domiciliacion_bancaria": return Color.parseColor("#607D8B");
            default: return Color.GRAY;
        }
    }

    private void updateMemberExpenseChart(List<MemberSummary> data) {
        if (data == null || data.isEmpty()) {
            binding.rvMemberExpenses.setVisibility(View.GONE);
            return;
        }

        binding.rvMemberExpenses.setVisibility(View.VISIBLE);
        memberExpenseLegendAdapter.updateData(data);
    }

    private void updateMemberIncomeChart(List<MemberSummary> data) {
        if (data == null || data.isEmpty()) {
            binding.rvMemberIncome.setVisibility(View.GONE);
            return;
        }

        binding.rvMemberIncome.setVisibility(View.VISIBLE);
        memberIncomeLegendAdapter.updateData(data);
    }

    @NonNull
    private PieData getPieData(PieDataSet dataSet) {
        PieData pieData = new PieData(dataSet);
        pieData.setValueFormatter(new com.github.mikephil.charting.formatter.ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return formatCurrency(value, currentCurrencyCode, 0);
            }
        });
        pieData.setValueTextColor(Color.WHITE);
        pieData.setValueTextSize(11f);
        return pieData;
    }

    @Override
    public void onValueSelected(Entry e, Highlight h) {
        if (e.getData() instanceof String) {
            String data = (String) e.getData();
            if (data.startsWith("GROUPED_OTHERS:")) return;
            
            if (data.startsWith("METHOD:")) {
                String method = data.substring(7);
                navigateToTransactions(null, method, null, null, null, null);
            } else {
                // Category ID
                navigateToTransactions(data, null, null, null, null, null);
            }
        } else if (e instanceof BarEntry) {
            // Index 0 is Income (success color), Index 1 is Expense (error color) in combined bar logic
            String type = null;
            if (h.getDataSetIndex() == 0) type = "expense";
            else if (h.getDataSetIndex() == 1) type = "income";

            if (type != null) {
                int index = (int) e.getX();
                if (index >= 0 && index < monthlyDataList.size()) {
                    MonthlySummary period = monthlyDataList.get(index);
                    navigateToTransactions(null, null, type, null, period.getStartDateMillis(), period.getEndDateMillis());
                } else {
                    navigateToTransactions(null, null, type, null, null, null);
                }
            }
        }
    }

    private void navigateToTransactions(String categoryId, String paymentMethod, String type, String memberUid, Long startMillis, Long endMillis) {
        Bundle args = new Bundle();
        if (categoryId != null) args.putString("preselectedCategoryId", categoryId);
        if (paymentMethod != null) args.putString("preselectedMethod", paymentMethod);
        if (type != null) args.putString("preselectedType", type);
        if (memberUid != null) args.putString("preselectedMemberUid", memberUid);
        
        if (startMillis != null && endMillis != null) {
            args.putLong("preselectedStartDateMillis", startMillis);
            args.putLong("preselectedEndDateMillis", endMillis);
        } else {
            Pair<Long, Long> range = viewModel.getDateRange().getValue();
            if (range != null) {
                args.putLong("preselectedStartDateMillis", range.first);
                args.putLong("preselectedEndDateMillis", range.second);
            }
        }
        
        Navigation.findNavController(requireView()).navigate(R.id.action_statisticsFragment_to_transactionListFragment, args);
    }

    @Override
    public void onNothingSelected() {}

    private boolean isDarkMode() {
        if (getContext() == null) return false;
        int nightModeFlags = getContext().getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private String formatCurrency(double amount, String currencyCode, int decimals) {
        Locale locale;
        switch (currencyCode) {
            case "USD": locale = Locale.US; break;
            case "GBP": locale = Locale.UK; break;
            default: locale = new Locale("es", "ES"); break;
        }
        NumberFormat format = NumberFormat.getCurrencyInstance(locale);
        format.setMinimumFractionDigits(decimals);
        format.setMaximumFractionDigits(decimals);
        try {
            format.setCurrency(Currency.getInstance(currencyCode));
        } catch (Exception ignored) {}
        return format.format(amount);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private class LegendAdapter extends RecyclerView.Adapter<LegendAdapter.ViewHolder> {
        private List<DashboardCategorySummary> items = new ArrayList<>();

        public void updateData(List<DashboardCategorySummary> newItems) {
            this.items = new ArrayList<>(newItems);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_statistics_legend, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            DashboardCategorySummary item = items.get(position);
            holder.tvName.setText(item.getCategoryName());
            holder.tvAmount.setText(formatCurrency(item.getAmount(), currentCurrencyCode, 2));
            holder.tvPercentage.setText(String.format(Locale.getDefault(), "%.1f%%", item.getPercentage()));
            holder.progressBar.setProgress((int) item.getPercentage());

            try {
                int color = Color.parseColor(item.getCategoryColor());
                holder.vColor.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
                holder.progressBar.setIndicatorColor(color);
            } catch (Exception e) {
                holder.vColor.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.GRAY));
                holder.progressBar.setIndicatorColor(Color.GRAY);
            }

            holder.itemView.setOnClickListener(v -> navigateToTransactions(item.getCategoryId(), null, null, null, null, null));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final View vColor;
            final TextView tvName;
            final TextView tvPercentage;
            final TextView tvAmount;
            final com.google.android.material.progressindicator.LinearProgressIndicator progressBar;

            ViewHolder(View itemView) {
                super(itemView);
                vColor = itemView.findViewById(R.id.v_category_color);
                tvName = itemView.findViewById(R.id.tv_category_name);
                tvPercentage = itemView.findViewById(R.id.tv_category_percentage);
                tvAmount = itemView.findViewById(R.id.tv_category_amount);
                progressBar = itemView.findViewById(R.id.progress_category);
            }
        }
    }

    private class PaymentMethodLegendAdapter extends RecyclerView.Adapter<PaymentMethodLegendAdapter.ViewHolder> {
        private List<PaymentMethodSummary> items = new ArrayList<>();

        public void updateData(List<PaymentMethodSummary> newItems) {
            this.items = new ArrayList<>(newItems);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_statistics_legend, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            PaymentMethodSummary item = items.get(position);
            String label = methodLabels.getOrDefault(item.getMethodId(), item.getMethodId());
            holder.tvName.setText(label);
            holder.tvAmount.setText(formatCurrency(item.getAmount(), currentCurrencyCode, 2));
            holder.tvPercentage.setText(String.format(Locale.getDefault(), "%.1f%%", item.getPercentage()));
            holder.progressBar.setProgress((int) item.getPercentage());

            int color = getPaymentMethodColor(item.getMethodId());
            holder.vColor.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
            holder.progressBar.setIndicatorColor(color);

            holder.itemView.setOnClickListener(v -> navigateToTransactions(null, item.getMethodId(), null, null, null, null));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final View vColor;
            final TextView tvName;
            final TextView tvPercentage;
            final TextView tvAmount;
            final com.google.android.material.progressindicator.LinearProgressIndicator progressBar;

            ViewHolder(View itemView) {
                super(itemView);
                vColor = itemView.findViewById(R.id.v_category_color);
                tvName = itemView.findViewById(R.id.tv_category_name);
                tvPercentage = itemView.findViewById(R.id.tv_category_percentage);
                tvAmount = itemView.findViewById(R.id.tv_category_amount);
                progressBar = itemView.findViewById(R.id.progress_category);
            }
        }
    }

    private class MemberSummaryAdapter extends RecyclerView.Adapter<MemberSummaryAdapter.ViewHolder> {
        private List<MemberSummary> items = new ArrayList<>();
        private final String transactionType;

        public MemberSummaryAdapter(String transactionType) {
            this.transactionType = transactionType;
        }

        public void updateData(List<MemberSummary> newItems) {
            this.items = new ArrayList<>(newItems);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_statistics_legend, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            MemberSummary item = items.get(position);
            holder.tvName.setText(item.getDisplayName());
            holder.tvAmount.setText(formatCurrency(item.getAmount(), currentCurrencyCode, 2));
            holder.tvPercentage.setText(String.format(Locale.getDefault(), "%.1f%%", item.getPercentage()));
            holder.progressBar.setProgress((int) item.getPercentage());

            int[] colorPalette = ColorTemplate.MATERIAL_COLORS;
            int color = colorPalette[position % colorPalette.length];
            holder.vColor.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
            holder.progressBar.setIndicatorColor(color);

            holder.itemView.setOnClickListener(v -> navigateToTransactions(null, null, transactionType, item.getUid(), null, null));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final View vColor;
            final TextView tvName;
            final TextView tvPercentage;
            final TextView tvAmount;
            final com.google.android.material.progressindicator.LinearProgressIndicator progressBar;

            ViewHolder(View itemView) {
                super(itemView);
                vColor = itemView.findViewById(R.id.v_category_color);
                tvName = itemView.findViewById(R.id.tv_category_name);
                tvPercentage = itemView.findViewById(R.id.tv_category_percentage);
                tvAmount = itemView.findViewById(R.id.tv_category_amount);
                progressBar = itemView.findViewById(R.id.progress_category);
            }
        }
    }
}
