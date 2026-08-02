package com.finanzapp.app.data.monetization;

import android.app.Activity;
import android.content.Context;
import com.google.android.gms.ads.MobileAds;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

import java.util.concurrent.atomic.AtomicBoolean;

public class AdsRepository {
    private final AtomicBoolean isMobileAdsInitializeCalled = new AtomicBoolean(false);
    private ConsentInformation consentInformation;

    public AdsRepository() {
    }

    public void init(Activity activity, ConsentResultCallback callback) {
        ConsentRequestParameters params = new ConsentRequestParameters.Builder()
                .setTagForUnderAgeOfConsent(false)
                .build();

        consentInformation = UserMessagingPlatform.getConsentInformation(activity);
        consentInformation.requestConsentInfoUpdate(
                activity,
                params,
                () -> {
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(
                            activity,
                            loadAndShowError -> {
                                if (loadAndShowError != null) {
                                    // Consent gathering failed.
                                }

                                if (consentInformation.canRequestAds()) {
                                    initializeMobileAdsSdk(activity);
                                }
                                callback.onConsentResult(consentInformation.canRequestAds());
                            }
                    );
                },
                requestConsentError -> {
                    // Consent gathering failed.
                    if (consentInformation.canRequestAds()) {
                        initializeMobileAdsSdk(activity);
                    }
                    callback.onConsentResult(consentInformation.canRequestAds());
                });

        // Check if you can initialize the Mobile Ads SDK in parallel
        if (consentInformation.canRequestAds()) {
            initializeMobileAdsSdk(activity);
        }
    }

    private void initializeMobileAdsSdk(Context context) {
        if (isMobileAdsInitializeCalled.getAndSet(true)) {
            return;
        }

        // Initialize the Mobile Ads SDK.
        MobileAds.initialize(context);
    }

    public interface ConsentResultCallback {
        void onConsentResult(boolean canRequestAds);
    }
}
