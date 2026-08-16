package com.finanzapp.app.ui.dashboard;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.finanzapp.app.R;
import com.finanzapp.app.data.model.Member;
import com.finanzapp.app.databinding.ItemMemberBalanceBinding;
import com.finanzapp.app.util.CurrencyFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DashboardMemberBalanceAdapter extends RecyclerView.Adapter<DashboardMemberBalanceAdapter.ViewHolder> {
    private final List<Member> members = new ArrayList<>();
    private Map<String, Double> balances;
    private String currencyCode = "EUR";
    private String currentUserId;
    private boolean isPrivacyModeEnabled = false;

    public void setItems(List<Member> members, Map<String, Double> balances, String currencyCode, String currentUserId) {
        this.members.clear();
        if (members != null) this.members.addAll(members);
        this.balances = balances;
        this.currencyCode = currencyCode;
        this.currentUserId = currentUserId;
        notifyDataSetChanged();
    }

    public void setPrivacyModeEnabled(boolean enabled) {
        this.isPrivacyModeEnabled = enabled;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemMemberBalanceBinding binding = ItemMemberBalanceBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Member m = members.get(position);
        String name = m.getDisplayName();
        if (m.getUid() != null && m.getUid().equals(currentUserId)) {
            name += " (" + holder.itemView.getContext().getString(R.string.label_me) + ")";
        }
        holder.binding.tvName.setText(name);

        // Set avatar
        holder.binding.tvInitials.setText(getInitials(m.getDisplayName()));
        holder.binding.ivAvatar.setColorFilter(getAvatarColor(m.getUid()));
        
        Double balObj = (balances != null && m.getUid() != null) ? balances.get(m.getUid()) : null;
        double bal = (balObj != null) ? balObj : 0.0;

        if (isPrivacyModeEnabled) {
            holder.binding.tvBalance.setText(R.string.privacy_mode_masked_value);
        } else {
            holder.binding.tvBalance.setText(CurrencyFormatter.format(bal, currencyCode));
        }
        
        int colorRes = bal >= 0 ? R.color.success : R.color.error;
        holder.binding.tvBalance.setTextColor(holder.itemView.getContext().getResources().getColor(colorRes, null));
    }

    @Override
    public int getItemCount() {
        return members.size();
    }

    private String getInitials(String name) {
        if (name == null || name.isEmpty()) return "?";
        String[] parts = name.split(" ");
        StringBuilder initials = new StringBuilder();
        for (int i = 0; i < Math.min(parts.length, 2); i++) {
            if (!parts[i].isEmpty()) {
                initials.append(parts[i].charAt(0));
            }
        }
        return initials.toString().toUpperCase();
    }

    private int getAvatarColor(String uid) {
        if (uid == null) return Color.GRAY;
        int hash = uid.hashCode();
        int[] colors = {
                0xFFF44336, 0xFFE91E63, 0xFF9C27B0, 0xFF673AB7,
                0xFF3F51B5, 0xFF2196F3, 0xFF03A9F4, 0xFF00BCD4,
                0xFF009688, 0xFF4CAF50, 0xFF8BC34A, 0xFFCDDC39,
                0xFFFFEB3B, 0xFFFFC107, 0xFFFF9800, 0xFFFF5722
        };
        return colors[Math.abs(hash) % colors.length];
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemMemberBalanceBinding binding;

        ViewHolder(ItemMemberBalanceBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
