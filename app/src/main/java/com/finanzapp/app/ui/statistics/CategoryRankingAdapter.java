package com.finanzapp.app.ui.statistics;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.finanzapp.app.data.model.DashboardCategorySummary;
import com.finanzapp.app.databinding.ItemCategoryRankingBinding;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

public class CategoryRankingAdapter extends RecyclerView.Adapter<CategoryRankingAdapter.ViewHolder> {

    private List<DashboardCategorySummary> items = new ArrayList<>();
    private String currencyCode = "EUR";
    private double maxAmount = 0;

    public void setItems(List<DashboardCategorySummary> items, String currencyCode) {
        this.items = items;
        this.currencyCode = currencyCode;
        this.maxAmount = 0;
        for (DashboardCategorySummary item : items) {
            if (item.getAmount() > maxAmount) {
                maxAmount = item.getAmount();
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemCategoryRankingBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemCategoryRankingBinding binding;

        ViewHolder(ItemCategoryRankingBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(DashboardCategorySummary item) {
            binding.tvCategoryName.setText(item.getCategoryName());
            
            NumberFormat formatter = NumberFormat.getCurrencyInstance(Locale.getDefault());
            formatter.setCurrency(Currency.getInstance(currencyCode));
            binding.tvCategoryAmount.setText(formatter.format(item.getAmount()));

            int progress = maxAmount > 0 ? (int) ((item.getAmount() / maxAmount) * 100) : 0;
            binding.progressCategory.setProgress(progress);

            try {
                int color = Color.parseColor(item.getCategoryColor());
                binding.progressCategory.setIndicatorColor(color);
            } catch (Exception e) {
                // Fallback to primary if invalid color
            }
        }
    }
}
