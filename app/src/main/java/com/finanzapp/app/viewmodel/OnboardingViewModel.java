package com.finanzapp.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.finanzapp.app.data.model.Family;
import com.finanzapp.app.data.model.Invitation;
import com.finanzapp.app.data.model.User;
import com.finanzapp.app.data.repository.FamilyRepository;
import com.finanzapp.app.util.Result;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.finanzapp.app.data.firebase.FirestorePaths;

public class OnboardingViewModel extends ViewModel {
    private final FamilyRepository familyRepository;
    private final MutableLiveData<Result<Family>> createFamilyResult = new MutableLiveData<>();
    private final MutableLiveData<Result<Boolean>> joinByCodeResult = new MutableLiveData<>();
    private final MutableLiveData<Result<Invitation>> pendingInvitation = new MutableLiveData<>();
    // Antes había un único `pendingFamilyId` compartido entre el flujo de invitación por
    // email y el de solicitud por código. Como ambos se consultan casi a la vez desde
    // WelcomeFragment, el que respondía último pisaba el valor del otro (normalmente el
    // de code-request, que casi siempre no encuentra nada y sobreescribía con null el
    // familyId correcto de la invitación por email). Separados para que no se pisen.
    private final MutableLiveData<String> pendingInvitationFamilyId = new MutableLiveData<>();
    private final MutableLiveData<String> pendingCodeRequestFamilyId = new MutableLiveData<>();
    private final MutableLiveData<Result<Family>> familyInfo = new MutableLiveData<>();
    private final MutableLiveData<Result<Boolean>> invitationAction = new MutableLiveData<>();
    private final MutableLiveData<Result<Invitation>> pendingCodeRequest = new MutableLiveData<>();
    private final MutableLiveData<Result<User>> userData = new MutableLiveData<>();
    private String lastAction; // "accepted" | "rejected"

    public OnboardingViewModel(FamilyRepository familyRepository) {
        this.familyRepository = familyRepository;
    }

    public LiveData<Result<Family>> getCreateFamilyResult() {
        return createFamilyResult;
    }

    public LiveData<Result<Boolean>> getJoinByCodeResult() {
        return joinByCodeResult;
    }

    public LiveData<Result<Invitation>> getPendingInvitation() {
        return pendingInvitation;
    }

    public LiveData<Result<Family>> getFamilyInfo() {
        return familyInfo;
    }

    public LiveData<Result<Boolean>> getInvitationAction() {
        return invitationAction;
    }

    public LiveData<Result<Invitation>> getPendingCodeRequest() {
        return pendingCodeRequest;
    }

    public LiveData<Result<User>> getUserData() {
        return userData;
    }

    public String getLastAction() {
        return lastAction;
    }

    public String getPendingInvitationFamilyIdValue() {
        return pendingInvitationFamilyId.getValue();
    }

    public String getPendingCodeRequestFamilyIdValue() {
        return pendingCodeRequestFamilyId.getValue();
    }

    public void fetchUserData() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            userData.setValue(new Result.Error<>(new Exception("User not authenticated")));
            return;
        }

        FirebaseFirestore.getInstance().collection(FirestorePaths.USERS).document(uid)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult().exists()) {
                        User user = task.getResult().toObject(User.class);
                        userData.postValue(new Result.Success<>(user));
                    } else {
                        userData.postValue(new Result.Error<>(new Exception("User not found")));
                    }
                });
    }

    public void fetchPendingInvitation(String email) {
        pendingInvitation.setValue(new Result.Loading<>());
        familyRepository.findInvitationByEmail(email, (result, familyId) -> {
            pendingInvitation.postValue(result);
            pendingInvitationFamilyId.postValue(familyId);
            if (result instanceof Result.Success && familyId != null) {
                fetchFamilyInfo(familyId);
            }
        });
    }

    public void fetchPendingCodeRequest(String uid) {
        pendingCodeRequest.setValue(new Result.Loading<>());
        familyRepository.findPendingCodeRequest(uid, (result, familyId) -> {
            android.util.Log.d("OnboardingViewModel", "Callback received: result=" + result + ", familyId=" + familyId);
            // IMPORTANT: Use postValue (thread-safe) since this comes from Firestore background thread
            pendingCodeRequestFamilyId.postValue(familyId);
            pendingCodeRequest.postValue(result);
            if (result instanceof Result.Success) {
                android.util.Log.d("OnboardingViewModel", "Pending code request found for uid: " + uid + " familyId: " + familyId);
            }
        });
    }

    private void fetchFamilyInfo(String familyId) {
        familyRepository.getFamily(familyId, familyInfo::postValue);
    }

    public void acceptInvitation(Invitation invitation, String familyId) {
        lastAction = "accepted";
        invitationAction.setValue(new Result.Loading<>());
        familyRepository.acceptInvitation(invitation, familyId, invitationAction::postValue);
    }

    public void rejectInvitation(String familyId, String invitationId) {
        lastAction = "rejected";
        invitationAction.setValue(new Result.Loading<>());
        familyRepository.deleteInvitation(familyId, invitationId, invitationAction::postValue);
    }

    public void createFamily(String name, String currencyCode) {
        createFamilyResult.setValue(new Result.Loading<>());
        familyRepository.createFamily(name, currencyCode, createFamilyResult::postValue);
    }

    public void joinByCode(String code) {
        joinByCodeResult.setValue(new Result.Loading<>());
        familyRepository.joinByCodeWithInvitation(code, (result, invitation, familyId) -> {
            android.util.Log.d("OnboardingViewModel", "joinByCodeWithInvitation callback: result=" + result + ", invId=" + (invitation != null ? invitation.getId() : "null") + ", familyId=" + familyId);
            if (result instanceof Result.Success && invitation != null && familyId != null) {
                // Use postValue (thread-safe) since this callback comes from Firestore background thread
                android.util.Log.d("OnboardingViewModel", "Storing familyId: " + familyId);
                pendingCodeRequestFamilyId.postValue(familyId);
                pendingCodeRequest.postValue(new Result.Success<>(invitation));
                android.util.Log.d("OnboardingViewModel", "Stored pending code request and familyId for later cancellation");
            } else if (result instanceof Result.Error) {
                // Convert error to match Result<Invitation> type
                Exception error = ((Result.Error<?>) result).getException();
                pendingCodeRequest.postValue(new Result.Error<>(error != null ? error : new Exception("Unknown error")));
            }
            // Post result to trigger navigation
            joinByCodeResult.postValue(result);
        });
    }

    /**
     * Cancel a pending code-request invitation previously created by this user.
     * This will delete the Invitation document under the target family.
     */
    public void cancelPendingCodeRequest(String familyId, Invitation invitation) {
        if (familyId == null || invitation == null || invitation.getId() == null) {
            android.util.Log.e("OnboardingViewModel", "Cannot cancel: familyId=" + familyId + ", invitation=" + invitation + ", invId=" + (invitation != null ? invitation.getId() : "null"));
            invitationAction.setValue(new Result.Error<>(new Exception("No pending request to cancel")));
            return;
        }
        android.util.Log.d("OnboardingViewModel", "Cancelling pending code request: invId=" + invitation.getId() + " familyId=" + familyId);
        lastAction = "cancelled";
        invitationAction.setValue(new Result.Loading<>());
        familyRepository.deleteInvitation(familyId, invitation.getId(), result -> {
            android.util.Log.d("OnboardingViewModel", "Delete invitation callback result: " + result);
            invitationAction.postValue(result);
        });
    }
}
