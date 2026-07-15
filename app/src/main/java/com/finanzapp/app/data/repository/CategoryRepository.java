package com.finanzapp.app.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.finanzapp.app.data.firebase.FirestorePaths;
import com.finanzapp.app.data.model.Category;
import com.finanzapp.app.util.Result;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class CategoryRepository {
    private final FirebaseFirestore db;

    public CategoryRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    public LiveData<List<Category>> getCategories(String familyId) {
        MutableLiveData<List<Category>> categoriesLiveData = new MutableLiveData<>();
        db.collection(FirestorePaths.getCategoriesPath(familyId))
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) {
                        return;
                    }
                    List<Category> categories = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : value) {
                        categories.add(doc.toObject(Category.class));
                    }
                    categoriesLiveData.setValue(categories);
                });
        return categoriesLiveData;
    }

    public void addCategory(String familyId, Category category, Callback callback) {
        String path = FirestorePaths.getCategoriesPath(familyId);
        db.collection(path).add(category)
                .addOnSuccessListener(documentReference -> {
                    category.setId(documentReference.getId());
                    documentReference.update("id", category.getId())
                            .addOnSuccessListener(aVoid -> callback.onResult(new Result.Success<>(true)))
                            .addOnFailureListener(e -> callback.onResult(new Result.Error<>(e)));
                })
                .addOnFailureListener(e -> callback.onResult(new Result.Error<>(e)));
    }

    public void updateCategory(String familyId, Category category, Callback callback) {
        db.collection(FirestorePaths.getCategoriesPath(familyId)).document(category.getId())
                .set(category)
                .addOnSuccessListener(aVoid -> callback.onResult(new Result.Success<>(true)))
                .addOnFailureListener(e -> callback.onResult(new Result.Error<>(e)));
    }

    public void deleteCategory(String familyId, String categoryId, Callback callback) {
        db.collection(FirestorePaths.getFamilyPath(familyId) + "/" + FirestorePaths.TRANSACTIONS)
                .whereEqualTo("categoryId", categoryId)
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        callback.onResult(new Result.Error<>(new Exception("CATEGORY_IN_USE")));
                    } else {
                        db.collection(FirestorePaths.getCategoriesPath(familyId)).document(categoryId)
                                .delete()
                                .addOnSuccessListener(aVoid -> callback.onResult(new Result.Success<>(true)))
                                .addOnFailureListener(e -> callback.onResult(new Result.Error<>(e)));
                    }
                })
                .addOnFailureListener(e -> callback.onResult(new Result.Error<>(e)));
    }

    public interface Callback {
        void onResult(Result<Boolean> result);
    }
}
