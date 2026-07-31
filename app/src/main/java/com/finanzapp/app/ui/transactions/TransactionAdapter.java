package com.finanzapp.app.ui.transactions;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.finanzapp.app.R;
import com.finanzapp.app.data.model.Transaction;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.ViewHolder> {
    public interface OnTransactionClickListener {
        void onTransactionClick(Transaction t);
        void onTransactionLongClick(Transaction t);
    }

    private final List<Transaction> transactions;
    private final Map<String, String> categoryNames;
    private final Map<String, String> categoryColors;
    private final Map<String, String> accountNames;
    private final Map<String, String> memberNames;
    private final Map<String, String> paymentMethodLabels;
    private final OnTransactionClickListener listener;
    private boolean isPrivacyModeEnabled = false;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("es", "ES"));

    public TransactionAdapter(List<Transaction> transactions, Map<String, String> categoryNames,
                       Map<String, String> categoryColors, Map<String, String> accountNames,
                       Map<String, String> memberNames, Map<String, String> paymentMethodLabels,
                       OnTransactionClickListener listener) {
        this.transactions = transactions;
        this.categoryNames = categoryNames;
        this.categoryColors = categoryColors;
        this.accountNames = accountNames;
        this.memberNames = memberNames;
        this.paymentMethodLabels = paymentMethodLabels;
        this.listener = listener;
    }

    public void updateTransactions(List<Transaction> newTransactions) {
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return transactions.size();
            }

            @Override
            public int getNewListSize() {
                return newTransactions.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return Objects.equals(transactions.get(oldItemPosition).getId(),
                        newTransactions.get(newItemPosition).getId());
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                Transaction oldT = transactions.get(oldItemPosition);
                Transaction newT = newTransactions.get(newItemPosition);
                return Double.compare(oldT.getAmount(), newT.getAmount()) == 0 &&
                        Objects.equals(oldT.getDate(), newT.getDate()) &&
                        Objects.equals(oldT.getDescription(), newT.getDescription()) &&
                        Objects.equals(oldT.getCategoryId(), newT.getCategoryId()) &&
                        Objects.equals(oldT.getAccountId(), newT.getAccountId()) &&
                        Objects.equals(oldT.getType(), newT.getType()) &&
                        Objects.equals(oldT.getCreatedBy(), newT.getCreatedBy()) &&
                        Objects.equals(oldT.getPaymentMethod(), newT.getPaymentMethod());
            }
        });

        this.transactions.clear();
        this.transactions.addAll(newTransactions);
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
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transaction, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Transaction t = transactions.get(position);

        holder.tvDate.setText(t.getDate() != null ? dateFormat.format(t.getDate().toDate()) : "");
        holder.tvCategory.setText(categoryNames.getOrDefault(t.getCategoryId(), holder.itemView.getContext().getString(R.string.label_category)));
        holder.tvDescription.setText(t.getDescription());
        holder.tvAccount.setText(accountNames.getOrDefault(t.getAccountId(), holder.itemView.getContext().getString(R.string.label_account)));

        int defaultCategoryColor = resolveColorPrimary(holder.itemView.getContext());
        int categoryColor = parseColorSafe(categoryColors.get(t.getCategoryId()), defaultCategoryColor);
        holder.tvCategory.setTextColor(categoryColor);
        holder.vCategoryColor.setBackgroundTintList(ColorStateList.valueOf(categoryColor));

        String methodLabel = paymentMethodLabels.getOrDefault(t.getPaymentMethod(), t.getPaymentMethod());
        holder.tvPaymentMethod.setText(methodLabel != null ? methodLabel : "");

        String creatorName = memberNames.getOrDefault(t.getCreatedBy(), "Usuario");
        holder.tvCreator.setText(holder.itemView.getContext().getString(R.string.by_user, creatorName));

        boolean isIncome = "income".equals(t.getType());
        double amount = t.getAmount();
        String amountStr;
        if (isPrivacyModeEnabled) {
            amountStr = (isIncome ? "+" : "-") + "****";
        } else {
            amountStr = (isIncome ? "+" : "-") + currencyFormat.format(amount);
        }
        holder.tvAmount.setText(amountStr);
        int amountColorRes = isIncome ? R.color.success : R.color.error;
        holder.tvAmount.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), amountColorRes));

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

    private static int resolveColorPrimary(Context context) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.colorPrimary, typedValue, true);
        return typedValue.data;
    }

    private static int parseColorSafe(String colorStr, int fallback) {
        if (colorStr == null || colorStr.isEmpty()) return fallback;
        try {
            return Color.parseColor(colorStr);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvDate;
        final TextView tvCategory;
        final TextView tvDescription;
        final TextView tvAmount;
        final TextView tvAccount;
        final TextView tvCreator;
        final TextView tvPaymentMethod;
        final View vCategoryColor;

        public ViewHolder(View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tv_date);
            tvCategory = itemView.findViewById(R.id.tv_category);
            tvDescription = itemView.findViewById(R.id.tv_description);
            tvAmount = itemView.findViewById(R.id.tv_amount);
            tvAccount = itemView.findViewById(R.id.tv_account);
            tvCreator = itemView.findViewById(R.id.tv_creator);
            tvPaymentMethod = itemView.findViewById(R.id.tv_payment_method);
            vCategoryColor = itemView.findViewById(R.id.v_category_color);
        }
    }
}
