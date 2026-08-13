package com.finanzapp.app.data.repository;

import com.finanzapp.app.data.firebase.FirestorePaths;
import com.finanzapp.app.data.model.Settlement;
import com.finanzapp.app.util.Result;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class SettlementRepository {
    private final FirebaseFirestore db;
    private final FirebaseAuth auth;
    private final List<ListenerRegistration> activeListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    public SettlementRepository(AuthRepository authRepository) {
        this.db = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
        authRepository.registerPreSignOutCleanup(this::stopListening);
    }

    public void stopListening() {
        for (ListenerRegistration reg : activeListeners) {
            reg.remove();
        }
        activeListeners.clear();
    }

    public void addSettlement(String familyId, String fromUid, String toUid, double amount, String note, SettlementCallback callback) {
        String uid = auth.getUid();
        if (uid == null) return;

        DocumentReference docRef = db.collection(FirestorePaths.getSettlementsPath(familyId)).document();
        Settlement settlement = new Settlement(
                docRef.getId(),
                fromUid,
                toUid,
                amount,
                note,
                uid,
                Timestamp.now()
        );

        docRef.set(settlement)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        callback.onResult(new Result.Success<>(settlement));
                    } else {
                        callback.onResult(new Result.Error<>(task.getException()));
                    }
                });
    }

    public ListenerRegistration getSettlements(String familyId, SettlementsCallback callback) {
        ListenerRegistration reg = db.collection(FirestorePaths.getSettlementsPath(familyId))
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) {
                        callback.onResult(new Result.Error<>(error != null ? error : new Exception("Empty result")));
                        return;
                    }
                    List<Settlement> settlements = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : value) {
                        settlements.add(doc.toObject(Settlement.class));
                    }
                    callback.onResult(new Result.Success<>(settlements));
                });
        activeListeners.add(reg);
        return reg;
    }

    public void deleteSettlement(String familyId, String settlementId, ApproveCallback callback) {
        db.collection(FirestorePaths.getSettlementsPath(familyId)).document(settlementId)
                .delete()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        callback.onResult(new Result.Success<>(true));
                    } else {
                        callback.onResult(new Result.Error<>(task.getException()));
                    }
                });
    }

    public interface SettlementCallback {
        void onResult(Result<Settlement> result);
    }

    public interface SettlementsCallback {
        void onResult(Result<List<Settlement>> result);
    }

    public interface ApproveCallback {
        void onResult(Result<Boolean> result);
    }
}
