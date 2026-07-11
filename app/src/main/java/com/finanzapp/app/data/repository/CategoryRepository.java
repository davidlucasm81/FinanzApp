package com.finanzapp.app.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.finanzapp.app.data.firebase.FirestorePaths;
import com.finanzapp.app.data.model.Category;
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
        db.collection(FirestorePaths.getFamilyPath(familyId) + "/" + FirestorePaths.CATEGORIES)
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
}
