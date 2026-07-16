package com.finanzapp.app.ui.onboarding;

import android.os.Bundle;
import android.text.Html;
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
import com.finanzapp.app.data.model.Family;
import com.finanzapp.app.data.model.Invitation;
import com.finanzapp.app.data.model.User;
import com.finanzapp.app.databinding.FragmentWelcomeBinding;

import com.finanzapp.app.util.Result;
import com.finanzapp.app.viewmodel.OnboardingViewModel;
import com.finanzapp.app.viewmodel.ViewModelFactory;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class WelcomeFragment extends Fragment {
    private FragmentWelcomeBinding binding;
    private OnboardingViewModel viewModel;
    private Invitation currentInvitation;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentWelcomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FinanzAppApplication.AppContainer appContainer = ((FinanzAppApplication) requireActivity().getApplication()).getAppContainer();
        ViewModelFactory factory = new ViewModelFactory(appContainer.getAuthRepository(), appContainer.getFamilyRepository());
        viewModel = new ViewModelProvider(this, factory).get(OnboardingViewModel.class);

        binding.btnCreateFamily.setOnClickListener(v -> 
                Navigation.findNavController(v).navigate(R.id.action_welcomeFragment_to_createFamilyFragment));

        binding.btnJoinFamily.setOnClickListener(v -> 
                Navigation.findNavController(v).navigate(R.id.action_welcomeFragment_to_joinByCodeFragment));

        binding.ivUserPhoto.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.settingsFragment));

        binding.btnAcceptInvite.setOnClickListener(v -> {
            String pendingFamilyId = viewModel.getPendingInvitationFamilyIdValue();
            if (currentInvitation != null && pendingFamilyId != null) {
                viewModel.acceptInvitation(currentInvitation, pendingFamilyId);
            } else {
                android.util.Log.w("WelcomeFragment", "No se pudo aceptar: currentInvitation=" + currentInvitation + ", pendingInvitationFamilyId=" + pendingFamilyId);
                android.widget.Toast.makeText(requireContext(), "No se pudo procesar la invitación, inténtalo de nuevo", android.widget.Toast.LENGTH_SHORT).show();
            }
        });

        binding.btnRejectInvite.setOnClickListener(v -> {
            String pendingFamilyId = viewModel.getPendingInvitationFamilyIdValue();
            if (currentInvitation != null && pendingFamilyId != null) {
                viewModel.rejectInvitation(pendingFamilyId, currentInvitation.getId());
            } else {
                android.util.Log.w("WelcomeFragment", "No se pudo rechazar: currentInvitation=" + currentInvitation + ", pendingInvitationFamilyId=" + pendingFamilyId);
                android.widget.Toast.makeText(requireContext(), "No se pudo procesar la invitación, inténtalo de nuevo", android.widget.Toast.LENGTH_SHORT).show();
            }
        });

        setupObservers();
        loadUserData();
        checkPendingInvitations();
    }

    private void loadUserData() {
        viewModel.fetchUserData();
    }

    private void setupObservers() {
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

        viewModel.getPendingCodeRequest().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof Result.Success) {
                // Pending code request found, navigate to waiting approval
                android.util.Log.d("WelcomeFragment", "Pending code request found, navigating to waiting approval");
                Navigation.findNavController(requireView()).navigate(R.id.action_welcomeFragment_to_waitingApprovalFragment);
            }
        });

        viewModel.getPendingInvitation().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof Result.Success) {
                currentInvitation = ((Result.Success<Invitation>) result).getData();
                binding.cvInvitationAlert.setVisibility(View.VISIBLE);
                android.util.Log.d("WelcomeFragment", "Showing invitation alert card");
            } else if (result instanceof Result.Error) {
                binding.cvInvitationAlert.setVisibility(View.GONE);
                android.util.Log.d("WelcomeFragment", "No invitation found, hiding card");
            }
        });

        viewModel.getFamilyInfo().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof Result.Success) {
                Family family = ((Result.Success<Family>) result).getData();
                String text = getString(R.string.invitation_alert_text, family.getName());
                binding.tvInvitationText.setText(Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY));
            }
        });

        viewModel.getInvitationAction().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof Result.Success) {
                if ("accepted".equals(viewModel.getLastAction())) {
                    // Navigate to Dashboard
                    android.content.Intent intent = new android.content.Intent(requireContext(), com.finanzapp.app.MainActivity.class);
                    startActivity(intent);
                    requireActivity().finish();
                } else {
                    // Rejected or other action, hide card
                    binding.cvInvitationAlert.setVisibility(View.GONE);
                    currentInvitation = null;
                }
            }
        });
    }

    private void checkPendingInvitations() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            if (user.getEmail() != null) {
                android.util.Log.d("WelcomeFragment", "Checking invitations for: " + user.getEmail());
                viewModel.fetchPendingInvitation(user.getEmail());
            }
            // Also check for pending code request
            android.util.Log.d("WelcomeFragment", "Checking for pending code request for uid: " + user.getUid());
            viewModel.fetchPendingCodeRequest(user.getUid());
        } else {
            android.util.Log.w("WelcomeFragment", "User is null, cannot check invitations");
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
