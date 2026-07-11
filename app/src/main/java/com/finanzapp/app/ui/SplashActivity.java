package com.finanzapp.app.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.finanzapp.app.FinanzAppApplication;
import com.finanzapp.app.MainActivity;
import com.finanzapp.app.data.firebase.FirestorePaths;
import com.finanzapp.app.data.model.User;
import com.finanzapp.app.data.repository.AuthRepository;
import com.finanzapp.app.ui.auth.LoginActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import com.finanzapp.app.ui.onboarding.OnboardingActivity;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        AuthRepository authRepository = ((FinanzAppApplication) getApplication()).getAppContainer().getAuthRepository();
        
        authRepository.getCurrentUser().observe(this, firebaseUser -> {
            if (firebaseUser == null) {
                navigateToLogin();
            } else {
                checkUserStatus(firebaseUser);
            }
        });
    }

    private void checkUserStatus(FirebaseUser firebaseUser) {
        FirebaseFirestore.getInstance().collection(FirestorePaths.USERS)
                .document(firebaseUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        User user = documentSnapshot.toObject(User.class);
                        if (user != null && user.getFamilyId() != null) {
                            navigateToMain();
                        } else {
                            checkPendingInvitations(firebaseUser.getEmail());
                        }
                    } else {
                        navigateToLogin();
                    }
                })
                .addOnFailureListener(e -> navigateToLogin());
    }

    private void checkPendingInvitations(String email) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            navigateToLogin();
            return;
        }

        // Check for email invitations
        FirebaseFirestore.getInstance().collectionGroup(FirestorePaths.INVITATIONS)
                .whereEqualTo("type", "email_invite")
                .whereEqualTo("targetEmail", email)
                .whereEqualTo("status", "pending")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        navigateToOnboarding();
                    } else {
                        // Check for pending code requests
                        checkPendingCodeRequests(currentUser.getUid());
                    }
                })
                .addOnFailureListener(e -> navigateToOnboarding());
    }

    private void checkPendingCodeRequests(String uid) {
        FirebaseFirestore.getInstance().collectionGroup(FirestorePaths.INVITATIONS)
                .whereEqualTo("type", "code_request")
                .whereEqualTo("requestedByUid", uid)
                .whereEqualTo("status", "pending")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    // In both cases we go to OnboardingActivity, 
                    // and WelcomeFragment will decide the specific Fragment
                    navigateToOnboarding();
                })
                .addOnFailureListener(e -> navigateToOnboarding());
    }

    private void navigateToLogin() {
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    private void navigateToOnboarding() {
        startActivity(new Intent(this, OnboardingActivity.class));
        finish();
    }

    private void navigateToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
