package com.finanzapp.app.ui.onboarding;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.finanzapp.app.MainActivity;
import com.finanzapp.app.data.firebase.FirestorePaths;
import com.finanzapp.app.data.model.Member;
import com.finanzapp.app.databinding.FragmentAcceptInvitationBinding;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

public class AcceptInvitationFragment extends Fragment {
    private FragmentAcceptInvitationBinding binding;
    private String invitationId;
    private String familyId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentAcceptInvitationBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // For now, let's assume we fetch the invitation from the arguments
        // or just look for the pending email invite again.
        checkPendingInvitation();

        binding.btnAccept.setOnClickListener(v -> acceptInvitation());
        binding.btnReject.setOnClickListener(v -> rejectInvitation());
    }

    private void checkPendingInvitation() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.getEmail() == null) return;

        FirebaseFirestore.getInstance().collectionGroup(FirestorePaths.INVITATIONS)
                .whereEqualTo("type", "email_invite")
                .whereEqualTo("targetEmail", user.getEmail())
                .whereEqualTo("status", "pending")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty() && queryDocumentSnapshots.getDocuments().get(0).getReference().getParent().getParent() != null) {
                        invitationId = queryDocumentSnapshots.getDocuments().get(0).getId();
                        familyId = queryDocumentSnapshots.getDocuments().get(0).getReference().getParent().getParent().getId();
                    } else {
                        // No invite found, go back
                        Navigation.findNavController(requireView()).popBackStack();
                    }
                });
    }

    private void acceptInvitation() {
        if (familyId == null || invitationId == null) return;

        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) return;

        WriteBatch batch = FirebaseFirestore.getInstance().batch();

        // 1. Mark invitation as accepted
        batch.update(FirebaseFirestore.getInstance().collection(FirestorePaths.getFamilyPath(familyId) + "/" + FirestorePaths.INVITATIONS).document(invitationId), 
                "status", "accepted", 
                "resolvedAt", Timestamp.now(), 
                "resolvedByUid", firebaseUser.getUid());

        // 2. Create member
        Member member = new Member(
                firebaseUser.getUid(),
                firebaseUser.getDisplayName(),
                firebaseUser.getEmail(),
                "member",
                "approved",
                Timestamp.now()
        );
        batch.set(FirebaseFirestore.getInstance().collection(FirestorePaths.getMembersPath(familyId)).document(firebaseUser.getUid()), member);

        // 3. Update user's familyId
        batch.update(FirebaseFirestore.getInstance().collection(FirestorePaths.USERS).document(firebaseUser.getUid()), "familyId", familyId);

        batch.commit().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                navigateToMain();
            } else {
                Toast.makeText(requireContext(), "Error al aceptar invitación", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void rejectInvitation() {
        if (familyId == null || invitationId == null) return;

        FirebaseFirestore.getInstance().collection(FirestorePaths.getFamilyPath(familyId) + "/" + FirestorePaths.INVITATIONS).document(invitationId)
                .update("status", "rejected", "resolvedAt", Timestamp.now(), "resolvedByUid", FirebaseAuth.getInstance().getUid())
                .addOnCompleteListener(task -> Navigation.findNavController(requireView()).popBackStack());
    }

    private void navigateToMain() {
        Intent intent = new Intent(requireContext(), MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
