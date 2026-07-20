package com.finanzapp.app.ui.family;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.finanzapp.app.MainActivity;
import com.finanzapp.app.FinanzAppApplication;
import com.finanzapp.app.R;
import com.finanzapp.app.data.model.FamilyMembership;
import com.finanzapp.app.data.repository.AuthRepository;
import com.finanzapp.app.data.repository.FamilyRepository;
import com.finanzapp.app.ui.onboarding.OnboardingActivity;
import com.finanzapp.app.util.Result;
import com.finanzapp.app.viewmodel.FamilyViewModel;
import com.finanzapp.app.viewmodel.ViewModelFactory;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.List;

public class FamilySwitcherFragment extends BottomSheetDialogFragment {

    private String currentFamilyId;
    private FamiliesAdapter adapter;

    public static FamilySwitcherFragment newInstance(String currentFamilyId) {
        FamilySwitcherFragment fragment = new FamilySwitcherFragment();
        Bundle args = new Bundle();
        args.putString("current_family_id", currentFamilyId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_family_switcher, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() != null) {
            currentFamilyId = getArguments().getString("current_family_id");
        }

        FinanzAppApplication.AppContainer container = ((FinanzAppApplication) requireActivity().getApplication()).getAppContainer();
        FamilyRepository repository = container.getFamilyRepository();
        AuthRepository authRepository = container.getAuthRepository();

        new ViewModelProvider(this, new ViewModelFactory(authRepository, repository)).get(FamilyViewModel.class);

        RecyclerView rvFamilies = view.findViewById(R.id.rv_families);
        rvFamilies.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new FamiliesAdapter(currentFamilyId, this::onFamilySelected);
        rvFamilies.setAdapter(adapter);

        view.findViewById(R.id.btn_create_family).setOnClickListener(v -> {
            Intent intent = new Intent(requireActivity(), OnboardingActivity.class);
            intent.putExtra("mode", "create");
            startActivity(intent);
            dismiss();
        });

        view.findViewById(R.id.btn_join_family).setOnClickListener(v -> {
            Intent intent = new Intent(requireActivity(), OnboardingActivity.class);
            intent.putExtra("mode", "join");
            startActivity(intent);
            dismiss();
        });

        loadFamilies();
    }

    private void loadFamilies() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        FamilyRepository repository = ((FinanzAppApplication) requireActivity().getApplication()).getAppContainer().getFamilyRepository();
        repository.getUserFamilies(uid, result -> {
            if (result instanceof Result.Success) {
                adapter.setItems(((Result.Success<List<FamilyMembership>>) result).getData());
            } else if (result instanceof Result.Error) {
                Toast.makeText(getContext(), "Error al cargar familias", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void onFamilySelected(FamilyMembership membership) {
        if (membership.getFamilyId().equals(currentFamilyId)) {
            dismiss();
            return;
        }

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        FamilyRepository repository = ((FinanzAppApplication) requireActivity().getApplication()).getAppContainer().getFamilyRepository();
        repository.switchActiveFamily(uid, membership.getFamilyId(), result -> {
            if (result instanceof Result.Success) {
                // Phase 7 bis: Clear navigation and restart MainActivity to reload all viewmodels with new familyId
                restartApp();
            } else {
                Toast.makeText(getContext(), "Error al cambiar de familia", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void restartApp() {
        if (getActivity() == null) return;
        
        Intent intent = new Intent(requireActivity(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
        dismiss();
    }

    private static class FamiliesAdapter extends RecyclerView.Adapter<FamiliesAdapter.ViewHolder> {
        private final List<FamilyMembership> items = new ArrayList<>();
        private final String currentFamilyId;
        private final OnFamilyClickListener listener;

        public FamiliesAdapter(String currentFamilyId, OnFamilyClickListener listener) {
            this.currentFamilyId = currentFamilyId;
            this.listener = listener;
        }

        public void setItems(List<FamilyMembership> newItems) {
            items.clear();
            items.addAll(newItems);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_family_membership, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            FamilyMembership item = items.get(position);
            holder.bind(item, currentFamilyId, listener);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            private final TextView tvName;
            private final TextView tvRole;
            private final View ivActive;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tv_family_name);
                tvRole = itemView.findViewById(R.id.tv_role);
                ivActive = itemView.findViewById(R.id.iv_active);
            }

            public void bind(FamilyMembership item, String currentFamilyId, OnFamilyClickListener listener) {
                tvName.setText(item.getFamilyName());
                tvRole.setText(item.getRole());
                ivActive.setVisibility(item.getFamilyId().equals(currentFamilyId) ? View.VISIBLE : View.GONE);
                itemView.setOnClickListener(v -> listener.onFamilySelected(item));
            }
        }
    }

    public interface OnFamilyClickListener {
        void onFamilySelected(FamilyMembership membership);
    }
}
