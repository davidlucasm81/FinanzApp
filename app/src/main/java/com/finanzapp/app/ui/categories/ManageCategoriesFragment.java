package com.finanzapp.app.ui.categories;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.finanzapp.app.FinanzAppApplication;
import com.finanzapp.app.R;
import com.finanzapp.app.data.model.Category;
import com.finanzapp.app.data.model.User;
import com.finanzapp.app.util.Result;
import com.finanzapp.app.viewmodel.CategoryViewModel;
import com.finanzapp.app.viewmodel.ViewModelFactory;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.finanzapp.app.data.firebase.FirestorePaths;

import java.util.ArrayList;
import java.util.List;

public class ManageCategoriesFragment extends Fragment {
    private CategoryViewModel viewModel;
    private CategoryAdapter adapter;
    private String familyId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_manage_categories, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FinanzAppApplication.AppContainer appContainer = ((FinanzAppApplication) requireActivity().getApplication()).getAppContainer();
        ViewModelFactory factory = new ViewModelFactory(appContainer.getAuthRepository(), appContainer.getFamilyRepository(), 
                                                        appContainer.getAccountRepository(), appContainer.getCategoryRepository());
        viewModel = new ViewModelProvider(this, factory).get(CategoryViewModel.class);

        RecyclerView rvCategories = view.findViewById(R.id.rv_categories);
        FloatingActionButton fabAdd = view.findViewById(R.id.fab_add_category);

        adapter = new CategoryAdapter(new ArrayList<>(), this::onEditCategory, this::onDeleteCategory);
        rvCategories.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvCategories.setAdapter(adapter);

        fabAdd.setOnClickListener(v -> {
            if (familyId != null) {
                AddEditCategoryDialogFragment.newInstance(familyId, null)
                        .show(getChildFragmentManager(), "add_category");
            }
        });

        resolveFamilyId();
    }

    private void resolveFamilyId() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            FirebaseFirestore.getInstance().collection(FirestorePaths.USERS).document(uid).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        User user = documentSnapshot.toObject(User.class);
                        if (user != null && user.getFamilyId() != null) {
                            familyId = user.getFamilyId();
                            observeCategories();
                        }
                    });
        }
    }

    private void observeCategories() {
        viewModel.getCategories(familyId).observe(getViewLifecycleOwner(), categories -> {
            if (categories != null) {
                adapter.updateCategories(categories);
            }
        });

        viewModel.getOperationResult().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof Result.Error) {
                Exception e = ((Result.Error<?>) result).getException();
                if (e != null && "CATEGORY_IN_USE".equals(e.getMessage())) {
                    Toast.makeText(requireContext(), "No se puede borrar: la categoría está siendo usada en movimientos", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(requireContext(), "Error en la operación", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void onEditCategory(Category category) {
        AddEditCategoryDialogFragment.newInstance(familyId, category)
                .show(getChildFragmentManager(), "edit_category");
    }

    private void onDeleteCategory(Category category) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Borrar categoría")
                .setMessage("¿Estás seguro de que quieres borrar la categoría '" + category.getName() + "'?")
                .setPositiveButton("Borrar", (dialog, which) -> viewModel.deleteCategory(familyId, category.getId()))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private static class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.ViewHolder> {
        private final List<Category> categories;
        private final OnCategoryClickListener editListener;
        private final OnCategoryClickListener deleteListener;

        interface OnCategoryClickListener {
            void onClick(Category category);
        }

        CategoryAdapter(List<Category> categories, OnCategoryClickListener editListener, OnCategoryClickListener deleteListener) {
            this.categories = categories;
            this.editListener = editListener;
            this.deleteListener = deleteListener;
        }

        void updateCategories(List<Category> newCategories) {
            this.categories.clear();
            this.categories.addAll(newCategories);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Category category = categories.get(position);
            holder.tvName.setText(category.getName());
            String typeText;
            switch (category.getAppliesTo()) {
                case "income":
                    typeText = "Ingreso";
                    break;

                case "expense":
                    typeText = "Gasto";
                    break;

                case "both":
                    typeText = "Ingreso / Gasto";
                    break;

                default:
                    typeText = "-";
                    break;
            }

            holder.tvType.setText(typeText);
            
            int color = android.graphics.Color.parseColor(category.getColor() != null ? category.getColor() : "#808080");
            
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            gd.setColor(color);
            holder.viewColor.setBackground(gd);

            holder.btnDelete.setVisibility(View.VISIBLE);
            holder.btnEdit.setVisibility(View.VISIBLE);
            holder.btnEdit.setOnClickListener(v -> editListener.onClick(category));
            holder.btnDelete.setOnClickListener(v -> deleteListener.onClick(category));
        }

        @Override
        public int getItemCount() {
            return categories.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvType;
            View viewColor;
            ImageButton btnEdit, btnDelete;

            ViewHolder(View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tv_name);
                tvType = itemView.findViewById(R.id.tv_type);
                viewColor = itemView.findViewById(R.id.view_color);
                btnEdit = itemView.findViewById(R.id.btn_edit);
                btnDelete = itemView.findViewById(R.id.btn_delete);
            }
        }
    }
}
