package com.finanzapp.app.util;

import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.firebase.perf.FirebasePerformance;
import com.google.firebase.perf.metrics.Trace;

/**
 * Centralized utility for Firebase observability (Crashlytics and Performance).
 */
public class FirebaseLogger {

    public static void logException(Throwable throwable) {
        if (throwable != null) {
            FirebaseCrashlytics.getInstance().recordException(throwable);
        }
    }

    public static void logMessage(String message) {
        if (message != null) {
            FirebaseCrashlytics.getInstance().log(message);
        }
    }

    public static void setUserId(String userId) {
        FirebaseCrashlytics.getInstance().setUserId(userId != null ? userId : "");
    }

    public static void setCustomKey(String key, String value) {
        FirebaseCrashlytics.getInstance().setCustomKey(key, value != null ? value : "null");
    }

    public static void setCustomKey(String key, boolean value) {
        FirebaseCrashlytics.getInstance().setCustomKey(key, value);
    }

    public static void setCustomKey(String key, int value) {
        FirebaseCrashlytics.getInstance().setCustomKey(key, value);
    }

    public static Trace startTrace(String traceName) {
        Trace trace = FirebasePerformance.getInstance().newTrace(traceName);
        trace.start();
        return trace;
    }

    public static void stopTrace(Trace trace) {
        if (trace != null) {
            trace.stop();
        }
    }
}
