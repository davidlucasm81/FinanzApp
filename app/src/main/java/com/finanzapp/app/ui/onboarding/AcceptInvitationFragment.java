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

    /**
     * Usar este constructor cuando quien navega hasta aquí (p. ej. la pantalla de
     * Bienvenida) ya ha localizado la invitación pendiente, para no repetir la
     * consulta ni depender de que termine antes de que el usuario pulse un botón.
     */
    public static AcceptInvitationFragment newInstance(String invitationId, String familyId) {
        AcceptInvitationFragment fragment = new AcceptInvitationFragment();
        Bundle args = new Bundle();
        args.putString("invitationId", invitationId);
        args.putString("familyId", familyId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            invitationId = getArguments().getString("invitationId");
            familyId = getArguments().getString("familyId");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentAcceptInvitationBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.btnAccept.setOnClickListener(v -> acceptInvitation());
        binding.btnReject.setOnClickListener(v -> rejectInvitation());

        if (invitationId != null && familyId != null) {
            // Ya nos pasaron la invitación resuelta: los botones pueden usarse ya.
            setButtonsEnabled(true);
        } else {
            // Fallback: no nos pasaron argumentos, la buscamos nosotros mismos.
            setButtonsEnabled(false);
            checkPendingInvitation();
        }
    }

    private void setButtonsEnabled(boolean enabled) {
        binding.btnAccept.setEnabled(enabled);
        binding.btnReject.setEnabled(enabled);
    }

    private void checkPendingInvitation() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.getEmail() == null) {
            Toast.makeText(requireContext(), "No se pudo verificar tu sesión", Toast.LENGTH_SHORT).show();
            Navigation.findNavController(requireView()).popBackStack();
            return;
        }

        String normalizedEmail = user.getEmail().toLowerCase().trim();

        FirebaseFirestore.getInstance().collectionGroup(FirestorePaths.INVITATIONS)
                .whereEqualTo("type", "email_invite")
                .whereEqualTo("targetEmail", normalizedEmail)
                .whereEqualTo("status", "pending")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty() && queryDocumentSnapshots.getDocuments().get(0).getReference().getParent().getParent() != null) {
                        invitationId = queryDocumentSnapshots.getDocuments().get(0).getId();
                        familyId = queryDocumentSnapshots.getDocuments().get(0).getReference().getParent().getParent().getId();
                        setButtonsEnabled(true);
                    } else {
                        Toast.makeText(requireContext(), "La invitación ya no está disponible", Toast.LENGTH_SHORT).show();
                        Navigation.findNavController(requireView()).popBackStack();
                    }
                })
                .addOnFailureListener(e -> {
                    // Antes este fallo era silencioso: familyId/invitationId se quedaban a null
                    // para siempre y pulsar "Aceptar" no hacía nada. Ahora lo mostramos.
                    android.util.Log.e("AcceptInvitation", "Error buscando invitación pendiente", e);
                    Toast.makeText(requireContext(), "No se pudo comprobar tu invitación. Inténtalo de nuevo.", Toast.LENGTH_LONG).show();
                });
    }

    private void acceptInvitation() {
        if (familyId == null || invitationId == null) {
            Toast.makeText(requireContext(), "Todavía estamos comprobando tu invitación, espera un momento", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) return;

        setButtonsEnabled(false);

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
                setButtonsEnabled(true);
                android.util.Log.e("AcceptInvitation", "Error al aceptar invitación", task.getException());
                Toast.makeText(requireContext(), "Error al aceptar invitación", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void rejectInvitation() {
        if (familyId == null || invitationId == null) {
            Toast.makeText(requireContext(), "Todavía estamos comprobando tu invitación, espera un momento", Toast.LENGTH_SHORT).show();
            return;
        }

        setButtonsEnabled(false);

        FirebaseFirestore.getInstance().collection(FirestorePaths.getFamilyPath(familyId) + "/" + FirestorePaths.INVITATIONS).document(invitationId)
                .update("status", "rejected", "resolvedAt", Timestamp.now(), "resolvedByUid", FirebaseAuth.getInstance().getUid())
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Navigation.findNavController(requireView()).popBackStack();
                    } else {
                        setButtonsEnabled(true);
                        android.util.Log.e("AcceptInvitation", "Error al rechazar invitación", task.getException());
                        Toast.makeText(requireContext(), "Error al rechazar invitación", Toast.LENGTH_SHORT).show();
                    }
                });
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
