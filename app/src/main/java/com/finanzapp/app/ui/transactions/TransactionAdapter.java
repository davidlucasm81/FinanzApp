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
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class TransactionAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public interface OnTransactionClickListener {
        void onTransactionClick(Transaction t);
        void onTransactionLongClick(Transaction t);
    }

    private static final int VIEW_TYPE_TRANSACTION = 0;
    private static final int VIEW_TYPE_AD = 1;
    private static final int AD_INTERVAL = 8; // Ad every 8 items

    private final List<Transaction> transactions;
    private final Map<String, String> categoryNames;
    private final Map<String, String> categoryColors;
    private final Map<String, String> accountNames;
    private final Map<String, String> memberNames;
    private final Map<String, String> paymentMethodLabels;
    private final OnTransactionClickListener listener;
    private boolean isPrivacyModeEnabled = false;
    private boolean isPremium = false;
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

    public void setPremium(boolean premium) {
        if (this.isPremium != premium) {
            this.isPremium = premium;
            notifyDataSetChanged();
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (isPremium) return VIEW_TYPE_TRANSACTION;
        if (position > 0 && position % AD_INTERVAL == 0) return VIEW_TYPE_AD;
        return VIEW_TYPE_TRANSACTION;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_AD) {
            AdView adView = new AdView(parent.getContext());
            adView.setAdSize(AdSize.MEDIUM_RECTANGLE);
            adView.setAdUnitId(com.finanzapp.app.BuildConfig.ADMOB_BANNER_INTERCALATED_ID);
            float density = parent.getContext().getResources().getDisplayMetrics().density;
            int height = (int) (250 * density);
            adView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
            return new AdViewHolder(adView);
        }
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transaction, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof AdViewHolder) {
            AdViewHolder adHolder = (AdViewHolder) holder;
            AdRequest adRequest = new AdRequest.Builder().build();
            adHolder.adView.loadAd(adRequest);
            return;
        }

        ViewHolder transactionHolder = (ViewHolder) holder;
        Transaction t = transactions.get(getTransactionPosition(position));

        transactionHolder.tvDate.setText(t.getDate() != null ? dateFormat.format(t.getDate().toDate()) : "");
        transactionHolder.tvCategory.setText(categoryNames.getOrDefault(t.getCategoryId(), transactionHolder.itemView.getContext().getString(R.string.label_category)));
        transactionHolder.tvDescription.setText(t.getDescription());
        transactionHolder.tvAccount.setText(accountNames.getOrDefault(t.getAccountId(), transactionHolder.itemView.getContext().getString(R.string.label_account)));

        int defaultCategoryColor = resolveColorPrimary(transactionHolder.itemView.getContext());
        int categoryColor = parseColorSafe(categoryColors.get(t.getCategoryId()), defaultCategoryColor);
        transactionHolder.tvCategory.setTextColor(categoryColor);
        transactionHolder.vCategoryColor.setBackgroundTintList(ColorStateList.valueOf(categoryColor));

        String methodLabel = paymentMethodLabels.getOrDefault(t.getPaymentMethod(), t.getPaymentMethod());
        transactionHolder.tvPaymentMethod.setText(methodLabel != null ? methodLabel : "");

        String creatorName = memberNames.getOrDefault(t.getCreatedBy(), "Usuario");
        transactionHolder.tvCreator.setText(transactionHolder.itemView.getContext().getString(R.string.by_user, creatorName));

        boolean isIncome = "income".equals(t.getType());
        double amount = t.getAmount();
        String amountStr;
        if (isPrivacyModeEnabled) {
            amountStr = (isIncome ? "+" : "-") + "****";
        } else {
            amountStr = (isIncome ? "+" : "-") + currencyFormat.format(amount);
        }
        transactionHolder.tvAmount.setText(amountStr);
        int amountColorRes = isIncome ? R.color.success : R.color.error;
        transactionHolder.tvAmount.setTextColor(ContextCompat.getColor(transactionHolder.itemView.getContext(), amountColorRes));

        transactionHolder.itemView.setOnClickListener(v -> listener.onTransactionClick(t));
        transactionHolder.itemView.setOnLongClickListener(v -> {
            listener.onTransactionLongClick(t);
            return true;
        });
    }

    private int getTransactionPosition(int adapterPosition) {
        if (isPremium) return adapterPosition;
        return adapterPosition - (adapterPosition / AD_INTERVAL);
    }

    @Override
    public int getItemCount() {
        if (isPremium || transactions.isEmpty()) return transactions.size();
        return transactions.size() + (transactions.size() / AD_INTERVAL);
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

    public static class AdViewHolder extends RecyclerView.ViewHolder {
        final AdView adView;
        public AdViewHolder(@NonNull View itemView) {
            super(itemView);
            adView = (AdView) itemView;
        }
    }
}
