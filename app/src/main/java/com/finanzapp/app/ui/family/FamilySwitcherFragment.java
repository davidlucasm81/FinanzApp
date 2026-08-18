package com.finanzapp.app.ui.family;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.finanzapp.app.FinanzAppApplication;
import com.finanzapp.app.MainActivity;
import com.finanzapp.app.R;
import com.finanzapp.app.data.model.FamilyMembership;
import com.finanzapp.app.ui.onboarding.OnboardingActivity;
import com.finanzapp.app.util.Result;
import com.finanzapp.app.viewmodel.FamilySwitcherViewModel;
import com.finanzapp.app.viewmodel.ViewModelFactory;

import java.util.ArrayList;
import java.util.List;

public class FamilySwitcherFragment extends Fragment {

    private String currentFamilyId;
    private FamiliesAdapter adapter;
    private FamilySwitcherViewModel viewModel;
    private boolean showArchived = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_family_switcher, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FinanzAppApplication.AppContainer appContainer = ((FinanzAppApplication) requireActivity().getApplication()).getAppContainer();
        ViewModelFactory factory = new ViewModelFactory(appContainer);
        viewModel = new ViewModelProvider(this, factory).get(FamilySwitcherViewModel.class);

        RecyclerView rvFamilies = view.findViewById(R.id.rv_families);
        rvFamilies.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new FamiliesAdapter(this::onFamilySelected, this::onFamilyOptionsClick);
        rvFamilies.setAdapter(adapter);

        view.findViewById(R.id.btn_back).setOnClickListener(v -> 
                androidx.navigation.Navigation.findNavController(v).popBackStack());

        view.findViewById(R.id.btn_create_family).setOnClickListener(v -> {
            Intent intent = new Intent(requireActivity(), OnboardingActivity.class);
            intent.putExtra("mode", "create");
            startActivity(intent);
        });

        view.findViewById(R.id.btn_join_family).setOnClickListener(v -> {
            Intent intent = new Intent(requireActivity(), OnboardingActivity.class);
            intent.putExtra("mode", "join");
            startActivity(intent);
        });

        com.google.android.material.button.MaterialButton btnToggleArchived = view.findViewById(R.id.btn_toggle_archived);
        btnToggleArchived.setOnClickListener(v -> {
            showArchived = !showArchived;
            btnToggleArchived.setText(showArchived ? R.string.btn_hide_archived : R.string.btn_show_archived);
            btnToggleArchived.setIconResource(showArchived ? R.drawable.ic_visibility_off : R.drawable.ic_visibility);
            adapter.setShowArchived(showArchived);
        });

        setupObservers(view);

        viewModel.fetchCurrentFamilyId();
        viewModel.fetchMemberships();
    }

    private void setupObservers(View view) {
        View progressBar = view.findViewById(R.id.progress_bar);
        com.google.android.material.button.MaterialButton btnToggleArchived = view.findViewById(R.id.btn_toggle_archived);

        viewModel.getCurrentFamilyId().observe(getViewLifecycleOwner(), familyId -> {
            currentFamilyId = familyId;
            adapter.setCurrentFamilyId(familyId);
        });

        viewModel.getMemberships().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof Result.Success) {
                progressBar.setVisibility(View.GONE);
                List<FamilyMembership> memberships = ((Result.Success<List<FamilyMembership>>) result).getData();
                adapter.setItems(memberships);
                
                boolean hasArchived = false;
                for (FamilyMembership m : memberships) {
                    if (m.isArchived()) {
                        hasArchived = true;
                        break;
                    }
                }
                btnToggleArchived.setVisibility(hasArchived ? View.VISIBLE : View.GONE);
            } else if (result instanceof Result.Loading) {
                progressBar.setVisibility(View.VISIBLE);
            } else if (result instanceof Result.Error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(getContext(), R.string.error_load_families, Toast.LENGTH_LONG).show();
            }
        });

        viewModel.getSwitchResult().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof Result.Success) {
                restartApp();
            } else if (result instanceof Result.Error) {
                Toast.makeText(getContext(), R.string.error_switch_family, Toast.LENGTH_LONG).show();
            }
        });

        viewModel.getArchiveResult().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof Result.Success) {
                viewModel.fetchMemberships();
                viewModel.fetchCurrentFamilyId();
            } else if (result instanceof Result.Error) {
                Toast.makeText(getContext(), R.string.error_generic, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void onFamilySelected(FamilyMembership membership) {
        if (membership.isArchived()) return;
        if (membership.getFamilyId().equals(currentFamilyId)) return;

        viewModel.switchFamily(membership.getFamilyId());
    }

    private void onFamilyOptionsClick(View anchor, FamilyMembership membership) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        if (membership.isArchived()) {
            popup.getMenu().add(0, 1, 0, R.string.menu_unarchive_family);
        } else {
            // Cannot archive active family
            if (!membership.getFamilyId().equals(currentFamilyId)) {
                popup.getMenu().add(0, 2, 0, R.string.menu_archive_family);
            }
        }

        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                viewModel.setArchived(membership.getFamilyId(), false);
                return true;
            } else if (item.getItemId() == 2) {
                viewModel.setArchived(membership.getFamilyId(), true);
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void restartApp() {
        if (getActivity() == null) return;
        Intent intent = new Intent(requireActivity(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }

    private static class FamiliesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_HEADER = 0;
        private static final int TYPE_ITEM = 1;

        private final List<Object> displayItems = new ArrayList<>();
        private List<FamilyMembership> allMemberships = new ArrayList<>();
        private String currentFamilyId;
        private boolean showArchived = false;
        private final OnFamilyClickListener listener;
        private final OnFamilyOptionsListener optionsListener;

        public FamiliesAdapter(OnFamilyClickListener listener, OnFamilyOptionsListener optionsListener) {
            this.listener = listener;
            this.optionsListener = optionsListener;
        }

        public void setCurrentFamilyId(String familyId) {
            this.currentFamilyId = familyId;
            notifyDataSetChanged();
        }

        public void setShowArchived(boolean show) {
            this.showArchived = show;
            updateDisplayItems();
        }

        public void setItems(List<FamilyMembership> memberships) {
            this.allMemberships = memberships;
            updateDisplayItems();
        }

        private void updateDisplayItems() {
            displayItems.clear();
            List<FamilyMembership> active = new ArrayList<>();
            List<FamilyMembership> archived = new ArrayList<>();

            for (FamilyMembership m : allMemberships) {
                if (m.isArchived()) archived.add(m);
                else active.add(m);
            }

            displayItems.addAll(active);

            if (showArchived && !archived.isEmpty()) {
                displayItems.add("ARCHIVED_HEADER");
                displayItems.addAll(archived);
            }

            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            return displayItems.get(position) instanceof String ? TYPE_HEADER : TYPE_ITEM;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_HEADER) {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_section_header, parent, false);
                return new HeaderViewHolder(view);
            } else {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_family_membership, parent, false);
                return new ItemViewHolder(view);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof HeaderViewHolder) {
                ((HeaderViewHolder) holder).bind();
            } else {
                FamilyMembership item = (FamilyMembership) displayItems.get(position);
                ((ItemViewHolder) holder).bind(item, currentFamilyId, listener, optionsListener);
            }
        }

        @Override
        public int getItemCount() {
            return displayItems.size();
        }

        static class HeaderViewHolder extends RecyclerView.ViewHolder {
            private final TextView tvTitle;
            public HeaderViewHolder(@NonNull View itemView) {
                super(itemView);
                tvTitle = (TextView) itemView;
            }
            public void bind() {
                tvTitle.setText(R.string.label_archived_families);
            }
        }

        static class ItemViewHolder extends RecyclerView.ViewHolder {
            private final TextView tvName;
            private final TextView tvMode;
            private final TextView tvRole;
            private final View ivActive;
            private final ImageButton btnMenu;

            public ItemViewHolder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tv_family_name);
                tvMode = itemView.findViewById(R.id.tv_mode);
                tvRole = itemView.findViewById(R.id.tv_role);
                ivActive = itemView.findViewById(R.id.iv_active);
                btnMenu = itemView.findViewById(R.id.btn_menu);
            }

            public void bind(FamilyMembership item, String currentFamilyId, OnFamilyClickListener listener, OnFamilyOptionsListener optionsListener) {
                tvName.setText(item.getFamilyName());
                int modeRes = "shared_expenses".equals(item.getMode()) ? R.string.mode_shared_expenses : R.string.mode_normal;
                tvMode.setText(modeRes);
                tvRole.setText(item.getRole());
                ivActive.setVisibility(item.getFamilyId().equals(currentFamilyId) ? View.VISIBLE : View.GONE);
                
                if (item.isArchived()) {
                    itemView.setAlpha(0.6f);
                    itemView.setOnClickListener(null);
                } else {
                    itemView.setAlpha(1.0f);
                    itemView.setOnClickListener(v -> listener.onFamilySelected(item));
                }

                btnMenu.setOnClickListener(v -> optionsListener.onFamilyOptionsClick(v, item));
            }
        }
    }

    public interface OnFamilyClickListener {
        void onFamilySelected(FamilyMembership membership);
    }

    public interface OnFamilyOptionsListener {
        void onFamilyOptionsClick(View anchor, FamilyMembership membership);
    }
}
