package com.finanzapp.app.ui.family;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.navigation.Navigation;

import com.finanzapp.app.FinanzAppApplication;
import com.finanzapp.app.data.firebase.FirestorePaths;
import com.finanzapp.app.data.model.Invitation;
import com.finanzapp.app.data.model.User;
import com.finanzapp.app.util.Result;
import com.finanzapp.app.viewmodel.FamilyViewModel;
import com.finanzapp.app.viewmodel.ViewModelFactory;
import com.finanzapp.app.data.model.Member;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class MemberListFragment extends Fragment {
    private FamilyViewModel viewModel;
    private MemberAdapter adapter;
    private String familyId;
    private androidx.recyclerview.widget.RecyclerView rvMembers;
    private Button btnInvite;
    private TextView tvEmpty;
    
    private List<Member> currentMembers = new ArrayList<>();
    private List<Invitation> currentInvitations = new ArrayList<>();
    private List<Invitation> currentJoinRequests = new ArrayList<>();

    public static MemberListFragment newInstance(String familyId) {
        MemberListFragment f = new MemberListFragment();
        Bundle args = new Bundle();
        args.putString("familyId", familyId);
        f.setArguments(args);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(com.finanzapp.app.R.layout.fragment_member_list, container, false);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            familyId = getArguments().getString("familyId");
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FinanzAppApplication.AppContainer appContainer = ((FinanzAppApplication) requireActivity().getApplication()).getAppContainer();
        ViewModelFactory factory = new ViewModelFactory(appContainer.getAuthRepository(), appContainer.getFamilyRepository());
        viewModel = new ViewModelProvider(this, factory).get(FamilyViewModel.class);

        rvMembers = requireView().findViewById(com.finanzapp.app.R.id.rv_members);
        btnInvite = requireView().findViewById(com.finanzapp.app.R.id.btn_invite);
        tvEmpty = requireView().findViewById(com.finanzapp.app.R.id.tv_empty);

        adapter = new MemberAdapter();
        adapter.setOnActionListener(new MemberAdapter.OnActionListener() {
            @Override
            public void onCancel(Invitation invitation) {
                if (familyId != null) {
                    viewModel.cancelInvitation(familyId, invitation.getId());
                }
            }

            @Override
            public void onApprove(Invitation invitation) {
                if (familyId != null) {
                    viewModel.approveRequest(familyId, invitation);
                }
            }

            @Override
            public void onReject(Invitation invitation) {
                if (familyId != null) {
                    viewModel.rejectRequest(familyId, invitation.getId());
                }
            }
        });
        rvMembers.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvMembers.setAdapter(adapter);

        btnInvite.setOnClickListener(v -> {
            if (familyId != null) {
                Bundle args = new Bundle();
                args.putString("familyId", familyId);
                Navigation.findNavController(requireView()).navigate(com.finanzapp.app.R.id.inviteMemberFragment, args);
            }
        });

        setupObservers();
        resolveFamilyId();
    }

    private void resolveFamilyId() {
        if (familyId != null) {
            fetchData();
            return;
        }

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            FirebaseFirestore.getInstance().collection(FirestorePaths.USERS).document(uid).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        User user = documentSnapshot.toObject(User.class);
                        if (user != null && user.getFamilyId() != null) {
                            familyId = user.getFamilyId();
                            fetchData();
                        }
                    });
        }
    }

    private void fetchData() {
        viewModel.fetchMembers(familyId);
        viewModel.fetchPendingInvitations(familyId);
        viewModel.fetchJoinRequests(familyId);
    }

    private void setupObservers() {
        viewModel.getMembers().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof Result.Success) {
                currentMembers = ((Result.Success<List<Member>>) result).getData();
                updateList();
            } else if (result instanceof Result.Error) {
                Toast.makeText(requireContext(), "Error al cargar miembros", Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.getPendingInvitations().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof Result.Success) {
                currentInvitations = ((Result.Success<List<Invitation>>) result).getData();
                updateList();
            }
        });

        viewModel.getJoinRequests().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof Result.Success) {
                currentJoinRequests = ((Result.Success<List<Invitation>>) result).getData();
                updateList();
            }
        });

        viewModel.getApprovalResult().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof Result.Success) {
                Toast.makeText(requireContext(), "Acción realizada con éxito", Toast.LENGTH_SHORT).show();
                fetchData(); // Refresh all
            } else if (result instanceof Result.Error) {
                Toast.makeText(requireContext(), "Error al procesar solicitud", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateList() {
        List<MemberListItem> items = new ArrayList<>();
        // Priority: Join Requests, then Email Invitations, then Members
        for (Invitation i : currentJoinRequests) {
            items.add(new MemberListItem(i));
        }
        for (Invitation i : currentInvitations) {
            items.add(new MemberListItem(i));
        }
        for (Member m : currentMembers) {
            items.add(new MemberListItem(m));
        }
        adapter.setItems(items);
        tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }
}


