package com.finanzapp.app.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.finanzapp.app.MainActivity;
import com.finanzapp.app.R;
import com.finanzapp.app.data.firebase.FirestorePaths;
import com.finanzapp.app.data.model.User;
import com.finanzapp.app.ui.auth.LoginActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import com.finanzapp.app.ui.onboarding.OnboardingActivity;
import com.finanzapp.app.util.FirebaseLogger;
import com.finanzapp.app.data.model.FamilyMembership;
import com.finanzapp.app.data.model.Member;
import com.google.firebase.Timestamp;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {
    private static final String TAG = "SplashActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        androidx.core.splashscreen.SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        
        FirebaseUser currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            navigateToLogin();
        } else {
            FirebaseLogger.setUserId(currentUser.getUid());
            checkPrivacyPolicy(currentUser);
        }
    }

    private void checkPrivacyPolicy(FirebaseUser firebaseUser) {
        // Timeout mechanism: if no response in 5 seconds, fallback to checkUserStatus directly
        // to avoid getting stuck on Splash screen.
        final boolean[] handled = {false};
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (!handled[0]) {
                android.util.Log.w(TAG, "checkPrivacyPolicy: Timeout reached! Proceeding to checkUserStatus as fallback.");
                handled[0] = true;
                checkUserStatus(firebaseUser);
            }
        }, 5000);

        // Usamos Source.SERVER para asegurar que Splash compruebe siempre el estado más reciente
        // y evitar loops por caché local tras aceptar la política.
        FirebaseFirestore.getInstance().collection(FirestorePaths.USERS)
                .document(firebaseUser.getUid())
                .get(com.google.firebase.firestore.Source.SERVER)
                .addOnSuccessListener(documentSnapshot -> {
                    if (handled[0]) return;
                    handled[0] = true;
                    if (documentSnapshot.exists()) {
                        Timestamp acceptedAt = documentSnapshot.getTimestamp("privacyPolicyAcceptedAt");
                        if (acceptedAt == null) {
                            navigateToPrivacyConsent();
                        } else {
                            // Una vez aceptada la política, comprobamos el estado de familias/memberships
                            checkUserStatus(firebaseUser);
                        }
                    } else {
                        navigateToLogin();
                    }
                })
                .addOnFailureListener(e -> {
                    FirebaseLogger.logException(e);
                    if (handled[0]) return;
                    // Si falla el servidor (ej. offline), intentamos con la caché como fallback
                    FirebaseFirestore.getInstance().collection(FirestorePaths.USERS)
                            .document(firebaseUser.getUid())
                            .get(com.google.firebase.firestore.Source.CACHE)
                            .addOnSuccessListener(doc -> {
                                if (handled[0]) return;
                                handled[0] = true;
                                if (doc.exists() && doc.getTimestamp("privacyPolicyAcceptedAt") != null) {
                                    checkUserStatus(firebaseUser);
                                } else {
                                    // Si no hay rastro de la aceptación ni en caché ni en server,
                                    // por seguridad pedimos consentimiento.
                                    navigateToPrivacyConsent();
                                }
                            })
                            .addOnFailureListener(e2 -> {
                                FirebaseLogger.logException(e2);
                                if (handled[0]) return;
                                handled[0] = true;
                                navigateToLogin();
                            });
                });
    }

    private void checkUserStatus(FirebaseUser firebaseUser) {
        // Phase 7 bis: Self-heal migration
        FirebaseFirestore.getInstance().collection(FirestorePaths.getMembershipsPath(firebaseUser.getUid()))
                .get()
                .addOnSuccessListener(memberships -> {
                    if (memberships.isEmpty()) {
                        // Check if user has a familyId but no memberships
                        performSelfHeal(firebaseUser);
                    } else {
                        // Phase 18: Check if memberships are missing the 'mode' field
                        boolean needsRepair = false;
                        for (com.google.firebase.firestore.DocumentSnapshot doc : memberships.getDocuments()) {
                            if (doc.get("mode") == null) {
                                needsRepair = true;
                                break;
                            }
                        }

                        if (needsRepair) {
                            repairMemberships(firebaseUser.getUid(), memberships.getDocuments(), 0);
                        } else {
                            // User already has memberships, proceed normally
                            proceedWithRouting(firebaseUser);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    FirebaseLogger.logException(e);
                    navigateToLogin();
                });
    }

    private void repairMemberships(String uid, java.util.List<com.google.firebase.firestore.DocumentSnapshot> docs, int index) {
        if (index >= docs.size()) {
            proceedWithRouting(com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser());
            return;
        }

        com.google.firebase.firestore.DocumentSnapshot doc = docs.get(index);
        if (doc.get("mode") != null) {
            repairMemberships(uid, docs, index + 1);
            return;
        }

        String familyId = doc.getId();
        FirebaseFirestore.getInstance().collection(FirestorePaths.FAMILIES).document(familyId).get()
                .addOnSuccessListener(familyDoc -> {
                    if (familyDoc.exists()) {
                        String mode = familyDoc.getString("mode");
                        if (mode == null) mode = "normal";
                        
                        FirebaseFirestore.getInstance().collection(FirestorePaths.getMembershipsPath(uid)).document(familyId)
                                .update("mode", mode)
                                .addOnCompleteListener(task -> repairMemberships(uid, docs, index + 1));
                    } else {
                        repairMemberships(uid, docs, index + 1);
                    }
                })
                .addOnFailureListener(e -> repairMemberships(uid, docs, index + 1));
    }

    private void performSelfHeal(FirebaseUser firebaseUser) {
        FirebaseFirestore.getInstance().collection(FirestorePaths.USERS)
                .document(firebaseUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String familyId = documentSnapshot.getString("familyId");
                        if (familyId != null) {
                            // User has a familyId, migrate it to memberships
                            migrateToMembership(firebaseUser.getUid(), familyId);
                        } else {
                            // No familyId and no memberships, go to onboarding/invitations
                            checkPendingInvitations(firebaseUser.getEmail());
                        }
                    } else {
                        navigateToLogin();
                    }
                })
                .addOnFailureListener(e -> {
                    FirebaseLogger.logException(e);
                    navigateToLogin();
                });
    }

    private void migrateToMembership(String uid, String familyId) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        // Fetch family name and member role
        db.collection(FirestorePaths.FAMILIES).document(familyId).get().addOnSuccessListener(familyDoc -> {
            if (familyDoc.exists()) {
                String familyName = familyDoc.getString("name");
                String mode = familyDoc.getString("mode");
                db.collection(FirestorePaths.getMembersPath(familyId)).document(uid).get().addOnSuccessListener(memberDoc -> {
                    if (memberDoc.exists()) {
                        Member member = memberDoc.toObject(Member.class);
                        if (member != null) {
                            FamilyMembership membership = new FamilyMembership(
                                    familyId,
                                    familyName,
                                    member.getRole(),
                                    mode,
                                    member.getJoinedAt() != null ? member.getJoinedAt() : Timestamp.now()
                            );
                            
                            db.collection(FirestorePaths.getMembershipsPath(uid))
                                    .document(familyId)
                                    .set(membership)
                                    .addOnSuccessListener(v -> navigateToMain())
                                    .addOnFailureListener(e -> {
                                        FirebaseLogger.logException(e);
                                        navigateToMain(); // Navigate anyway to avoid blocking
                                    });
                        } else {
                            navigateToMain();
                        }
                    } else {
                        navigateToMain();
                    }
                }).addOnFailureListener(e -> {
                    FirebaseLogger.logException(e);
                    navigateToMain();
                });
            } else {
                navigateToMain();
            }
        }).addOnFailureListener(e -> {
            FirebaseLogger.logException(e);
            navigateToMain();
        });
    }

    private void proceedWithRouting(FirebaseUser firebaseUser) {
        FirebaseFirestore.getInstance().collection(FirestorePaths.USERS)
                .document(firebaseUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        User user = documentSnapshot.toObject(User.class);
                        if (user != null) {
                            String activeFamilyId = user.getFamilyId();
                            
                            // Photo Migration (Self-Heal): 
                            // If user has a photo but we don't know if the Member doc has it, 
                            // we update it in the active family.
                            if (user.getPhotoUrl() != null && activeFamilyId != null) {
                                String uid = firebaseUser.getUid();
                                String photoUrl = user.getPhotoUrl();
                                FirebaseFirestore.getInstance()
                                        .collection(FirestorePaths.getMembersPath(activeFamilyId))
                                        .document(uid)
                                        .get()
                                        .addOnSuccessListener(memberDoc -> {
                                            if (memberDoc.exists()) {
                                                String currentMemberPhoto = memberDoc.getString("photoUrl");
                                                if (currentMemberPhoto == null || !currentMemberPhoto.equals(photoUrl)) {
                                                    memberDoc.getReference().update("photoUrl", photoUrl);
                                                }
                                            }
                                        });
                            }

                            if (activeFamilyId != null) {
                                // Check if the active familyId is still valid (exists in memberships)
                                validateActiveFamily(firebaseUser.getUid(), activeFamilyId);
                            } else {
                                // activeFamilyId is null, but we have memberships, so pick the first one
                                setFirstFamilyActive(firebaseUser.getUid());
                            }
                        } else {
                            navigateToLogin();
                        }
                    } else {
                        navigateToLogin();
                    }
                })
                .addOnFailureListener(e -> {
                    FirebaseLogger.logException(e);
                    navigateToLogin();
                });
    }

    private void validateActiveFamily(String uid, String activeFamilyId) {
        FirebaseFirestore.getInstance().document(FirestorePaths.getMembershipPath(uid, activeFamilyId))
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        navigateToMain();
                    } else {
                        // User was expelled or the family was deleted, set another one active
                        setFirstFamilyActive(uid);
                    }
                })
                .addOnFailureListener(e -> {
                    FirebaseLogger.logException(e);
                    navigateToMain();
                });
    }

    private void setFirstFamilyActive(String uid) {
        FirebaseFirestore.getInstance().collection(FirestorePaths.getMembershipsPath(uid))
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        String firstFamilyId = querySnapshot.getDocuments().get(0).getId();
                        FirebaseFirestore.getInstance().collection(FirestorePaths.USERS).document(uid)
                                .update("familyId", firstFamilyId)
                                .addOnSuccessListener(v -> navigateToMain())
                                .addOnFailureListener(e -> {
                                    FirebaseLogger.logException(e);
                                    navigateToMain();
                                });
                    } else {
                        // No memberships after all? (should not happen here), go to onboarding
                        navigateToOnboarding();
                    }
                })
                .addOnFailureListener(e -> {
                    FirebaseLogger.logException(e);
                    navigateToOnboarding();
                });
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
                .addOnFailureListener(e -> {
                    FirebaseLogger.logException(e);
                    navigateToOnboarding();
                });
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
                .addOnFailureListener(e -> {
                    FirebaseLogger.logException(e);
                    navigateToOnboarding();
                });
    }

    private void navigateToLogin() {
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    private void navigateToOnboarding() {
        startActivity(new Intent(this, OnboardingActivity.class));
        finish();
    }

    private void navigateToPrivacyConsent() {
        Intent intent = new Intent(this, OnboardingActivity.class);
        intent.putExtra("show_privacy_consent", true);
        startActivity(intent);
        finish();
    }

    private void navigateToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
