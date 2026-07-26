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
import android.widget.Toast;

import java.util.Objects;
import com.finanzapp.app.R;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.finanzapp.app.FinanzAppApplication;
import com.finanzapp.app.data.model.User;
import com.finanzapp.app.databinding.FragmentSettingsBinding;
import com.finanzapp.app.util.Result;
import com.finanzapp.app.viewmodel.NotificationViewModel;
import com.finanzapp.app.viewmodel.SettingsViewModel;
import com.finanzapp.app.viewmodel.ViewModelFactory;

public class SettingsFragment extends Fragment {
    private FragmentSettingsBinding binding;
    private SettingsViewModel viewModel;
    private NotificationViewModel notificationViewModel;
    private com.finanzapp.app.data.repository.FamilyRepository familyRepository;
    private User currentUser;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FinanzAppApplication.AppContainer appContainer = ((FinanzAppApplication) requireActivity().getApplication()).getAppContainer();
        familyRepository = appContainer.getFamilyRepository();
        ViewModelFactory factory = new ViewModelFactory(appContainer);
        viewModel = new ViewModelProvider(this, factory).get(SettingsViewModel.class);
        notificationViewModel = new ViewModelProvider(requireActivity(), factory).get(NotificationViewModel.class);

        setupClickListeners();
        setupObservers();

        viewModel.fetchUserData();
    }

    private void setupClickListeners() {
        binding.btnSignOut.setOnClickListener(v -> {
            viewModel.signOut();
            Toast.makeText(requireContext(), R.string.msg_sign_out_success, Toast.LENGTH_SHORT).show();
            navigateToSplash();
        });

        binding.btnExportData.setOnClickListener(v -> {
            binding.btnExportData.setEnabled(false);
            viewModel.exportUserData();
        });

        binding.btnViewPrivacyPolicy.setOnClickListener(v -> {
            androidx.navigation.Navigation.findNavController(v)
                    .navigate(R.id.action_settingsFragment_to_privacyPolicyFragment);
        });

        binding.btnDeleteAccount.setOnClickListener(v -> showDeleteConfirmation());

        binding.switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) ->
                notificationViewModel.setNotificationsEnabled(isChecked));
    }

    private void setupObservers() {
        notificationViewModel.getNotificationsEnabled().observe(getViewLifecycleOwner(), enabled -> {
            if (binding.switchNotifications.isChecked() != enabled) {
                binding.switchNotifications.setChecked(enabled);
            }
        });

        viewModel.getUserData().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof Result.Success) {
                currentUser = ((Result.Success<User>) result).getData();
                binding.tvName.setText(currentUser.getDisplayName() != null ? currentUser.getDisplayName() : "-");
                binding.tvEmail.setText(currentUser.getEmail() != null ? currentUser.getEmail() : "-");
                
                if (currentUser.getPhotoUrl() != null && !currentUser.getPhotoUrl().isEmpty()) {
                    com.bumptech.glide.Glide.with(this)
                            .load(currentUser.getPhotoUrl())
                            .circleCrop()
                            .placeholder(R.drawable.ic_user_placeholder)
                            .into(binding.ivUserPhotoProfile);
                }
            } else if (result instanceof Result.Error) {
                if (getActivity() != null && !getActivity().isFinishing()) {
                    Toast.makeText(requireContext(), R.string.error_load_user, Toast.LENGTH_LONG).show();
                }
            }
        });

        viewModel.getDeleteAccountResult().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof Result.Success) {
                Toast.makeText(requireContext(), R.string.msg_account_deleted, Toast.LENGTH_SHORT).show();
                navigateToSplash();
            } else if (result instanceof Result.Error) {
                Exception e = ((Result.Error<?>) result).getException();
                android.util.Log.e("SettingsFragment", "Error al borrar cuenta", e);
                Toast.makeText(requireContext(), R.string.error_delete_account, Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.getExportResult().observe(getViewLifecycleOwner(), result -> {
            binding.btnExportData.setEnabled(true);
            if (result instanceof Result.Success) {
                String json = ((Result.Success<String>) result).getData();
                shareJsonFile(json);
            } else if (result instanceof Result.Error) {
                Toast.makeText(requireContext(), R.string.settings_export_error, Toast.LENGTH_SHORT).show();
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
            Toast.makeText(requireContext(), R.string.error_export_file, Toast.LENGTH_LONG).show();
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
        input.setHint(getString(R.string.delete_account_confirmation_phrase));

        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding, padding, 0);
        container.addView(input);

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete_account_title)
                .setMessage(Html.fromHtml(getString(R.string.delete_account_message) + "<br><br>" +
                        getString(R.string.delete_account_verification_instruction), Html.FROM_HTML_MODE_LEGACY))
                .setView(container)
                .setPositiveButton(R.string.delete_button, (d, which) -> viewModel.deleteAccount(familyRepository))
                .setNegativeButton(R.string.cancel_button, null)
                .create();

        dialog.setOnShowListener(d -> {
            Button deleteBtn = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE);
            deleteBtn.setEnabled(false);

            input.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String phrase = getString(R.string.delete_account_confirmation_phrase);
                    deleteBtn.setEnabled(Objects.equals(s.toString(), phrase));
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
        binding = null;
    }
}