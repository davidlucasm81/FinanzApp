package com.finanzapp.app.ui.dashboard;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
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

    public void setItems(List<Account> newItems, String currencyCode) {
        this.items.clear();
        // El Dashboard solo debe mostrar cuentas activas: una cuenta archivada
        // (active == false) sigue existiendo para conservar el histórico de
        // movimientos, pero no debe listarse aquí ni sumar en el desglose.
        for (Account account : newItems) {
            if (account.isActive()) {
                this.items.add(account);
            }
        }
        this.currencyCode = currencyCode;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemDashboardAccountBinding binding = ItemDashboardAccountBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
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
        private final ItemDashboardAccountBinding binding;

        ViewHolder(ItemDashboardAccountBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Account account, String currencyCode) {
            binding.tvAccountName.setText(account.getName());
            binding.tvAccountBalance.setText(formatCurrency(account.getCurrentBalance(), currencyCode));
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