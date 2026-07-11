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
import androidx.recyclerview.widget.LinearLayoutManager;

import com.finanzapp.app.FinanzAppApplication;
import com.finanzapp.app.data.model.Invitation;
import com.finanzapp.app.databinding.FragmentManageJoinRequestsBinding;
import com.finanzapp.app.util.Result;
import com.finanzapp.app.viewmodel.FamilyViewModel;
import com.finanzapp.app.viewmodel.ViewModelFactory;

import java.util.List;

public class ManageJoinRequestsFragment extends Fragment {
    private FragmentManageJoinRequestsBinding binding;
    private FamilyViewModel viewModel;
    private JoinRequestAdapter adapter;
    private String familyId;

    public static ManageJoinRequestsFragment newInstance(String familyId) {
        ManageJoinRequestsFragment fragment = new ManageJoinRequestsFragment();
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
        binding = FragmentManageJoinRequestsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FinanzAppApplication.AppContainer appContainer = ((FinanzAppApplication) requireActivity().getApplication()).getAppContainer();
        ViewModelFactory factory = new ViewModelFactory(appContainer.getAuthRepository(), appContainer.getFamilyRepository());
        viewModel = new ViewModelProvider(this, factory).get(FamilyViewModel.class);

        setupRecyclerView();
        setupObservers();

        if (familyId != null) {
            viewModel.fetchJoinRequests(familyId);
        }
    }

    private void setupRecyclerView() {
        adapter = new JoinRequestAdapter(new JoinRequestAdapter.OnActionListener() {
            @Override
            public void onApprove(Invitation invitation) {
                viewModel.approveRequest(familyId, invitation);
            }

            @Override
            public void onReject(Invitation invitation) {
                viewModel.rejectRequest(familyId, invitation.getId());
            }
        });
        binding.rvRequests.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvRequests.setAdapter(adapter);
    }

    private void setupObservers() {
        viewModel.getJoinRequests().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof Result.Success) {
                List<Invitation> requests = ((Result.Success<List<Invitation>>) result).getData();
                adapter.setRequests(requests);
                binding.tvEmpty.setVisibility(requests.isEmpty() ? View.VISIBLE : View.GONE);
            } else if (result instanceof Result.Error) {
                Toast.makeText(requireContext(), "Error al cargar solicitudes", Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.getApprovalResult().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof Result.Success) {
                Toast.makeText(requireContext(), "Acción realizada con éxito", Toast.LENGTH_SHORT).show();
            } else if (result instanceof Result.Error) {
                Toast.makeText(requireContext(), "Error al procesar solicitud", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
