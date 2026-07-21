package com.finanzapp.app.ui.settings;

import android.os.Bundle;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.finanzapp.app.FinanzAppApplication;
import com.finanzapp.app.data.model.User;
import com.finanzapp.app.util.Result;
import com.finanzapp.app.viewmodel.SettingsViewModel;
import com.finanzapp.app.viewmodel.ViewModelFactory;

public class SettingsFragment extends Fragment {
    private SettingsViewModel viewModel;
    private TextView tvName;
    private TextView tvEmail;
    private Button btnSignOut;
    private Button btnExportData;
    private Button btnDeleteAccount;
    private com.finanzapp.app.data.repository.FamilyRepository familyRepository;
    private User currentUser;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(com.finanzapp.app.R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FinanzAppApplication.AppContainer appContainer = ((FinanzAppApplication) requireActivity().getApplication()).getAppContainer();
        familyRepository = appContainer.getFamilyRepository();
        ViewModelFactory factory = new ViewModelFactory(appContainer.getAuthRepository(), familyRepository);
        viewModel = new ViewModelProvider(this, factory).get(SettingsViewModel.class);

        tvName = requireView().findViewById(com.finanzapp.app.R.id.tv_name);
        tvEmail = requireView().findViewById(com.finanzapp.app.R.id.tv_email);
        btnSignOut = requireView().findViewById(com.finanzapp.app.R.id.btn_sign_out);
        btnExportData = requireView().findViewById(com.finanzapp.app.R.id.btn_export_data);
        btnDeleteAccount = requireView().findViewById(com.finanzapp.app.R.id.btn_delete_account);

        btnSignOut.setOnClickListener(v -> {
            viewModel.signOut();
            Toast.makeText(requireContext(), "Sesión cerrada", Toast.LENGTH_SHORT).show();
            navigateToSplash();
        });

        btnExportData.setOnClickListener(v -> {
            btnExportData.setEnabled(false);
            viewModel.exportUserData();
        });

        btnDeleteAccount.setOnClickListener(v -> showDeleteConfirmation());

        setupObservers();
        viewModel.fetchUserData();
    }

    private void setupObservers() {
        viewModel.getUserData().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof Result.Success) {
                currentUser = ((Result.Success<User>) result).getData();
                tvName.setText(currentUser.getDisplayName() != null ? currentUser.getDisplayName() : "-");
                tvEmail.setText(currentUser.getEmail() != null ? currentUser.getEmail() : "-");
            } else if (result instanceof Result.Error) {
                // Only show error if we are not signing out
                if (getActivity() != null && !getActivity().isFinishing()) {
                    Toast.makeText(requireContext(), "Error al cargar datos de usuario", Toast.LENGTH_SHORT).show();
                }
            }
        });

        viewModel.getDeleteAccountResult().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof Result.Success) {
                Toast.makeText(requireContext(), "Cuenta borrada con éxito", Toast.LENGTH_SHORT).show();
                navigateToSplash();
            } else if (result instanceof Result.Error) {
                Exception e = ((Result.Error<?>) result).getException();
                android.util.Log.e("SettingsFragment", "Error al borrar cuenta", e);
                Toast.makeText(requireContext(), "Error al borrar cuenta", Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.getExportResult().observe(getViewLifecycleOwner(), result -> {
            btnExportData.setEnabled(true);
            if (result instanceof Result.Success) {
                String json = ((Result.Success<String>) result).getData();
                shareJsonFile(json);
            } else if (result instanceof Result.Error) {
                Toast.makeText(requireContext(), com.finanzapp.app.R.string.settings_export_error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void shareJsonFile(String json) {
        try {
            java.io.File cachePath = new java.io.File(requireContext().getCacheDir(), "exports");
            if (!cachePath.exists() && !cachePath.mkdirs()) {
                throw new java.io.IOException("Could not create cache directory");
            }
            java.io.File newFile = new java.io.File(cachePath, "my_data.json");
            java.io.FileOutputStream stream = new java.io.FileOutputStream(newFile);
            stream.write(json.getBytes());
            stream.close();

            android.net.Uri contentUri = androidx.core.content.FileProvider.getUriForFile(requireContext(), "com.finanzapp.app.fileprovider", newFile);

            if (contentUri != null) {
                android.content.Intent shareIntent = new android.content.Intent();
                shareIntent.setAction(android.content.Intent.ACTION_SEND);
                shareIntent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                shareIntent.setDataAndType(contentUri, "application/json");
                shareIntent.putExtra(android.content.Intent.EXTRA_STREAM, contentUri);
                startActivity(android.content.Intent.createChooser(shareIntent, "Descargar mis datos"));
            }
        } catch (java.io.IOException e) {
            android.util.Log.e("SettingsFragment", "Error sharing file", e);
            Toast.makeText(requireContext(), "Error al exportar archivo", Toast.LENGTH_SHORT).show();
        }
    }

    private void navigateToSplash() {
        android.content.Intent intent = new android.content.Intent(requireContext(), com.finanzapp.app.ui.SplashActivity.class);
        intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }

    private void showDeleteConfirmation() {
        EditText input = new EditText(requireContext());
        input.setHint(getString(com.finanzapp.app.R.string.delete_account_confirmation_phrase));

        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding, padding, 0);
        container.addView(input);

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(requireContext())
                .setTitle(com.finanzapp.app.R.string.delete_account_title)
                .setMessage(Html.fromHtml(getString(com.finanzapp.app.R.string.delete_account_message) + "<br><br>" +
                        getString(com.finanzapp.app.R.string.delete_account_verification_instruction), Html.FROM_HTML_MODE_LEGACY))
                .setView(container)
                .setPositiveButton(com.finanzapp.app.R.string.delete_button, (d, which) -> viewModel.deleteAccount(familyRepository))
                .setNegativeButton(com.finanzapp.app.R.string.cancel_button, null)
                .create();

        dialog.setOnShowListener(d -> {
            Button deleteBtn = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE);
            deleteBtn.setEnabled(false);

            input.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String phrase = getString(com.finanzapp.app.R.string.delete_account_confirmation_phrase);
                    deleteBtn.setEnabled(s.toString().equals(phrase));
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        });

        dialog.show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }
}