package com.finanzapp.app.ui.family;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.finanzapp.app.R;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.finanzapp.app.FinanzAppApplication;
import com.finanzapp.app.ui.onboarding.OnboardingActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.finanzapp.app.data.firebase.FirestorePaths;
import com.finanzapp.app.data.model.Member;
import com.finanzapp.app.data.model.User;
import com.finanzapp.app.util.Result;
import com.finanzapp.app.viewmodel.FamilyViewModel;
import com.finanzapp.app.viewmodel.ViewModelFactory;

public class FamilySettingsFragment extends Fragment {
    private FamilyViewModel viewModel;
    private String familyId;
    private AutoCompleteTextView autoCurrency;
    private Button btnSave;
    private Button btnManageCategories;
    private TextView tvInviteCode;
    private com.google.android.material.textfield.TextInputLayout tilName;
    private com.google.android.material.textfield.TextInputLayout tilCurrency;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(com.finanzapp.app.R.layout.fragment_family_settings, container, false);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            familyId = getArguments().getString("familyId");
        }
    }

    private void resolveFamilyId() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (familyId != null) {
            viewModel.fetchFamily(familyId);
            if (uid != null) {
                viewModel.fetchCurrentMember(familyId, uid);
            }
            return;
        }

        // Try to get familyId from current user if not provided in args
        if (uid != null) {
            FirebaseFirestore.getInstance().collection(FirestorePaths.USERS).document(uid).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        User user = documentSnapshot.toObject(User.class);
                        if (user != null && user.getFamilyId() != null) {
                            familyId = user.getFamilyId();
                            viewModel.fetchFamily(familyId);
                            viewModel.fetchCurrentMember(familyId, uid);
                        }
                    });
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FinanzAppApplication.AppContainer appContainer = ((FinanzAppApplication) requireActivity().getApplication()).getAppContainer();
        ViewModelFactory factory = new ViewModelFactory(appContainer);
        viewModel = new ViewModelProvider(this, factory).get(FamilyViewModel.class);

        autoCurrency = requireView().findViewById(com.finanzapp.app.R.id.auto_currency);
        btnSave = requireView().findViewById(com.finanzapp.app.R.id.btn_save);
        Button btnLeave = requireView().findViewById(R.id.btn_leave);
        btnManageCategories = requireView().findViewById(com.finanzapp.app.R.id.btn_manage_categories);
        tilName = requireView().findViewById(com.finanzapp.app.R.id.til_name);
        tilCurrency = requireView().findViewById(R.id.til_currency);
        tvInviteCode = requireView().findViewById(com.finanzapp.app.R.id.tv_invite_code);
        ImageButton btnCopyCode = requireView().findViewById(R.id.btn_copy_code);

        // Modern currency selector using AutoCompleteTextView
        String[] currencies = new String[]{"EUR", "USD", "GBP"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, currencies);
        autoCurrency.setAdapter(adapter);

        btnSave.setOnClickListener(v -> {
            if (familyId == null) return;
            String name = tilName.getEditText() != null ? tilName.getEditText().getText().toString().trim() : "";
            String currency = autoCurrency.getText().toString();
            if (name.isEmpty()) {
                tilName.setError(getString(R.string.label_name_required));
                return;
            }
            viewModel.updateFamily(familyId, name, currency);
        });

        btnLeave.setOnClickListener(v -> {
            if (familyId == null) return;
            showLeaveConfirmation();
        });

        btnManageCategories.setOnClickListener(v -> Navigation.findNavController(v).navigate(R.id.action_familySettingsFragment_to_manageCategoriesFragment));

        btnCopyCode.setOnClickListener(v -> copyCodeToClipboard());
        tvInviteCode.setOnClickListener(v -> copyCodeToClipboard());

        setupObservers();
        resolveFamilyId();
    }

    private void copyCodeToClipboard() {
        String code = tvInviteCode.getText().toString();
        if (code.equals("------")) return;

        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Family Invite Code", code);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(requireContext(), com.finanzapp.app.R.string.code_copied, Toast.LENGTH_SHORT).show();
    }

    private void setupObservers() {
        viewModel.getFamilyData().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof Result.Success) {
                com.finanzapp.app.data.model.Family family = ((Result.Success<com.finanzapp.app.data.model.Family>) result).getData();
                if (tilName.getEditText() != null) {
                    tilName.getEditText().setText(family.getName());
                }
                
                if (family.getInviteCode() != null) {
                    tvInviteCode.setText(family.getInviteCode());
                }
                
                // Set currency selection
                String currency = family.getCurrencyCode();
                if (currency != null) {
                    autoCurrency.setText(currency, false);
                }
            }
        });

        viewModel.getUpdateResult().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof Result.Success) {
                Toast.makeText(requireContext(), R.string.msg_family_updated, Toast.LENGTH_SHORT).show();
                Navigation.findNavController(requireView()).popBackStack();
            } else if (result instanceof Result.Error) {
                Toast.makeText(requireContext(), R.string.error_update_family, Toast.LENGTH_LONG).show();
            }
        });

        viewModel.getLeaveResult().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof Result.Success) {
                Toast.makeText(requireContext(), R.string.msg_leave_family_success, Toast.LENGTH_LONG).show();
                // Phase 7 bis: Decide whether to go to Dashboard or Onboarding based on remaining memberships
                checkMembershipsAndNavigate();
            } else if (result instanceof Result.Error) {
                Toast.makeText(requireContext(), R.string.error_leave_family, Toast.LENGTH_LONG).show();
            }
        });

        viewModel.getCurrentMemberData().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof Result.Success) {
                Member member = ((Result.Success<Member>) result).getData();
                String role = member.getRole();
                boolean isAdminOrOwner = "admin".equals(role) || "owner".equals(role);
                
                // Management section visibility (Member can always see Manage Categories but maybe not edit? 
                // AGENTS.md says: "Solo un member con role == admin o role == owner puede: gestionar categorías")
                btnManageCategories.setVisibility(isAdminOrOwner ? View.VISIBLE : View.GONE);

                // Solo admin/owner pueden editar el nombre de la familia y su moneda
                btnSave.setVisibility(isAdminOrOwner ? View.VISIBLE : View.GONE);
                if (tilName.getEditText() != null) {
                    tilName.getEditText().setEnabled(isAdminOrOwner);
                }
                
                autoCurrency.setEnabled(isAdminOrOwner);
                tilCurrency.setEnabled(isAdminOrOwner);

                if (!isAdminOrOwner) {
                    tilName.setHelperText("Solo un administrador puede editar la información de la familia");
                } else {
                    tilName.setHelperText(null);
                }
            }
        });
    }

    private void showLeaveConfirmation() {
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.dialog_abandon_family))
                .setMessage(getString(R.string.dialog_abandon_family_msg))
                .setPositiveButton(R.string.label_confirm, (dialog, which) -> viewModel.leaveFamily(familyId))
                .setNegativeButton(R.string.cancel_button, null)
                .show();
    }

    private void checkMembershipsAndNavigate() {
        String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            navigateToOnboarding();
            return;
        }

        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection(com.finanzapp.app.data.firebase.FirestorePaths.getMembershipsPath(uid))
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        navigateToOnboarding();
                    } else {
                        navigateToMain();
                    }
                })
                .addOnFailureListener(e -> navigateToOnboarding());
    }

    private void navigateToMain() {
        Intent intent = new Intent(requireContext(), com.finanzapp.app.MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }

    private void navigateToOnboarding() {
        Intent intent = new Intent(requireContext(), OnboardingActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }
}


