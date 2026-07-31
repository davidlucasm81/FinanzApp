package com.finanzapp.app.ui.dashboard;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.finanzapp.app.data.model.Account;
import com.finanzapp.app.databinding.ItemDashboardAccountBinding;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

public class DashboardAccountAdapter extends RecyclerView.Adapter<DashboardAccountAdapter.ViewHolder> {
    private final List<Account> items = new ArrayList<>();
    private String currencyCode = "EUR";
    private boolean isPrivacyModeEnabled = false;

    public void setItems(List<Account> newItems, String currencyCode) {
        List<Account> activeItems = new ArrayList<>();
        for (Account account : newItems) {
            if (account.isActive()) {
                activeItems.add(account);
            }
        }

        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return items.size();
            }

            @Override
            public int getNewListSize() {
                return activeItems.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return java.util.Objects.equals(items.get(oldItemPosition).getId(),
                        activeItems.get(newItemPosition).getId());
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                Account oldA = items.get(oldItemPosition);
                Account newA = activeItems.get(newItemPosition);
                return java.util.Objects.equals(oldA.getName(), newA.getName()) &&
                        Double.compare(oldA.getCurrentBalance(), newA.getCurrentBalance()) == 0;
            }
        });

        this.items.clear();
        this.items.addAll(activeItems);
        this.currencyCode = currencyCode;
        result.dispatchUpdatesTo(this);
    }

    public void setPrivacyModeEnabled(boolean enabled) {
        if (this.isPrivacyModeEnabled != enabled) {
            this.isPrivacyModeEnabled = enabled;
            notifyDataSetChanged();
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemDashboardAccountBinding binding = ItemDashboardAccountBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position), currencyCode, isPrivacyModeEnabled);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemDashboardAccountBinding binding;

        ViewHolder(ItemDashboardAccountBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Account account, String currencyCode, boolean isPrivacyModeEnabled) {
            binding.tvAccountName.setText(account.getName());
            if (isPrivacyModeEnabled) {
                binding.tvAccountBalance.setText("****");
            } else {
                binding.tvAccountBalance.setText(formatCurrency(account.getCurrentBalance(), currencyCode));
            }
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