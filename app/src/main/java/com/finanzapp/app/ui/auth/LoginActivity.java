package com.finanzapp.app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.lifecycle.ViewModelProvider;

import com.finanzapp.app.FinanzAppApplication;
import com.finanzapp.app.databinding.ActivityLoginBinding;
import com.finanzapp.app.util.Result;
import com.finanzapp.app.viewmodel.AuthViewModel;
import com.finanzapp.app.viewmodel.ViewModelFactory;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.GoogleAuthProvider;

public class LoginActivity extends AppCompatActivity {
    private static final String TAG = "LoginActivity";
    private ActivityLoginBinding binding;
    private AuthViewModel viewModel;
    private CredentialManager credentialManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        FinanzAppApplication.AppContainer container = ((FinanzAppApplication) getApplication()).getAppContainer();
        ViewModelFactory factory = new ViewModelFactory(container.getAuthRepository(), container.getFamilyRepository());
        viewModel = new ViewModelProvider(this, factory).get(AuthViewModel.class);

        credentialManager = CredentialManager.create(this);

        setupObservers();
        setupListeners();
    }

    private void setupObservers() {
        viewModel.getAuthResult().observe(this, result -> {
            if (result instanceof Result.Loading) {
                Log.d(TAG, "Auth status: Loading");
                showLoading(true);
            } else if (result instanceof Result.Success) {
                Log.d(TAG, "Auth status: Success");
                showLoading(false);
                navigateToMain();
            } else if (result instanceof Result.Error) {
                Log.d(TAG, "Auth status: Error");
                showLoading(false);
                Exception e = ((Result.Error<?>) result).getException();
                Log.e(TAG, "Auth error", e);
                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setupListeners() {
        binding.btnGoogleLogin.setOnClickListener(v -> loginWithGoogle());
    }

    private void loginWithGoogle() {
        // NOTE: In a real scenario, you'd get the Web Client ID from your google-services.json or Firebase Console.
        // For now, we assume it's correctly configured in the Firebase project.
        // String webClientId = getString(R.string.default_web_client_id); 
        
        // As an AI, I can't read the actual google-services.json content if it's not provided or accessible.
        // But the user said they did the manual actions.
        
        GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId("308062806250-m4ce2tv57f8dq6nf6vtqhb2pb0ojvtkq.apps.googleusercontent.com")
                .build();

        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build();

        credentialManager.getCredentialAsync(this, request, null, ContextCompat.getMainExecutor(this), new androidx.credentials.CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
            @Override
            public void onResult(GetCredentialResponse result) {
                Log.d(TAG, "CredentialManager onResult");
                Credential credential = result.getCredential();
                String idToken = null;

                if (credential instanceof GoogleIdTokenCredential) {
                    Log.d(TAG, "Credential is GoogleIdTokenCredential");
                    idToken = ((GoogleIdTokenCredential) credential).getIdToken();
                } else if (credential instanceof androidx.credentials.CustomCredential &&
                        credential.getType().equals(GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)) {
                    try {
                        Log.d(TAG, "Credential is CustomCredential (Google ID Token)");
                        GoogleIdTokenCredential googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.getData());
                        idToken = googleIdTokenCredential.getIdToken();
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing Google ID Token from CustomCredential", e);
                    }
                }

                if (idToken != null) {
                    AuthCredential firebaseCredential = GoogleAuthProvider.getCredential(idToken, null);
                    viewModel.loginWithCredential(firebaseCredential);
                } else {
                    Log.w(TAG, "Credential is NOT a valid Google ID Token. Type: " + credential.getType());
                    Toast.makeText(LoginActivity.this, "Error: No se pudo obtener el token de Google", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(GetCredentialException e) {
                Log.e(TAG, "Credential Manager error", e);
                if (!(e instanceof androidx.credentials.exceptions.GetCredentialCancellationException)) {
                    Toast.makeText(LoginActivity.this, "Error de Google: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void showLoading(boolean loading) {
        binding.pbLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnGoogleLogin.setEnabled(!loading);
    }

    private void navigateToMain() {
        Intent intent = new Intent(this, com.finanzapp.app.ui.SplashActivity.class);
        startActivity(intent);
        finish();
    }
}
