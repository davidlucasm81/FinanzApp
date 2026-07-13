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
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

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
import com.finanzapp.app.data.model.User;
import com.finanzapp.app.util.Result;
import com.finanzapp.app.viewmodel.FamilyViewModel;
import com.finanzapp.app.viewmodel.ViewModelFactory;

public class FamilySettingsFragment extends Fragment {
    private FamilyViewModel viewModel;
    private String familyId;
    private Spinner spinnerCurrency;
    private Button btnSave;
    private Button btnLeave;
    private TextView tvInviteCode;
    private ImageButton btnCopyCode;
    private com.google.android.material.textfield.TextInputLayout tilName;

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
        if (familyId != null) {
            viewModel.fetchFamily(familyId);
            return;
        }

        // Try to get familyId from current user if not provided in args
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            FirebaseFirestore.getInstance().collection(FirestorePaths.USERS).document(uid).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        User user = documentSnapshot.toObject(User.class);
                        if (user != null && user.getFamilyId() != null) {
                            familyId = user.getFamilyId();
                            viewModel.fetchFamily(familyId);
                        }
                    });
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FinanzAppApplication.AppContainer appContainer = ((FinanzAppApplication) requireActivity().getApplication()).getAppContainer();
        ViewModelFactory factory = new ViewModelFactory(appContainer.getAuthRepository(), appContainer.getFamilyRepository());
        viewModel = new ViewModelProvider(this, factory).get(FamilyViewModel.class);

        spinnerCurrency = requireView().findViewById(com.finanzapp.app.R.id.spinner_currency);
        btnSave = requireView().findViewById(com.finanzapp.app.R.id.btn_save);
        btnLeave = requireView().findViewById(com.finanzapp.app.R.id.btn_leave);
        tilName = requireView().findViewById(com.finanzapp.app.R.id.til_name);
        tvInviteCode = requireView().findViewById(com.finanzapp.app.R.id.tv_invite_code);
        btnCopyCode = requireView().findViewById(com.finanzapp.app.R.id.btn_copy_code);

        // Simple currency selector
        String[] currencies = new String[]{"EUR", "USD", "GBP"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, currencies);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCurrency.setAdapter(adapter);

        btnSave.setOnClickListener(v -> {
            if (familyId == null) return;
            String name = tilName.getEditText() != null ? tilName.getEditText().getText().toString().trim() : "";
            String currency = spinnerCurrency.getSelectedItem().toString();
            if (name.isEmpty()) {
                tilName.setError("Introduce un nombre");
                return;
            }
            viewModel.updateFamily(familyId, name, currency);
        });

        btnLeave.setOnClickListener(v -> {
            if (familyId == null) return;
            showLeaveConfirmation();
        });

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
                
                // Set spinner selection
                String currency = family.getCurrencyCode();
                if (currency != null) {
                    for (int i = 0; i < spinnerCurrency.getCount(); i++) {
                        if (spinnerCurrency.getItemAtPosition(i).toString().equals(currency)) {
                            spinnerCurrency.setSelection(i);
                            break;
                        }
                    }
                }
            }
        });

        viewModel.getUpdateResult().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof Result.Success) {
                Toast.makeText(requireContext(), "Familia actualizada", Toast.LENGTH_SHORT).show();
                Navigation.findNavController(requireView()).popBackStack();
            } else if (result instanceof Result.Error) {
                Toast.makeText(requireContext(), "Error al actualizar", Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.getLeaveResult().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof Result.Success) {
                Toast.makeText(requireContext(), "Has salido de la familia", Toast.LENGTH_SHORT).show();
                navigateToOnboarding();
            } else if (result instanceof Result.Error) {
                Toast.makeText(requireContext(), "Error al salir de la familia", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showLeaveConfirmation() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Abandonar familia")
                .setMessage("¿Estás seguro de que quieres salir de esta familia? Si eres el último miembro, se borrarán todos los datos.")
                .setPositiveButton("Confirmar", (dialog, which) -> viewModel.leaveFamily(familyId))
                .setNegativeButton("Cancelar", null)
                .show();
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


