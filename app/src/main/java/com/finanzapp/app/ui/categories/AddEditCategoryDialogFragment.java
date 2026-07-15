package com.finanzapp.app.ui.categories;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.finanzapp.app.FinanzAppApplication;
import com.finanzapp.app.R;
import com.finanzapp.app.data.model.Category;
import com.finanzapp.app.util.Result;
import com.finanzapp.app.viewmodel.CategoryViewModel;
import com.finanzapp.app.viewmodel.ViewModelFactory;
import com.github.dhaval2404.colorpicker.ColorPickerDialog;
import com.github.dhaval2404.colorpicker.model.ColorShape;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;

public class AddEditCategoryDialogFragment extends DialogFragment {
    private static final String ARG_FAMILY_ID = "family_id";
    private static final String ARG_CATEGORY_ID = "category_id";
    private static final String ARG_CATEGORY_NAME = "category_name";
    private static final String ARG_CATEGORY_TYPE = "category_type";
    private static final String ARG_CATEGORY_COLOR = "category_color";

    private CategoryViewModel viewModel;
    private String familyId;
    private Category categoryToEdit;
    private String selectedColor = "#4CAF50"; // Default green
    private View viewSelectedColor;

    public static AddEditCategoryDialogFragment newInstance(String familyId, Category category) {
        AddEditCategoryDialogFragment fragment = new AddEditCategoryDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_FAMILY_ID, familyId);
        if (category != null) {
            args.putString(ARG_CATEGORY_ID, category.getId());
            args.putString(ARG_CATEGORY_NAME, category.getName());
            args.putString(ARG_CATEGORY_TYPE, category.getAppliesTo());
            args.putString(ARG_CATEGORY_COLOR, category.getColor());
        }
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            familyId = getArguments().getString(ARG_FAMILY_ID);
            String categoryId = getArguments().getString(ARG_CATEGORY_ID);
            if (categoryId != null) {
                categoryToEdit = new Category();
                categoryToEdit.setId(categoryId);
                categoryToEdit.setName(getArguments().getString(ARG_CATEGORY_NAME));
                categoryToEdit.setAppliesTo(getArguments().getString(ARG_CATEGORY_TYPE));
                categoryToEdit.setColor(getArguments().getString(ARG_CATEGORY_COLOR));
                categoryToEdit.setDefault(false);
                if (categoryToEdit.getColor() != null) {
                    selectedColor = categoryToEdit.getColor();
                }
            }
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View view = getLayoutInflater().inflate(R.layout.dialog_add_edit_category, null);

        TextView tvTitle = view.findViewById(R.id.tv_dialog_title);
        TextInputLayout tilName = view.findViewById(R.id.til_category_name);
        RadioButton rbExpense = view.findViewById(R.id.rb_expense);
        RadioButton rbIncome = view.findViewById(R.id.rb_income);
        viewSelectedColor = view.findViewById(R.id.view_selected_color);
        Button btnPickColor = view.findViewById(R.id.btn_pick_color);
        Button btnSave = view.findViewById(R.id.btn_save);
        Button btnCancel = view.findViewById(R.id.btn_cancel);

        updatePreviewColor();

        btnPickColor.setOnClickListener(v -> {
            new ColorPickerDialog.Builder(requireContext())
                    .setTitle("Color de la categoría")
                    .setColorShape(ColorShape.CIRCLE)
                    .setDefaultColor(selectedColor)
                    .setColorListener((color, colorHex) -> {
                        selectedColor = colorHex;
                        updatePreviewColor();
                    })
                    .show();
        });

        if (categoryToEdit != null) {
            tvTitle.setText("Editar Categoría");
            if (tilName.getEditText() != null) {
                tilName.getEditText().setText(categoryToEdit.getName());
            }
            if ("income".equals(categoryToEdit.getAppliesTo())) {
                rbIncome.setChecked(true);
            } else {
                rbExpense.setChecked(true);
            }
        }

        FinanzAppApplication.AppContainer appContainer = ((FinanzAppApplication) requireActivity().getApplication()).getAppContainer();
        ViewModelFactory factory = new ViewModelFactory(appContainer.getAuthRepository(), appContainer.getFamilyRepository(), 
                                                        appContainer.getAccountRepository(), appContainer.getCategoryRepository());
        viewModel = new ViewModelProvider(this, factory).get(CategoryViewModel.class);

        btnSave.setOnClickListener(v -> {
            String name = tilName.getEditText() != null ? tilName.getEditText().getText().toString().trim() : "";
            if (name.isEmpty()) {
                tilName.setError("Introduce un nombre");
                return;
            }

            String type = rbIncome.isChecked() ? "income" : "expense";
            String icon = rbIncome.isChecked() ? "ic_income" : "ic_expense";

            if (categoryToEdit == null) {
                Category newCategory = new Category(null, name, type, icon, selectedColor, false, FirebaseAuth.getInstance().getUid());
                viewModel.addCategory(familyId, newCategory);
            } else {
                categoryToEdit.setName(name);
                categoryToEdit.setAppliesTo(type);
                categoryToEdit.setColor(selectedColor);
                categoryToEdit.setIcon(icon);
                viewModel.updateCategory(familyId, categoryToEdit);
            }
        });

        btnCancel.setOnClickListener(v -> dismiss());

        viewModel.getOperationResult().observe(this, result -> {
            if (result instanceof Result.Success) {
                dismiss();
            } else if (result instanceof Result.Error) {
                Toast.makeText(requireContext(), "Error al guardar", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setView(view);
        return builder.create();
    }

    private void updatePreviewColor() {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        try {
            gd.setColor(Color.parseColor(selectedColor));
        } catch (Exception e) {
            gd.setColor(Color.GRAY);
        }
        viewSelectedColor.setBackground(gd);
    }
}
