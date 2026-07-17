package com.finanzapp.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.finanzapp.app.data.model.Category;
import com.finanzapp.app.data.repository.CategoryRepository;
import com.finanzapp.app.util.Result;
import com.finanzapp.app.util.SingleLiveEvent;

import java.util.List;

public class CategoryViewModel extends ViewModel {
    private final CategoryRepository repository;
    private final SingleLiveEvent<Result<Boolean>> operationResult = new SingleLiveEvent<>();

    public CategoryViewModel(CategoryRepository repository) {
        this.repository = repository;
    }

    public LiveData<List<Category>> getCategories(String familyId) {
        return repository.getCategories(familyId);
    }

    public LiveData<Result<Boolean>> getOperationResult() {
        return operationResult;
    }

    public void addCategory(String familyId, Category category) {
        repository.addCategory(familyId, category, operationResult::setValue);
    }

    public void updateCategory(String familyId, Category category) {
        repository.updateCategory(familyId, category, operationResult::setValue);
    }

    public void deleteCategory(String familyId, String categoryId) {
        repository.deleteCategory(familyId, categoryId, operationResult::setValue);
    }
}
