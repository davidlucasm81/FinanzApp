package com.finanzapp.app.ui.statistics;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.finanzapp.app.FinanzAppApplication;
import com.finanzapp.app.R;
import com.finanzapp.app.data.model.Category;
import com.finanzapp.app.data.model.DashboardCategorySummary;
import com.finanzapp.app.data.model.statistics.MonthlySummary;
import com.finanzapp.app.databinding.FragmentStatisticsBinding;
import com.finanzapp.app.util.ChartUtils;
import com.finanzapp.app.viewmodel.StatisticsViewModel;
import com.finanzapp.app.viewmodel.ViewModelFactory;
import com.github.mikephil.charting.charts.CombinedChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.CombinedData;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;

import androidx.navigation.Navigation;

public class StatisticsFragment extends Fragment implements OnChartValueSelectedListener {

    private FragmentStatisticsBinding binding;
    private StatisticsViewModel viewModel;
    private String currentCurrencyCode = "EUR";
    private LegendAdapter legendAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentStatisticsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FinanzAppApplication.AppContainer appContainer = ((FinanzAppApplication) requireActivity().getApplication()).getAppContainer();
        ViewModelFactory factory = new ViewModelFactory(appContainer.getAuthRepository(), appContainer.getFamilyRepository());
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
        binding.categoryPieChart.setUsePercentValues(true);
        binding.categoryPieChart.setDrawHoleEnabled(false);
        binding.categoryPieChart.getLegend().setEnabled(false);
        binding.categoryPieChart.setDrawEntryLabels(false);
        binding.categoryPieChart.setOnChartValueSelectedListener(this);
    }

    private void setupClickListeners() {
        binding.cvDateRange.setOnClickListener(v -> showDateRangePicker());
        binding.btnClearDateRange.setOnClickListener(v -> viewModel.setDateRange(null, null));
    }

    private void showDateRangePicker() {
        MaterialDatePicker<androidx.core.util.Pair<Long, Long>> picker = MaterialDatePicker.Builder.dateRangePicker()
                .setTitleText(R.string.date_filter_select_period)
                .build();

        picker.addOnPositiveButtonClickListener(selection -> {
            if (selection.first != null && selection.second != null) {
                // Ensure the end date is the end of the day with millisecond precision
                LocalDate endDate = Instant.ofEpochMilli(selection.second)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate();
                long endOfDay = endDate.atTime(LocalTime.MAX)
                        .atZone(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli();
                viewModel.setDateRange(selection.first, endOfDay);
            }
        });

        picker.show(getChildFragmentManager(), "date_range_picker");
    }

    private void setupObservers() {
        viewModel.getDataLoaded().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof com.finanzapp.app.util.Result.Success) {
                binding.progressBar.setVisibility(View.GONE);
                binding.scrollView.setVisibility(View.VISIBLE);
            } else if (result instanceof com.finanzapp.app.util.Result.Loading) {
                binding.progressBar.setVisibility(View.VISIBLE);
                binding.scrollView.setVisibility(View.GONE);
            }
        });

        viewModel.getFamilyData().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof com.finanzapp.app.util.Result.Success) {
                currentCurrencyCode = ((com.finanzapp.app.util.Result.Success<com.finanzapp.app.data.model.Family>) result).getData().getCurrencyCode();
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
                updateMonthlyChart(evolution);
            }
        });
        
        viewModel.getCategoryDistribution().observe(getViewLifecycleOwner(), this::updatePieChart);
        // El ranking y la matriz se han eliminado por decisión de UX
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
            // Income: positive variation is good (green up), negative is bad (red down)
            if (variation > 0) {
                tvVariation.setTextColor(successColor);
                ivIcon.setImageResource(R.drawable.ic_expense); // UP arrow
                ivIcon.setColorFilter(successColor);
            } else {
                tvVariation.setTextColor(errorColor);
                ivIcon.setImageResource(R.drawable.ic_income); // DOWN arrow
                ivIcon.setColorFilter(errorColor);
            }
        } else {
            // Expense/Variation: positive variation is bad (red up), negative is good (green down)
            if (variation > 0) {
                tvVariation.setTextColor(errorColor);
                ivIcon.setImageResource(R.drawable.ic_expense); // UP arrow
                ivIcon.setColorFilter(errorColor);
            } else {
                tvVariation.setTextColor(successColor);
                ivIcon.setImageResource(R.drawable.ic_income); // DOWN arrow
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

        BarData barData = new BarData(incomeSet, expenseSet);
        barData.setValueFormatter(new com.github.mikephil.charting.formatter.ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                if (value == 0) return "";
                return formatCurrency(value, currentCurrencyCode, 0);
            }
        });

        float groupSpace = 0.08f;
        float barSpace = 0.03f;
        float barWidth = 0.43f;

        barData.setBarWidth(barWidth);
        if (data.size() > 1) {
            barData.groupBars(0, groupSpace, barSpace);
        }

        CombinedData combinedData = new CombinedData();
        combinedData.setData(barData);

        binding.monthlyChart.setData(combinedData);
        XAxis xAxis = binding.monthlyChart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setCenterAxisLabels(true);
        xAxis.setAxisMinimum(0f);
        xAxis.setAxisMaximum(combinedData.getXMax() + 1.1f);
        xAxis.setLabelCount(data.size());
        xAxis.setTextSize(12f);
        
        binding.monthlyChart.getAxisLeft().setAxisMinimum(0f);
        binding.monthlyChart.getAxisLeft().setTextSize(12f);
        binding.monthlyChart.getAxisLeft().setSpaceTop(25f); // More space for labels
        binding.monthlyChart.getLegend().setTextSize(14f);

        // Zoom and Scroll configuration: Show 3 months
        binding.monthlyChart.setVisibleXRangeMaximum(3f); 
        binding.monthlyChart.moveViewToX(data.size() - 3f);

        binding.monthlyChart.invalidate();
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

        for (DashboardCategorySummary summary : data) {
            double percentage = (totalAmount > 0) ? (summary.getAmount() / totalAmount) * 100.0 : 0;
            String label = String.format(Locale.getDefault(), "%s (%.1f%%)", 
                    summary.getCategoryName(), percentage);
            PieEntry entry = new PieEntry((float) summary.getAmount(), label);
            entry.setData(summary.getCategoryId());
            entries.add(entry);
            try {
                colors.add(Color.parseColor(summary.getCategoryColor()));
            } catch (Exception e) {
                colors.add(Color.GRAY);
            }
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(colors);
        dataSet.setSliceSpace(3f);
        dataSet.setSelectionShift(5f);
        
        // Label configuration: only percentages inside
        dataSet.setXValuePosition(PieDataSet.ValuePosition.INSIDE_SLICE);
        dataSet.setYValuePosition(PieDataSet.ValuePosition.INSIDE_SLICE);
        dataSet.setValueLineColor(isDarkMode() ? Color.WHITE : Color.BLACK);

        PieData pieData = new PieData(dataSet);
        pieData.setValueFormatter(new com.github.mikephil.charting.formatter.ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                // Show label only for values >= 2% to avoid overlap in small slices
                if (value < 2.0f) return "";
                return String.format(Locale.getDefault(), "%.1f%%", value);
            }
        });
        pieData.setValueTextSize(10f);
        pieData.setValueTextColor(isDarkMode() ? Color.WHITE : Color.BLACK);

        binding.categoryPieChart.setData(pieData);
        binding.categoryPieChart.setMinAngleForSlices(12f);
        binding.categoryPieChart.invalidate();

        // Update custom legend
        legendAdapter.updateData(data);
    }

    @Override
    public void onValueSelected(Entry e, Highlight h) {
        if (e == null || e.getData() == null) return;
        navigateToTransactions((String) e.getData());
        
        // Deselect the slice after navigation (if the user returns)
        binding.categoryPieChart.highlightValues(null);
    }

    private void navigateToTransactions(String categoryId) {
        Pair<Long, Long> range = viewModel.getDateRange().getValue();
        
        Bundle args = new Bundle();
        args.putString("preselectedCategoryId", categoryId);
        
        if (range != null) {
            args.putLong("preselectedStartDateMillis", range.first);
            args.putLong("preselectedEndDateMillis", range.second);
        } else {
            args.putLong("preselectedStartDateMillis", -1L);
            args.putLong("preselectedEndDateMillis", -1L);
        }
        
        Navigation.findNavController(requireView()).navigate(R.id.action_statisticsFragment_to_transactionListFragment, args);
    }

    @Override
    public void onNothingSelected() {
        // No action needed
    }

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
            this.items = newItems;
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

            holder.itemView.setOnClickListener(v -> navigateToTransactions(item.getCategoryId()));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            View vColor;
            TextView tvName, tvPercentage, tvAmount;
            com.google.android.material.progressindicator.LinearProgressIndicator progressBar;

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
