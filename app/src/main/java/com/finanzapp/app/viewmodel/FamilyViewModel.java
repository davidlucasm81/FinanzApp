package com.finanzapp.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.finanzapp.app.data.model.Invitation;
import com.finanzapp.app.data.model.Member;
import com.finanzapp.app.data.repository.AuthRepository;
import com.finanzapp.app.data.repository.FamilyRepository;
import com.finanzapp.app.util.Result;
import com.finanzapp.app.util.SingleLiveEvent;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.List;

public class FamilyViewModel extends ViewModel {
    private final AuthRepository authRepository;
    private final FamilyRepository familyRepository;
    private final MutableLiveData<Result<List<Invitation>>> joinRequests = new MutableLiveData<>();
    private final SingleLiveEvent<Result<Boolean>> approvalResult = new SingleLiveEvent<>();
    private final MutableLiveData<Result<List<Member>>> members = new MutableLiveData<>();
    private final MutableLiveData<Result<List<Invitation>>> pendingInvitations = new MutableLiveData<>();
    private final SingleLiveEvent<Result<Boolean>> leaveResult = new SingleLiveEvent<>();
    private final SingleLiveEvent<Result<Boolean>> updateResult = new SingleLiveEvent<>();
    private final MutableLiveData<Result<com.finanzapp.app.data.model.Family>> familyData = new MutableLiveData<>();
    private final MutableLiveData<Result<Member>> currentMemberData = new MutableLiveData<>();

    private ListenerRegistration joinRequestsListener;
    private ListenerRegistration membersListener;
    private ListenerRegistration pendingInvitationsListener;

    // Referencia estable para poder registrar/desregistrar el mismo Runnable
    private final Runnable signOutCleanup = this::stopListening;

    public FamilyViewModel(AuthRepository authRepository, FamilyRepository familyRepository) {
        this.authRepository = authRepository;
        this.familyRepository = familyRepository;
        authRepository.registerPreSignOutCleanup(signOutCleanup);
    }

    public LiveData<Result<List<Invitation>>> getJoinRequests() {
        return joinRequests;
    }

    public LiveData<Result<Boolean>> getApprovalResult() {
        return approvalResult;
    }

    public LiveData<Result<List<Member>>> getMembers() {
        return members;
    }

    public LiveData<Result<List<Invitation>>> getPendingInvitations() {
        return pendingInvitations;
    }

    public LiveData<Result<Boolean>> getLeaveResult() {
        return leaveResult;
    }

    public LiveData<Result<Boolean>> getUpdateResult() {
        return updateResult;
    }

    public LiveData<Result<com.finanzapp.app.data.model.Family>> getFamilyData() {
        return familyData;
    }

    public LiveData<Result<Member>> getCurrentMemberData() {
        return currentMemberData;
    }

    public void fetchJoinRequests(String familyId) {
        joinRequests.setValue(new Result.Loading<>());
        if (joinRequestsListener != null) joinRequestsListener.remove();
        joinRequestsListener = familyRepository.getPendingJoinRequests(familyId, joinRequests::postValue);
    }

    public void approveRequest(String familyId, Invitation invitation) {
        approvalResult.setValue(new Result.Loading<>());
        familyRepository.approveJoinRequest(familyId, invitation, approvalResult::postValue);
    }

    public void rejectRequest(String familyId, String invitationId) {
        approvalResult.setValue(new Result.Loading<>());
        familyRepository.rejectJoinRequest(familyId, invitationId, approvalResult::postValue);
    }

    public void inviteByEmail(String familyId, String email) {
        approvalResult.setValue(new Result.Loading<>());
        familyRepository.inviteByEmail(familyId, email, approvalResult::postValue);
    }

    public void fetchMembers(String familyId) {
        members.setValue(new Result.Loading<>());
        if (membersListener != null) membersListener.remove();
        membersListener = familyRepository.getMembers(familyId, members::postValue);
    }

    public void fetchPendingInvitations(String familyId) {
        pendingInvitations.setValue(new Result.Loading<>());
        if (pendingInvitationsListener != null) pendingInvitationsListener.remove();
        pendingInvitationsListener = familyRepository.getPendingEmailInvitations(familyId, pendingInvitations::postValue);
    }

    public void cancelInvitation(String familyId, String invitationId) {
        familyRepository.deleteInvitation(familyId, invitationId, result -> {
            // We don't necessarily need to post to a specific result LiveData if the listener
            // on getPendingEmailInvitations will trigger an update automatically
        });
    }

    public void updateFamily(String familyId, String name, String currencyCode) {
        updateResult.setValue(new Result.Loading<>());
        familyRepository.updateFamily(familyId, name, currencyCode, updateResult::postValue);
    }

    public void fetchFamily(String familyId) {
        familyData.setValue(new Result.Loading<>());
        familyRepository.getFamily(familyId, familyData::postValue);
    }

    public void fetchCurrentMember(String familyId, String uid) {
        currentMemberData.setValue(new Result.Loading<>());
        if (membersListener != null) membersListener.remove();
        membersListener = familyRepository.getMembers(familyId, result -> {
            if (result instanceof Result.Success) {
                List<Member> memberList = ((Result.Success<List<Member>>) result).getData();
                for (Member m : memberList) {
                    if (m.getUid().equals(uid)) {
                        currentMemberData.postValue(new Result.Success<>(m));
                        return;
                    }
                }
                currentMemberData.postValue(new Result.Error<>(new Exception("Member not found")));
            } else if (result instanceof Result.Error) {
                currentMemberData.postValue(new Result.Error<>(((Result.Error<?>) result).getException()));
            }
        });
    }

    public void leaveFamily(String familyId) {
        leaveResult.setValue(new Result.Loading<>());
        familyRepository.leaveFamily(familyId, leaveResult::postValue);
    }

    public void updateMemberRole(String familyId, String memberUid, String newRole) {
        approvalResult.setValue(new Result.Loading<>());
        familyRepository.updateMemberRole(familyId, memberUid, newRole, approvalResult::postValue);
    }

    public void removeMember(String familyId, String memberUid) {
        approvalResult.setValue(new Result.Loading<>());
        familyRepository.removeMember(familyId, memberUid, approvalResult::postValue);
    }

    private void stopListening() {
        if (joinRequestsListener != null) {
            joinRequestsListener.remove();
            joinRequestsListener = null;
        }
        if (membersListener != null) {
            membersListener.remove();
            membersListener = null;
        }
        if (pendingInvitationsListener != null) {
            pendingInvitationsListener.remove();
            pendingInvitationsListener = null;
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        authRepository.unregisterPreSignOutCleanup(signOutCleanup);
        stopListening();
    }
}