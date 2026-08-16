package com.finanzapp.app.ui.sharedexpenses;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.bumptech.glide.Glide;
import com.finanzapp.app.FinanzAppApplication;
import com.finanzapp.app.R;
import com.finanzapp.app.data.model.Member;
import com.finanzapp.app.data.sharedexpenses.BalanceCalculator;
import com.finanzapp.app.util.CurrencyFormatter;
import com.finanzapp.app.util.Result;
import com.finanzapp.app.viewmodel.BalancesViewModel;
import com.finanzapp.app.viewmodel.ViewModelFactory;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;
import java.util.Map;

public class BalancesFragment extends Fragment {
    private BalancesViewModel viewModel;
    private String familyId;
    private String currencyCode = "EUR"; // Fallback

    private LinearLayout layoutNetBalances, layoutSuggestedPayments, layoutEmpty;
    private View progressBar;
    private ImageView ivPrivacyToggle;
    private boolean isPrivacyModeEnabled = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_balances, container, false);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            familyId = getArguments().getString("familyId");
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FinanzAppApplication.AppContainer appContainer = ((FinanzAppApplication) requireActivity().getApplication()).getAppContainer();
        ViewModelFactory factory = new ViewModelFactory(appContainer, familyId);
        viewModel = new ViewModelProvider(this, factory).get(BalancesViewModel.class);

        layoutNetBalances = view.findViewById(R.id.layout_net_balances);
        layoutSuggestedPayments = view.findViewById(R.id.layout_suggested_payments);
        layoutEmpty = view.findViewById(R.id.layout_empty);
        progressBar = view.findViewById(R.id.progress_bar);
        ivPrivacyToggle = view.findViewById(R.id.iv_privacy_toggle);

        viewModel.initPrivacyMode(requireContext());
        ivPrivacyToggle.setOnClickListener(v -> viewModel.togglePrivacyMode(requireContext()));
        
        MaterialButton btnHistory = view.findViewById(R.id.btn_settlement_history);
        btnHistory.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putString("familyId", familyId);
            Navigation.findNavController(v).navigate(R.id.action_balancesFragment_to_settlementHistoryFragment, args);
        });

        // Get currency from shared pref or family data if possible. 
        // For now we'll assume EUR or fetch it from family data in a real app.
        // Let's observe family data too if we want perfect formatting.

        setupObservers();
    }

    private void setupObservers() {
        viewModel.getCurrencyCode().observe(getViewLifecycleOwner(), code -> {
            this.currencyCode = code;
            if (viewModel.getBalancesData().getValue() != null) {
                updateUI(viewModel.getBalancesData().getValue());
            }
        });

        viewModel.getBalancesData().observe(getViewLifecycleOwner(), data -> {
            if (data != null) {
                updateUI(data);
            }
        });

        viewModel.getSettlementResult().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof Result.Success) {
                Toast.makeText(requireContext(), R.string.operation_success, Toast.LENGTH_SHORT).show();
            } else if (result instanceof Result.Error) {
                Toast.makeText(requireContext(), R.string.error_generic, Toast.LENGTH_LONG).show();
            }
        });

        viewModel.isPrivacyModeEnabled().observe(getViewLifecycleOwner(), enabled -> {
            this.isPrivacyModeEnabled = enabled;
            ivPrivacyToggle.setImageResource(enabled ? R.drawable.ic_visibility_off : R.drawable.ic_visibility);
            if (viewModel.getBalancesData().getValue() != null) {
                updateUI(viewModel.getBalancesData().getValue());
            }
        });
    }

    private void updateUI(BalancesViewModel.BalancesData data) {
        layoutNetBalances.removeAllViews();
        layoutSuggestedPayments.removeAllViews();

        boolean hasNonZeroBalance = false;
        String currentUserId = viewModel.getCurrentUserId();

        // 1. Net Balances
        for (Member m : data.members) {
            Double bal = data.netBalances.get(m.getUid());
            if (bal == null) bal = 0.0;

            if (Math.abs(bal) > 0.009) {
                hasNonZeroBalance = true;
                String displayName = m.getDisplayName();
                if (m.getUid() != null && m.getUid().equals(currentUserId)) {
                    displayName += " (" + getString(R.string.label_me) + ")";
                }
                addBalanceItem(displayName, bal, m.getUid());
            }
        }

        if (!hasNonZeroBalance) {
            addBalanceItem(getString(R.string.label_none), 0.0, null);
        }

        // 2. Suggested Payments
        if (data.suggestedPayments.isEmpty()) {
            addNoneItem(layoutSuggestedPayments);
        } else {
            for (BalanceCalculator.SuggestedPayment p : data.suggestedPayments) {
                View itemView = getLayoutInflater().inflate(R.layout.item_suggested_payment, layoutSuggestedPayments, false);
                TextView tvAmount = itemView.findViewById(R.id.tv_amount);
                
                TextView tvInitialsFrom = itemView.findViewById(R.id.tv_initials_from);
                ImageView ivAvatarFrom = itemView.findViewById(R.id.iv_avatar_from);
                TextView tvInitialsTo = itemView.findViewById(R.id.tv_initials_to);
                ImageView ivAvatarTo = itemView.findViewById(R.id.iv_avatar_to);
                ImageView ivArrow = itemView.findViewById(R.id.iv_arrow);
                
                View btnSettle = itemView.findViewById(R.id.btn_settle);

                Member fromMember = getMember(p.fromUid, data.members);
                Member toMember = getMember(p.toUid, data.members);

                String fromName = fromMember != null ? fromMember.getDisplayName() : p.fromUid;
                String toName = toMember != null ? toMember.getDisplayName() : p.toUid;

                String amountStr;
                if (isPrivacyModeEnabled) {
                    amountStr = getString(R.string.privacy_mode_masked_value);
                } else {
                    amountStr = CurrencyFormatter.format(p.amount, currencyCode);
                }
                
                tvAmount.setText(amountStr);
                
                // Contextual colors
                if (p.fromUid.equals(currentUserId)) {
                    // I owe money
                    tvAmount.setTextColor(getResources().getColor(R.color.error, null));
                    ivArrow.setColorFilter(getResources().getColor(R.color.error, null));
                } else if (p.toUid.equals(currentUserId)) {
                    // I receive money
                    tvAmount.setTextColor(getResources().getColor(R.color.success, null));
                    ivArrow.setColorFilter(getResources().getColor(R.color.success, null));
                } else {
                    tvAmount.setTextColor(getResources().getColor(R.color.primary_dark, null));
                    ivArrow.setColorFilter(getResources().getColor(R.color.primary_dark, null));
                }
                
                // Set avatars
                tvInitialsFrom.setText(getInitials(fromName));
                if (fromMember != null && fromMember.getPhotoUrl() != null) {
                    tvInitialsFrom.setVisibility(View.GONE);
                    ivAvatarFrom.setColorFilter(null);
                    Glide.with(this)
                            .load(fromMember.getPhotoUrl())
                            .circleCrop()
                            .into(ivAvatarFrom);
                } else {
                    tvInitialsFrom.setVisibility(View.VISIBLE);
                    ivAvatarFrom.setImageResource(R.drawable.shape_circle);
                    ivAvatarFrom.setColorFilter(getAvatarColor(p.fromUid));
                }
                
                tvInitialsTo.setText(getInitials(toName));
                if (toMember != null && toMember.getPhotoUrl() != null) {
                    tvInitialsTo.setVisibility(View.GONE);
                    ivAvatarTo.setColorFilter(null);
                    Glide.with(this)
                            .load(toMember.getPhotoUrl())
                            .circleCrop()
                            .into(ivAvatarTo);
                } else {
                    tvInitialsTo.setVisibility(View.VISIBLE);
                    ivAvatarTo.setImageResource(R.drawable.shape_circle);
                    ivAvatarTo.setColorFilter(getAvatarColor(p.toUid));
                }

                final String finalFromName = fromMember != null ? fromMember.getDisplayName() : p.fromUid;
                final String finalToName = toMember != null ? toMember.getDisplayName() : p.toUid;
                btnSettle.setOnClickListener(v -> showSettleDialog(p, finalFromName, finalToName));
                
                layoutSuggestedPayments.addView(itemView);
            }
        }

        // Only show full empty state if really nothing is there (this might be redundant now)
        layoutEmpty.setVisibility(data.members.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void addBalanceItem(String name, double bal, String uid) {
        View itemView = getLayoutInflater().inflate(R.layout.item_member_balance, layoutNetBalances, false);
        TextView tvName = itemView.findViewById(R.id.tv_name);
        TextView tvBalance = itemView.findViewById(R.id.tv_balance);
        TextView tvInitials = itemView.findViewById(R.id.tv_initials);
        ImageView ivAvatar = itemView.findViewById(R.id.iv_avatar);

        tvName.setText(name);
        if (isPrivacyModeEnabled) {
            tvBalance.setText(R.string.privacy_mode_masked_value);
        } else {
            tvBalance.setText(CurrencyFormatter.format(bal, currencyCode));
        }
        tvBalance.setTextColor(getResources().getColor(bal >= 0 ? R.color.success : R.color.error, null));

        tvInitials.setText(getInitials(name));

        if (uid != null) {
            BalancesViewModel.BalancesData data = viewModel.getBalancesData().getValue();
            Member member = data != null ? getMember(uid, data.members) : null;
            
            if (member != null && member.getPhotoUrl() != null) {
                tvInitials.setVisibility(View.GONE);
                ivAvatar.setColorFilter(null);
                Glide.with(this)
                        .load(member.getPhotoUrl())
                        .circleCrop()
                        .into(ivAvatar);
            } else {
                tvInitials.setVisibility(View.VISIBLE);
                ivAvatar.setImageResource(R.drawable.shape_circle);
                ivAvatar.setColorFilter(getAvatarColor(uid));
            }
        } else {
            tvInitials.setVisibility(View.VISIBLE);
            ivAvatar.setImageResource(R.drawable.shape_circle);
            ivAvatar.setColorFilter(getAvatarColor(null));
        }

        layoutNetBalances.addView(itemView);
    }

    private void addNoneItem(LinearLayout layout) {
        TextView tvNone = new TextView(requireContext());
        tvNone.setText(R.string.label_none);
        tvNone.setPadding(32, 32, 32, 32);
        tvNone.setAlpha(0.6f);
        layout.addView(tvNone);
    }

    private Member getMember(String uid, List<Member> members) {
        if (uid == null) return null;
        for (Member m : members) {
            if (uid.equals(m.getUid())) {
                return m;
            }
        }
        return null;
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

    private void showSettleDialog(BalanceCalculator.SuggestedPayment p, String fromName, String toName) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_settle_debt, null);
        TextInputLayout tilAmount = dialogView.findViewById(R.id.til_amount);
        TextInputLayout tilNote = dialogView.findViewById(R.id.til_note);
        TextView tvSummary = dialogView.findViewById(R.id.tv_summary);

        if (tilAmount.getEditText() != null) {
            tilAmount.getEditText().setText(String.valueOf(p.amount));
        }

        String amountStrFormatted = CurrencyFormatter.format(p.amount, currencyCode);
        tvSummary.setText(getString(R.string.dialog_settle_msg, amountStrFormatted, fromName, toName));

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialog_settle_title)
                .setView(dialogView)
                .setPositiveButton(R.string.label_confirm, (dialog, which) -> {
                    String amountText = tilAmount.getEditText() != null ? tilAmount.getEditText().getText().toString() : "";
                    String note = tilNote.getEditText() != null ? tilNote.getEditText().getText().toString() : null;

                    try {
                        double amount = Double.parseDouble(amountText);
                        if (amount <= 0) {
                            Toast.makeText(requireContext(), R.string.error_amount, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        viewModel.addSettlement(familyId, p.fromUid, p.toUid, amount, note);
                    } catch (NumberFormatException e) {
                        Toast.makeText(requireContext(), R.string.error_amount, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel_button, null)
                .show();
    }
}
