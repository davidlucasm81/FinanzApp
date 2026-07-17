package com.finanzapp.app.ui.transactions;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.finanzapp.app.databinding.FragmentImportTransactionsBinding;
import com.finanzapp.app.viewmodel.ImportTransactionsViewModel;

public class ImportTransactionsFragment extends Fragment {

    private FragmentImportTransactionsBinding binding;
    private ImportTransactionsViewModel viewModel;
    private String familyId;

    private final ActivityResultLauncher<Intent> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        viewModel.importFromUri(familyId, uri);
                    }
                }
            });

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            familyId = getArguments().getString("familyId");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentImportTransactionsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(ImportTransactionsViewModel.class);

        setupUI();
        observeViewModel();
    }

    private void setupUI() {
        binding.toolbar.setNavigationOnClickListener(v -> Navigation.findNavController(v).navigateUp());
        binding.btnSelectFile.setOnClickListener(v -> openFilePicker());
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        String[] mimeTypes = {"text/csv", "text/comma-separated-values", "text/tab-separated-values", "text/plain"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        filePickerLauncher.launch(intent);
    }

    private void observeViewModel() {
        viewModel.getLoading().observe(getViewLifecycleOwner(), loading -> {
            binding.loadingOverlay.setVisibility(loading ? View.VISIBLE : View.GONE);
            binding.btnSelectFile.setEnabled(!loading);
        });

        viewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
            }
        });

        viewModel.getImportResult().observe(getViewLifecycleOwner(), result -> {
            if (result != null) {
                binding.layoutResults.setVisibility(View.VISIBLE);
                binding.txtImportedCount.setText("Movimientos importados: " + result.getImportedCount());
                binding.txtNewAccountsCount.setText("Cuentas nuevas creadas: " + result.getNewAccountsCount());
                binding.txtNewCategoriesCount.setText("Categorías nuevas creadas: " + result.getNewCategoriesCount());

                if (!result.getErrors().isEmpty()) {
                    binding.labelErrors.setVisibility(View.VISIBLE);
                    binding.txtErrors.setVisibility(View.VISIBLE);
                    StringBuilder sb = new StringBuilder();
                    for (String err : result.getErrors()) {
                        sb.append("• ").append(err).append("\n");
                    }
                    binding.txtErrors.setText(sb.toString().trim());
                } else {
                    binding.labelErrors.setVisibility(View.GONE);
                    binding.txtErrors.setVisibility(View.GONE);
                    Toast.makeText(requireContext(), "Importación completada con éxito", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
