package com.finanzapp.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.finanzapp.app.data.model.Invitation;
import com.finanzapp.app.data.model.Member;
import com.finanzapp.app.data.repository.FamilyRepository;
import com.finanzapp.app.util.Result;

import java.util.List;

public class FamilyViewModel extends ViewModel {
    private final FamilyRepository familyRepository;
    private final MutableLiveData<Result<List<Invitation>>> joinRequests = new MutableLiveData<>();
    private final MutableLiveData<Result<Boolean>> approvalResult = new MutableLiveData<>();
    private final MutableLiveData<Result<List<Member>>> members = new MutableLiveData<>();
    private final MutableLiveData<Result<List<Invitation>>> pendingInvitations = new MutableLiveData<>();
    private final MutableLiveData<Result<Boolean>> leaveResult = new MutableLiveData<>();
    private final MutableLiveData<Result<Boolean>> updateResult = new MutableLiveData<>();
    private final MutableLiveData<Result<com.finanzapp.app.data.model.Family>> familyData = new MutableLiveData<>();

    public FamilyViewModel(FamilyRepository familyRepository) {
        this.familyRepository = familyRepository;
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

    public void fetchJoinRequests(String familyId) {
        joinRequests.setValue(new Result.Loading<>());
        familyRepository.getPendingJoinRequests(familyId, joinRequests::postValue);
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
        familyRepository.getMembers(familyId, members::postValue);
    }

    public void fetchPendingInvitations(String familyId) {
        pendingInvitations.setValue(new Result.Loading<>());
        familyRepository.getPendingEmailInvitations(familyId, pendingInvitations::postValue);
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

    public void leaveFamily(String familyId) {
        leaveResult.setValue(new Result.Loading<>());
        familyRepository.leaveFamily(familyId, leaveResult::postValue);
    }

    public void updateMemberRole(String familyId, String memberUid, String newRole) {
        approvalResult.setValue(new Result.Loading<>());
        familyRepository.updateMemberRole(familyId, memberUid, newRole, result -> approvalResult.postValue(result));
    }
}
