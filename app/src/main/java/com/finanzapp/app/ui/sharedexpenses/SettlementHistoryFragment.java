package com.finanzapp.app.ui.sharedexpenses;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.finanzapp.app.FinanzAppApplication;
import com.finanzapp.app.R;
import com.finanzapp.app.data.model.Member;
import com.finanzapp.app.data.model.Settlement;
import com.finanzapp.app.util.CurrencyFormatter;
import com.finanzapp.app.util.Result;
import com.finanzapp.app.viewmodel.BalancesViewModel;
import com.finanzapp.app.viewmodel.ViewModelFactory;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SettlementHistoryFragment extends Fragment {
    private BalancesViewModel viewModel;
    private String familyId;
    private SettlementAdapter adapter;
    private TextView tvEmpty;
    private boolean isPrivacyModeEnabled = false;
    private String currencyCode = "EUR";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settlement_history, container, false);
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

        com.google.android.material.appbar.MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> Navigation.findNavController(v).popBackStack());

        RecyclerView rv = view.findViewById(R.id.rv_history);
        tvEmpty = view.findViewById(R.id.tv_empty);

        adapter = new SettlementAdapter();
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);

        viewModel.initPrivacyMode(requireContext());
        setupObservers();
    }

    private void setupObservers() {
        viewModel.getBalancesData().observe(getViewLifecycleOwner(), data -> {
            if (data != null) {
                Result<List<Settlement>> sResult = viewModel.getSettlements().getValue();
                if (sResult instanceof Result.Success) {
                    adapter.setItems(((Result.Success<List<Settlement>>) sResult).getData(), data.members);
                }
            }
        });

        viewModel.getSettlements().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof Result.Success) {
                BalancesViewModel.BalancesData data = viewModel.getBalancesData().getValue();
                if (data != null) {
                    adapter.setItems(((Result.Success<List<Settlement>>) result).getData(), data.members);
                }
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
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        });

        viewModel.getCurrencyCode().observe(getViewLifecycleOwner(), code -> {
            this.currencyCode = code;
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        });
    }

    private class SettlementAdapter extends RecyclerView.Adapter<SettlementAdapter.ViewHolder> {
        private List<Settlement> items = new ArrayList<>();
        private List<Member> members = new ArrayList<>();
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

        public void setItems(List<Settlement> items, List<Member> members) {
            this.items = items;
            this.members = members;
            notifyDataSetChanged();
            tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_settlement, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Settlement s = items.get(position);
            if (isPrivacyModeEnabled) {
                holder.tvAmount.setText(R.string.privacy_mode_masked_value);
            } else {
                holder.tvAmount.setText(CurrencyFormatter.format(s.getAmount(), currencyCode));
            }
            holder.tvDate.setText(s.getCreatedAt() != null ? dateFormat.format(s.getCreatedAt().toDate()) : "");
            
            String fromName = getName(s.getFromUid(), members);
            String toName = getName(s.getToUid(), members);
            holder.tvDesc.setText(fromName + " → " + toName);

            if (s.getNote() != null && !s.getNote().isEmpty()) {
                holder.tvNote.setVisibility(View.VISIBLE);
                holder.tvNote.setText(s.getNote());
            } else {
                holder.tvNote.setVisibility(View.GONE);
            }

            String regBy = getName(s.getCreatedBy(), members);
            holder.tvRegBy.setText(getString(R.string.label_created_by) + ": " + regBy);

            holder.btnDelete.setOnClickListener(v -> showDeleteDialog(s));

            holder.itemView.setOnLongClickListener(v -> {
                showDeleteDialog(s);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        private String getName(String uid, List<Member> members) {
            for (Member m : members) {
                if (m.getUid().equals(uid)) {
                    String name = m.getDisplayName();
                    if (uid.equals(viewModel.getCurrentUserId())) {
                        name += " (" + getString(R.string.label_me) + ")";
                    }
                    return name;
                }
            }
            return uid;
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvAmount, tvDate, tvDesc, tvNote, tvRegBy;
            View btnDelete;

            ViewHolder(View v) {
                super(v);
                tvAmount = v.findViewById(R.id.tv_amount);
                tvDate = v.findViewById(R.id.tv_date);
                tvDesc = v.findViewById(R.id.tv_description);
                tvNote = v.findViewById(R.id.tv_note);
                tvRegBy = v.findViewById(R.id.tv_registered_by);
                btnDelete = v.findViewById(R.id.btn_delete);
            }
        }
    }

    private void showDeleteDialog(Settlement s) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete_button)
                .setMessage("¿Eliminar este registro de liquidación?")
                .setPositiveButton(R.string.label_confirm, (dialog, which) -> {
                    viewModel.deleteSettlement(familyId, s.getId());
                })
                .setNegativeButton(R.string.cancel_button, null)
                .show();
    }
}
