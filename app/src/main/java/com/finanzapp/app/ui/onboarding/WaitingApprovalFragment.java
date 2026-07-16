package com.finanzapp.app.ui.onboarding;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.bumptech.glide.Glide;
import com.finanzapp.app.FinanzAppApplication;
import com.finanzapp.app.R;
import com.finanzapp.app.data.model.User;
import com.finanzapp.app.data.model.Invitation;
import com.finanzapp.app.databinding.FragmentWaitingApprovalBinding;
import com.finanzapp.app.util.Result;
import com.finanzapp.app.viewmodel.OnboardingViewModel;
import com.finanzapp.app.viewmodel.ViewModelFactory;
// ...existing code...
import com.google.firebase.auth.FirebaseAuth;
import android.widget.Toast;

public class WaitingApprovalFragment extends Fragment {
    private FragmentWaitingApprovalBinding binding;
    private OnboardingViewModel viewModel;
    private Invitation pendingInvitation;
    private String pendingFamilyId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentWaitingApprovalBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FinanzAppApplication.AppContainer appContainer = ((FinanzAppApplication) requireActivity().getApplication()).getAppContainer();
        ViewModelFactory factory = new ViewModelFactory(appContainer.getAuthRepository(), appContainer.getFamilyRepository());
        viewModel = new ViewModelProvider(this, factory).get(OnboardingViewModel.class);

        // Check if we already have the pending data stored from fetchPendingCodeRequest (called by Welcome)
        // If not, fetch it now
        String currentFamilyId = viewModel.getPendingCodeRequestFamilyIdValue();
        if (currentFamilyId == null) {
            android.util.Log.d("WaitingApprovalFragment", "familyId is null, calling fetchPendingCodeRequest");
            String uid = FirebaseAuth.getInstance().getUid();
            if (uid != null) {
                viewModel.fetchPendingCodeRequest(uid);
            }
        } else {
            android.util.Log.d("WaitingApprovalFragment", "familyId already set: " + currentFamilyId);
        }

        binding.btnBackToOnboarding.setOnClickListener(v -> {
            // If there's a pending code-request, cancel it first. Otherwise just navigate back.
            android.util.Log.d("WaitingApprovalFragment", "Cancel button pressed. pendingInvitation=" + pendingInvitation + ", pendingFamilyId=" + pendingFamilyId);
            if (pendingInvitation != null && pendingFamilyId != null) {
                // Trigger cancel in ViewModel; observer below will handle navigation on success
                android.util.Log.d("WaitingApprovalFragment", "Calling cancelPendingCodeRequest");
                viewModel.cancelPendingCodeRequest(pendingFamilyId, pendingInvitation);
            } else {
                android.util.Log.d("WaitingApprovalFragment", "No pending invitation to cancel, navigating back");
                Navigation.findNavController(v).popBackStack(R.id.welcomeFragment, false);
            }
        });

        binding.ivUserPhoto.setOnClickListener(v -> {
            Navigation.findNavController(v).navigate(R.id.settingsFragment);
        });


        setupObservers();
        loadUserData();
    }

    private void loadUserData() {
        viewModel.fetchUserData();
    }

    private void setupObservers() {
        // Observe the pending code request invitation
        // This will fire when fetchPendingCodeRequest() completes (if called) or when joinByCode() completes
        viewModel.getPendingCodeRequest().observe(getViewLifecycleOwner(), result -> {
            android.util.Log.d("WaitingApprovalFragment", "getPendingCodeRequest observer fired: " + result);
            if (result instanceof Result.Success) {
                Invitation inv = ((Result.Success<Invitation>) result).getData();
                if (inv != null) {
                    android.util.Log.d("WaitingApprovalFragment", "Invitation from observer: id=" + inv.getId());
                    // Get the familyId which should have been set by the same callback that updated pendingCodeRequest
                    String familyId = viewModel.getPendingCodeRequestFamilyIdValue();
                    android.util.Log.d("WaitingApprovalFragment", "FamilyId from ViewModel: " + familyId);
                    if (familyId != null) {
                        pendingInvitation = inv;
                        pendingFamilyId = familyId;
                        android.util.Log.d("WaitingApprovalFragment", "Successfully stored both invitation and familyId");
                    } else {
                        android.util.Log.d("WaitingApprovalFragment", "familyId is null, cannot store");
                        pendingInvitation = null;
                        pendingFamilyId = null;
                    }
                }
            } else if (result instanceof Result.Error) {
                android.util.Log.d("WaitingApprovalFragment", "Error fetching invitation");
                pendingInvitation = null;
                pendingFamilyId = null;
            }
            // Ignore Loading state
        });

        // Observe invitationAction (used for deletion/cancellation) to react to the cancel result
        viewModel.getInvitationAction().observe(getViewLifecycleOwner(), result -> {
            android.util.Log.d("WaitingApprovalFragment", "invitationAction observer fired: " + result);
            if (result instanceof Result.Loading) {
                android.util.Log.d("WaitingApprovalFragment", "Loading...");
                binding.btnBackToOnboarding.setEnabled(false);
            } else if (result instanceof Result.Success) {
                // Successfully cancelled the pending request; navigate back to welcome
                android.util.Log.d("WaitingApprovalFragment", "Success! Navigating back");
                Toast.makeText(requireContext(), "Solicitud cancelada", Toast.LENGTH_SHORT).show();
                Navigation.findNavController(requireView()).popBackStack(R.id.welcomeFragment, false);
            } else if (result instanceof Result.Error) {
                android.util.Log.d("WaitingApprovalFragment", "Error occurred");
                binding.btnBackToOnboarding.setEnabled(true);
                Exception e = ((Result.Error<?>) result).getException();
                String errorMsg = e != null ? e.getMessage() : "Unknown error";
                android.util.Log.e("WaitingApprovalFragment", "Error: " + errorMsg, e);
                Toast.makeText(requireContext(), "Error al cancelar: " + errorMsg, Toast.LENGTH_LONG).show();
            }
        });

        viewModel.getUserData().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof Result.Success) {
                User user = ((Result.Success<User>) result).getData();
                if (user != null && user.getPhotoUrl() != null) {
                    Glide.with(this)
                            .load(user.getPhotoUrl())
                            .placeholder(R.drawable.ic_user_placeholder)
                            .circleCrop()
                            .into(binding.ivUserPhoto);
                }
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
