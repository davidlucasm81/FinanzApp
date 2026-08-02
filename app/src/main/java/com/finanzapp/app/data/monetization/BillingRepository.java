package com.finanzapp.app.data.monetization;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.finanzapp.app.data.firebase.FirestorePaths;
import com.finanzapp.app.data.model.User;
import com.finanzapp.app.data.repository.AuthRepository;
import com.finanzapp.app.util.Result;
import com.finanzapp.app.util.SingleLiveEvent;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.revenuecat.purchases.CustomerInfo;
import com.revenuecat.purchases.Purchases;
import com.revenuecat.purchases.PurchasesConfiguration;
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback;

public class BillingRepository {
    public enum PremiumType {
        UNKNOWN,
        NONE,
        PURCHASED,
        WHITELIST
    }

    private final AuthRepository authRepository;
    private final FirebaseFirestore db;
    private final MutableLiveData<PremiumType> premiumTypeLiveData = new MutableLiveData<>(PremiumType.UNKNOWN);
    private final SingleLiveEvent<Result<Boolean>> restoreResultEvent = new SingleLiveEvent<>();
    private boolean isRevenueCatConfigured = false;

    private final Context context;

    public BillingRepository(Context context, AuthRepository authRepository) {
        this.context = context.getApplicationContext();
        this.authRepository = authRepository;
        this.db = FirebaseFirestore.getInstance();

        authRepository.getCurrentUser().observeForever(firebaseUser -> {
            if (firebaseUser != null) {
                // 1. Immediately check Whitelist (doesn't depend on RevenueCat)
                checkPremiumStatus(firebaseUser.getEmail());

                // The following logic will be executed inside setPremium() or checkPremiumStatus callbacks
                // once we know if the user is already Whitelist.
            } else {
                premiumTypeLiveData.setValue(PremiumType.NONE);
            }
        });
    }

    private void continueInitializationAfterWhitelist(com.google.firebase.auth.FirebaseUser firebaseUser, Context context) {
        if (premiumTypeLiveData.getValue() == PremiumType.WHITELIST) {
            android.util.Log.d("BillingRepo", "Skipping RevenueCat initialization for Whitelist user.");
            return;
        }

        // 2. RevenueCat Initialization (Lazy)
        ensureRevenueCatConfigured(context.getApplicationContext());

        if (isRevenueCatConfigured) {
            Purchases.getSharedInstance().logIn(firebaseUser.getUid(), new com.revenuecat.purchases.interfaces.LogInCallback() {
                @Override
                public void onReceived(@NonNull CustomerInfo customerInfo, boolean created) {
                    checkRevenueCatStatus();
                }

                @Override
                public void onError(@NonNull com.revenuecat.purchases.PurchasesError error) {
                    if (error.getCode() != com.revenuecat.purchases.PurchasesErrorCode.PurchaseNotAllowedError) {
                        android.util.Log.e("BillingRepo", "RC LogIn FAILED: " + error.getMessage());
                    }
                }
            });
        }
    }

    private void ensureRevenueCatConfigured(Context context) {
        if (isRevenueCatConfigured) return;

        String revenueCatApiKey = com.finanzapp.app.BuildConfig.REVENUECAT_API_KEY;
        if (revenueCatApiKey == null || revenueCatApiKey.isEmpty() || revenueCatApiKey.equals("goog_placeholder_api_key")) {
            android.util.Log.w("BillingRepo", "RevenueCat API Key is placeholder or empty. Skipping RC configuration.");
            return;
        }

        try {
            com.google.firebase.auth.FirebaseUser firebaseUser = authRepository.getCurrentUser().getValue();
            PurchasesConfiguration.Builder builder = new PurchasesConfiguration.Builder(context, revenueCatApiKey);
            if (firebaseUser != null) {
                builder.appUserID(firebaseUser.getUid());
            }
            Purchases.configure(builder.build());

            isRevenueCatConfigured = true;
            android.util.Log.d("BillingRepo", "RevenueCat configured successfully.");
        } catch (Exception e) {
            android.util.Log.e("BillingRepo", "Error configuring RevenueCat", e);
        }
    }

    public LiveData<PremiumType> getPremiumType() {
        return premiumTypeLiveData;
    }

    public LiveData<Boolean> getIsPremium() {
        return androidx.lifecycle.Transformations.map(premiumTypeLiveData, type -> {
            if (type == PremiumType.UNKNOWN) return null;
            return type != PremiumType.NONE;
        });
    }

    public SingleLiveEvent<Result<Boolean>> getRestoreResultEvent() {
        return restoreResultEvent;
    }

    public void checkPremiumStatus(String email) {
        android.util.Log.d("BillingRepo", "Checking premium for: " + email);
        // 1. Check Whitelist in Firestore
        if (email != null && !email.isEmpty()) {
            String normalizedEmail = email.toLowerCase().trim();
            db.collection(FirestorePaths.PREMIUM_WHITELIST).document(normalizedEmail).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            android.util.Log.d("BillingRepo", "Whitelist HIT! User is premium.");
                            setPremium(PremiumType.WHITELIST);
                        } else {
                            // 2. If not in whitelist, check RevenueCat
                            continueInitializationAfterWhitelist(authRepository.getCurrentUser().getValue(), context);
                        }
                    })
                    .addOnFailureListener(e -> {
                        // Fallback to RevenueCat
                        continueInitializationAfterWhitelist(authRepository.getCurrentUser().getValue(), context);
                    });
        } else {
            continueInitializationAfterWhitelist(authRepository.getCurrentUser().getValue(), context);
        }
    }

    private void checkRevenueCatStatus() {
        if (!isRevenueCatConfigured) {
            syncWithFirestoreUser();
            return;
        }

        Purchases.getSharedInstance().getCustomerInfo(new ReceiveCustomerInfoCallback() {
            @Override
            public void onReceived(@NonNull CustomerInfo customerInfo) {
                // Check if user has an active entitlement for lifetime premium
                boolean hasPurchased = !customerInfo.getEntitlements().getActive().isEmpty();
                
                if (hasPurchased) {
                    setPremium(PremiumType.PURCHASED);
                } else {
                    // If RC says no, check if we were already WHITELIST (from Firestore)
                    if (premiumTypeLiveData.getValue() != PremiumType.WHITELIST) {
                        setPremium(PremiumType.NONE);
                    }
                }
            }

            @Override
            public void onError(@NonNull com.revenuecat.purchases.PurchasesError error) {
                if (error.getCode() != com.revenuecat.purchases.PurchasesErrorCode.PurchaseNotAllowedError) {
                    android.util.Log.e("BillingRepo", "RC getCustomerInfo FAILED: " + error.getMessage());
                }
                syncWithFirestoreUser();
            }
        });
    }

    private void syncWithFirestoreUser() {
        if (authRepository.isLoggedIn()) {
            com.google.firebase.auth.FirebaseUser firebaseUser = authRepository.getCurrentUser().getValue();
            if (firebaseUser == null) {
                restoreResultEvent.setValue(new Result.Error<>(new Exception("No user logged in")));
                return;
            }
            String uid = firebaseUser.getUid();
            db.collection(FirestorePaths.USERS).document(uid).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        User user = documentSnapshot.toObject(User.class);
                        if (user != null) {
                            if (user.isPremium()) {
                                if (premiumTypeLiveData.getValue() == PremiumType.NONE) {
                                    premiumTypeLiveData.setValue(PremiumType.PURCHASED);
                                }
                                restoreResultEvent.setValue(new Result.Success<>(true));
                            } else {
                                premiumTypeLiveData.setValue(PremiumType.NONE);
                                restoreResultEvent.setValue(new Result.Success<>(false));
                            }
                        } else {
                            restoreResultEvent.setValue(new Result.Error<>(new Exception("User data not found")));
                        }
                    })
                    .addOnFailureListener(e -> restoreResultEvent.setValue(new Result.Error<>(e)));
        } else {
            restoreResultEvent.setValue(new Result.Error<>(new Exception("Not logged in")));
        }
    }

    private void setPremium(PremiumType type) {
        PremiumType current = premiumTypeLiveData.getValue();
        if (current == type) {
            // Even if no change, we might be here as a result of a restore operation
            if (type == PremiumType.WHITELIST || type == PremiumType.PURCHASED) {
                restoreResultEvent.setValue(new Result.Success<>(true));
            } else if (type == PremiumType.NONE) {
                restoreResultEvent.setValue(new Result.Success<>(false));
            }
            return; // No change in state
        }

        premiumTypeLiveData.setValue(type);
        
        // Notify restore result
        if (type != PremiumType.UNKNOWN) {
            restoreResultEvent.setValue(new Result.Success<>(type != PremiumType.NONE));
        }

        if (authRepository.isLoggedIn()) {
            boolean isPremium = type != PremiumType.NONE;
            com.google.firebase.auth.FirebaseUser firebaseUser = authRepository.getCurrentUser().getValue();
            if (firebaseUser == null) return;
            String uid = firebaseUser.getUid();
            db.collection(FirestorePaths.USERS).document(uid)
                    .update("isPremium", isPremium, "premiumUpdatedAt", Timestamp.now())
                    .addOnFailureListener(e -> android.util.Log.e("BillingRepo", "Failed to sync premium status to Firestore", e));
        }
    }

    public void restorePurchases() {
        if (!isRevenueCatConfigured) {
            syncWithFirestoreUser();
            return;
        }

        Purchases.getSharedInstance().restorePurchases(new ReceiveCustomerInfoCallback() {
            @Override
            public void onReceived(@NonNull CustomerInfo customerInfo) {
                boolean hasPurchased = !customerInfo.getEntitlements().getActive().isEmpty();
                if (hasPurchased) {
                    setPremium(PremiumType.PURCHASED);
                } else {
                    // Fallback to whitelist check if RevenueCat says no
                    com.google.firebase.auth.FirebaseUser user = authRepository.getCurrentUser().getValue();
                    if (user != null) {
                        checkPremiumStatus(user.getEmail());
                    } else {
                        setPremium(PremiumType.NONE);
                    }
                }
            }

            @Override
            public void onError(@NonNull com.revenuecat.purchases.PurchasesError error) {
                // Fallback to whitelist check on error too
                com.google.firebase.auth.FirebaseUser user = authRepository.getCurrentUser().getValue();
                if (user != null) {
                    checkPremiumStatus(user.getEmail());
                }
            }
        });
    }
}
