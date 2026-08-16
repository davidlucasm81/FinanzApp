package com.finanzapp.app.data.repository;

import androidx.lifecycle.LiveData;

import com.finanzapp.app.data.firebase.FirestorePaths;
import com.finanzapp.app.data.model.Category;
import com.finanzapp.app.util.FirebaseLogger;
import com.finanzapp.app.util.FirestoreLiveData;
import com.finanzapp.app.util.Result;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.perf.metrics.Trace;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CategoryRepository {
    private final FirebaseFirestore db;

    // Listeners activos: stopListening() los desconecta todos antes de invalidar la sesión.
    private final List<ListenerRegistration> activeListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    private final Map<String, LiveData<List<Category>>> categoriesCache = new ConcurrentHashMap<>();

    public CategoryRepository(AuthRepository authRepository) {
        this.db = FirebaseFirestore.getInstance();
        authRepository.registerPreSignOutCleanup(this::clearCache);
    }

    private void clearCache() {
        categoriesCache.clear();
        stopListening();
    }

    public void stopListening() {
        for (ListenerRegistration reg : activeListeners) {
            reg.remove();
        }
        activeListeners.clear();
    }

    public LiveData getCategories(String familyId) {
        if (categoriesCache.containsKey(familyId)) {
            return categoriesCache.get(familyId);
        }
        Query query = db.collection(FirestorePaths.getCategoriesPath(familyId))
                .orderBy("name");

        FirestoreLiveData<Category> liveData = new FirestoreLiveData<>(query, Category.class, true);
        categoriesCache.put(familyId, (LiveData) liveData);
        return (LiveData) liveData;
    }

    public void addCategory(String familyId, Category category, Callback callback) {
        Trace trace = FirebaseLogger.startTrace("repo_add_category");
        String path = FirestorePaths.getCategoriesPath(familyId);
        db.collection(path).add(category)
                .addOnSuccessListener(documentReference -> {
                    category.setId(documentReference.getId());
                    documentReference.update("id", category.getId())
                            .addOnSuccessListener(aVoid -> {
                                callback.onResult(new Result.Success<>(true));
                                FirebaseLogger.stopTrace(trace);
                            })
                            .addOnFailureListener(e -> {
                                callback.onResult(new Result.Error<>(e));
                                FirebaseLogger.stopTrace(trace);
                            });
                })
                .addOnFailureListener(e -> {
                    callback.onResult(new Result.Error<>(e));
                    FirebaseLogger.stopTrace(trace);
                });
    }

    public void updateCategory(String familyId, Category category, Callback callback) {
        Trace trace = FirebaseLogger.startTrace("repo_update_category");
        db.collection(FirestorePaths.getCategoriesPath(familyId)).document(category.getId())
                .set(category)
                .addOnSuccessListener(aVoid -> {
                    callback.onResult(new Result.Success<>(true));
                    FirebaseLogger.stopTrace(trace);
                })
                .addOnFailureListener(e -> {
                    callback.onResult(new Result.Error<>(e));
                    FirebaseLogger.stopTrace(trace);
                });
    }

    public void deleteCategory(String familyId, String categoryId, Callback callback) {
        Trace trace = FirebaseLogger.startTrace("repo_delete_category");
        db.collection(FirestorePaths.getFamilyPath(familyId) + "/" + FirestorePaths.TRANSACTIONS)
                .whereEqualTo("categoryId", categoryId)
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        callback.onResult(new Result.Error<>(new Exception("CATEGORY_IN_USE")));
                        FirebaseLogger.stopTrace(trace);
                    } else {
                        db.collection(FirestorePaths.getCategoriesPath(familyId)).document(categoryId)
                                .delete()
                                .addOnSuccessListener(aVoid -> {
                                    callback.onResult(new Result.Success<>(true));
                                    FirebaseLogger.stopTrace(trace);
                                })
                                .addOnFailureListener(e -> {
                                    callback.onResult(new Result.Error<>(e));
                                    FirebaseLogger.stopTrace(trace);
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    callback.onResult(new Result.Error<>(e));
                    FirebaseLogger.stopTrace(trace);
                });
    }

    public interface Callback {
        void onResult(Result<Boolean> result);
    }
}