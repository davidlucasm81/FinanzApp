package com.finanzapp.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.finanzapp.app.data.firebase.FirestorePaths;
import com.finanzapp.app.data.model.Notification;
import com.finanzapp.app.data.model.User;
import com.finanzapp.app.data.repository.AuthRepository;
import com.finanzapp.app.data.repository.NotificationRepository;
import com.finanzapp.app.util.SingleLiveEvent;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.Objects;

public class NotificationViewModel extends ViewModel {
    private final AuthRepository authRepository;
    private final NotificationRepository notificationRepository;

    private final SingleLiveEvent<Notification> notificationEvent = new SingleLiveEvent<>();
    private final MutableLiveData<Boolean> notificationsEnabled = new MutableLiveData<>(true);

    private ListenerRegistration userListener;
    private ListenerRegistration notificationListener;
    private String currentFamilyId;
    private String currentUserId;

    // Referencia estable para poder registrar/desregistrar el mismo Runnable
    private final Runnable signOutCleanup = this::stopListening;

    public NotificationViewModel(AuthRepository authRepository, NotificationRepository notificationRepository) {
        this.authRepository = authRepository;
        this.notificationRepository = notificationRepository;
        authRepository.registerPreSignOutCleanup(signOutCleanup);

        FirebaseUser firebaseUser = authRepository.getCurrentUser().getValue();
        if (firebaseUser != null) {
            currentUserId = firebaseUser.getUid();
            listenToUser();
        }
    }

    public LiveData<Notification> getNotificationEvent() {
        return notificationEvent;
    }

    public LiveData<Boolean> getNotificationsEnabled() {
        return notificationsEnabled;
    }

    private void listenToUser() {
        if (currentUserId == null) return;

        userListener = FirebaseFirestore.getInstance().collection(FirestorePaths.USERS).document(currentUserId)
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) return;

                    User user = value.toObject(User.class);
                    if (user != null) {
                        String newFamilyId = user.getFamilyId();
                        if (newFamilyId != null && !Objects.equals(newFamilyId, currentFamilyId)) {
                            currentFamilyId = newFamilyId;
                            setupNotificationListener();
                        } else if (newFamilyId == null) {
                            stopNotificationListener();
                            currentFamilyId = null;
                        }
                    }
                });
    }

    private void setupNotificationListener() {
        stopNotificationListener();
        if (currentFamilyId == null) return;

        notificationListener = notificationRepository.listenToNotifications(currentFamilyId, notification -> {
            Boolean enabled = notificationsEnabled.getValue();
            // Don't notify the creator of the notification
            if (enabled != null && enabled && !Objects.equals(notification.getCreatedBy(), currentUserId)) {
                notificationEvent.postValue(notification);
            }
        });
    }

    private void stopNotificationListener() {
        if (notificationListener != null) {
            notificationListener.remove();
            notificationListener = null;
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        authRepository.unregisterPreSignOutCleanup(signOutCleanup);
        stopListening();
    }

    public void setNotificationsEnabled(boolean enabled) {
        notificationsEnabled.setValue(enabled);
    }

    /**
     * Debe llamarse explícitamente al cerrar sesión (no basta con onCleared()),
     * ya que este ViewModel está scopeado a la Activity y sigue vivo mientras esta
     * termina, provocando PERMISSION_DENIED en Firestore tras el signOut de FirebaseAuth.
     */
    public void stopListening() {
        stopNotificationListener();
        if (userListener != null) {
            userListener.remove();
            userListener = null;
        }
        currentFamilyId = null;
    }
}