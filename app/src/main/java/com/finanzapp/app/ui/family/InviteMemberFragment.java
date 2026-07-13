package com.finanzapp.app.ui.family;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.finanzapp.app.FinanzAppApplication;
import com.finanzapp.app.databinding.FragmentInviteMemberBinding;
import com.finanzapp.app.util.Result;
import com.finanzapp.app.viewmodel.FamilyViewModel;
import com.finanzapp.app.viewmodel.ViewModelFactory;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.finanzapp.app.data.firebase.FirestorePaths;
import com.finanzapp.app.data.model.User;

public class InviteMemberFragment extends Fragment {
    private FragmentInviteMemberBinding binding;
    private FamilyViewModel viewModel;
    private String familyId;

    public static InviteMemberFragment newInstance(String familyId) {
        InviteMemberFragment fragment = new InviteMemberFragment();
        Bundle args = new Bundle();
        args.putString("familyId", familyId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            familyId = getArguments().getString("familyId");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentInviteMemberBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FinanzAppApplication.AppContainer appContainer = ((FinanzAppApplication) requireActivity().getApplication()).getAppContainer();
        ViewModelFactory factory = new ViewModelFactory(appContainer.getAuthRepository(), appContainer.getFamilyRepository());
        viewModel = new ViewModelProvider(this, factory).get(FamilyViewModel.class);

        setupObservers();

        binding.btnSendInvite.setOnClickListener(v -> {
            if (binding.tilEmail.getEditText() == null) return;
            String email = binding.tilEmail.getEditText().getText().toString().trim();
            if (email.isEmpty()) {
                binding.tilEmail.setError("Introduce un email");
                return;
            }
            if (familyId != null) {
                viewModel.inviteByEmail(familyId, email);
            }
        });

        resolveFamilyId();
    }

    private void resolveFamilyId() {
        if (familyId != null) return;

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            FirebaseFirestore.getInstance().collection(FirestorePaths.USERS).document(uid).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        User user = documentSnapshot.toObject(User.class);
                        if (user != null && user.getFamilyId() != null) {
                            familyId = user.getFamilyId();
                        }
                    });
        }
    }

    private void setupObservers() {
        viewModel.getApprovalResult().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof Result.Loading) {
                binding.btnSendInvite.setEnabled(false);
            } else if (result instanceof Result.Success) {
                Toast.makeText(requireContext(), "Invitación enviada", Toast.LENGTH_SHORT).show();
                Navigation.findNavController(requireView()).popBackStack();
            } else if (result instanceof Result.Error) {
                binding.btnSendInvite.setEnabled(true);
                Toast.makeText(requireContext(), "Error al enviar invitación", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
