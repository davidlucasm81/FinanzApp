package com.finanzapp.app.ui.dashboard;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.finanzapp.app.data.model.DashboardCategorySummary;
import com.finanzapp.app.databinding.ItemDashboardCategoryBinding;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

public class DashboardCategoryAdapter extends RecyclerView.Adapter<DashboardCategoryAdapter.ViewHolder> {
    private final List<DashboardCategorySummary> items = new ArrayList<>();
    private String currencyCode = "EUR";

    public void setItems(List<DashboardCategorySummary> newItems, String currencyCode) {
        this.items.clear();
        this.items.addAll(newItems);
        this.currencyCode = currencyCode;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemDashboardCategoryBinding binding = ItemDashboardCategoryBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position), currencyCode);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemDashboardCategoryBinding binding;

        ViewHolder(ItemDashboardCategoryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(DashboardCategorySummary summary, String currencyCode) {
            binding.tvCategoryName.setText(summary.getCategoryName());
            binding.tvCategoryAmount.setText(formatCurrency(summary.getAmount(), currencyCode));
            
            int color;
            try {
                color = Color.parseColor(summary.getCategoryColor());
            } catch (Exception e) {
                color = Color.GRAY;
            }
            
            binding.vCategoryColor.setBackgroundTintList(ColorStateList.valueOf(color));
            binding.progressCategory.setIndicatorColor(color);
            binding.progressCategory.setProgress((int) Math.round(summary.getPercentage()));
        }

        private String formatCurrency(double amount, String currencyCode) {
            Locale locale;
            switch (currencyCode) {
                case "USD": locale = Locale.US; break;
                case "GBP": locale = Locale.UK; break;
                default: locale = new Locale("es", "ES"); break;
            }
            NumberFormat format = NumberFormat.getCurrencyInstance(locale);
            try {
                format.setCurrency(Currency.getInstance(currencyCode));
            } catch (Exception ignored) {}
            return format.format(amount);
        }
    }
}
