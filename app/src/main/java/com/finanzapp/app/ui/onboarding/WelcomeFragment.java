package com.finanzapp.app.ui.onboarding;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.finanzapp.app.R;
import com.finanzapp.app.databinding.FragmentWelcomeBinding;

import com.finanzapp.app.data.firebase.FirestorePaths;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class WelcomeFragment extends Fragment {
    private FragmentWelcomeBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentWelcomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.btnCreateFamily.setOnClickListener(v -> 
                Navigation.findNavController(v).navigate(R.id.action_welcomeFragment_to_createFamilyFragment));

        binding.btnJoinFamily.setOnClickListener(v -> 
                Navigation.findNavController(v).navigate(R.id.action_welcomeFragment_to_joinByCodeFragment));

        checkPendingInvitations();
    }

    private void checkPendingInvitations() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        // Check email invitations first
        if (user.getEmail() != null) {
            FirebaseFirestore.getInstance().collectionGroup(FirestorePaths.INVITATIONS)
                    .whereEqualTo("type", "email_invite")
                    .whereEqualTo("targetEmail", user.getEmail())
                    .whereEqualTo("status", "pending")
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        if (!queryDocumentSnapshots.isEmpty()) {
                            if (getView() != null) {
                                Navigation.findNavController(getView()).navigate(R.id.action_welcomeFragment_to_acceptInvitationFragment);
                            }
                        } else {
                            checkPendingCodeRequests(user.getUid());
                        }
                    });
        } else {
            checkPendingCodeRequests(user.getUid());
        }
    }

    private void checkPendingCodeRequests(String uid) {
        FirebaseFirestore.getInstance().collectionGroup(FirestorePaths.INVITATIONS)
                .whereEqualTo("type", "code_request")
                .whereEqualTo("requestedByUid", uid)
                .whereEqualTo("status", "pending")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        if (getView() != null) {
                            Navigation.findNavController(getView()).navigate(R.id.action_welcomeFragment_to_waitingApprovalFragment);
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
