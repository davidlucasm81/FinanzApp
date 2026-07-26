package com.finanzapp.app.data.repository;

import com.finanzapp.app.data.firebase.FirestorePaths;
import com.finanzapp.app.data.model.Notification;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

public class NotificationRepository {
    private final FirebaseFirestore db;

    public NotificationRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    public interface OnNotificationReceivedListener {
        void onNotificationReceived(Notification notification);
    }

    public ListenerRegistration listenToNotifications(String familyId, OnNotificationReceivedListener listener) {
        // Listen to notifications created in the last minute to avoid old pop-ups
        Timestamp oneMinuteAgo = new Timestamp(System.currentTimeMillis() / 1000 - 60, 0);

        return db.collection(FirestorePaths.getNotificationsPath(familyId))
                .whereGreaterThan("createdAt", oneMinuteAgo)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) {
                        return;
                    }
                    // Only process new documents added in the last snapshot
                    for (QueryDocumentSnapshot doc : value) {
                        // We check if it was recently added
                        if (doc.getMetadata().hasPendingWrites()) continue; // Ignore local changes

                        Notification notification = doc.toObject(Notification.class);
                        if (notification.getId() == null) {
                            notification.setId(doc.getId());
                        }
                        listener.onNotificationReceived(notification);
                        // Usually we only want the most recent one if multiple arrive at once
                        break; 
                    }
                });
    }

    public void addNotification(String familyId, Notification notification) {
        DocumentReference ref = db.collection(FirestorePaths.getNotificationsPath(familyId)).document();
        notification.setId(ref.getId());
        ref.set(notification);
    }
}
