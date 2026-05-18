package com.recipebookpro.presentation.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.View;

import com.recipebookpro.presentation.ui.BaseActivity;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;

import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textview.MaterialTextView;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.recipebookpro.R;
import com.recipebookpro.domain.model.User;
import com.recipebookpro.presentation.ui.MainActivity;

import java.util.concurrent.Executors;

/**
 * LoginActivity -> user sign in
 */
public class LoginActivity extends BaseActivity {

    private static final String TAG = "GoogleSignIn";
    private static final String WEB_CLIENT_ID =
            "1071679993343-oscn2063e5v3rdv0qnu4t8937sd5pna8.apps.googleusercontent.com";

    private TextInputEditText etEmail;
    private TextInputEditText etPassword;
    private MaterialButton btnLogin;
    private MaterialButton btnGoogleLogin;
    private CircularProgressIndicator progressIndicator;
    private MaterialTextView tvRegisterLink;
    private MaterialTextView tvForgotPassword;
    private TextInputLayout tilEmail;
    private TextInputLayout tilPassword;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private CredentialManager credentialManager;
    private View rootView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        applyInsetsToView(findViewById(R.id.loginRoot));

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        credentialManager = CredentialManager.create(this);
        rootView = findViewById(R.id.loginRoot);

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnGoogleLogin = findViewById(R.id.btnGoogleLogin);
        progressIndicator = findViewById(R.id.progressLogin);
        tvRegisterLink = findViewById(R.id.tvRegisterLink);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);

        AuthLanguageSelector.setup(this);

        etEmail.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                String email = s.toString().trim();
                if (!TextUtils.isEmpty(email) && !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    tilEmail.setError(getString(R.string.invalid_email));
                } else {
                    tilEmail.setError(null);
                }
            }
        });

        etPassword.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                String pass = s.toString().trim();
                if (!TextUtils.isEmpty(pass) && pass.length() < 6) {
                    tilPassword.setError(getString(R.string.password_too_short));
                    tilPassword.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
                } else {
                    tilPassword.setError(null);
                    tilPassword.setErrorEnabled(false);
                    tilPassword.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
                }
            }
        });

        btnLogin.setOnClickListener(v -> loginUser());
        btnGoogleLogin.setOnClickListener(v -> startGoogleSignIn());
        tvForgotPassword.setOnClickListener(v -> handleForgotPassword());
        tvRegisterLink.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class)));

        // Handle 'Done' action on keyboard for password field
        etPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                loginUser();
                return true;
            }
            return false;
        });
    }

    private void hideKeyboard() {
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        android.view.View view = getCurrentFocus();
        if (view == null) view = new android.view.View(this);
        if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void loginUser() {
        hideKeyboard();
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";

        boolean hasError = false;
        tilEmail.setError(null);
        tilPassword.setError(null);
        tilPassword.setErrorEnabled(false);
        tilPassword.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);

        if (TextUtils.isEmpty(email)) {
            tilEmail.setError(getString(R.string.required_field));
            hasError = true;
        }
        if (TextUtils.isEmpty(password)) {
            tilPassword.setError(getString(R.string.required_field));
            tilPassword.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
            hasError = true;
        }

        if (hasError) return;

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError(getString(R.string.invalid_email));
            return;
        }

        setLoading(true);
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    setLoading(false);
                    if (task.isSuccessful()) {
                        goToMain();
                    } else {
                        showMessage(R.string.login_failed);
                    }
                });
    }

    private void startGoogleSignIn() {
        GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(WEB_CLIENT_ID)
                .setAutoSelectEnabled(false)
                .build();

        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build();

        setLoading(true);

        credentialManager.getCredentialAsync(
                this,
                request,
                new CancellationSignal(),
                Executors.newSingleThreadExecutor(),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        runOnUiThread(() -> handleCredential(result.getCredential()));
                    }

                    @Override
                    public void onError(GetCredentialException e) {
                        Log.e(TAG, "Credential request failed: " + e.getMessage(), e);
                        runOnUiThread(() -> {
                            setLoading(false);
                            showMessage(R.string.google_sign_in_failed);
                        });
                    }
                }
        );
    }

    private void handleCredential(Credential credential) {
        if (credential instanceof CustomCredential &&
                credential.getType().equals(GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)) {
            GoogleIdTokenCredential googleIdTokenCredential =
                    GoogleIdTokenCredential.createFrom(credential.getData());
            firebaseAuthWithGoogle(googleIdTokenCredential.getIdToken());
        } else {
            Log.e(TAG, "Unexpected credential type: " + credential.getType());
            setLoading(false);
            showMessage(R.string.google_sign_in_failed);
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        if (idToken == null) {
            setLoading(false);
            return;
        }

        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser firebaseUser = mAuth.getCurrentUser();
                        if (firebaseUser != null) {
                            saveUserToFirestoreIfNew(firebaseUser);
                        } else {
                            setLoading(false);
                            goToMain();
                        }
                    } else {
                        Log.e(TAG, "Firebase auth with Google failed", task.getException());
                        setLoading(false);
                        showMessage(R.string.google_sign_in_failed);
                    }
                });
    }

    private void saveUserToFirestoreIfNew(FirebaseUser firebaseUser) {
        String uid = firebaseUser.getUid();
        db.collection("users").document(uid).get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && !task.getResult().exists()) {
                        String email = firebaseUser.getEmail() != null ? firebaseUser.getEmail() : "";
                        String name = firebaseUser.getDisplayName() != null ? firebaseUser.getDisplayName() : "";
                        String photoUrl = firebaseUser.getPhotoUrl() != null ? firebaseUser.getPhotoUrl().toString() : "";
                        
                        User user = new User(uid, email, name, System.currentTimeMillis());
                        user.setProfileImageUrl(photoUrl);
                        db.collection("users").document(uid).set(user);
                    }
                    setLoading(false);
                    goToMain();
                });
    }

    private void handleForgotPassword() {
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        if (TextUtils.isEmpty(email)) {
            showMessage(R.string.invalid_email);
            return;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.forgot_password)
                .setMessage(R.string.send_reset_link_confirm)
                .setPositiveButton(R.string.send_reset_link, (dialog, which) -> {
                    setLoading(true);
                    mAuth.sendPasswordResetEmail(email)
                            .addOnCompleteListener(task -> {
                                setLoading(false);
                                if (task.isSuccessful()) {
                                    showMessage(R.string.reset_password_email_sent);
                                } else {
                                    String error = task.getException() != null ? task.getException().getMessage() : "";
                                    Snackbar.make(rootView, getString(R.string.error_with_reason, error), Snackbar.LENGTH_LONG).show();
                                }
                            });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void setLoading(boolean isLoading) {
        progressIndicator.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!isLoading);
        btnGoogleLogin.setEnabled(!isLoading);
    }

    private void showMessage(int messageRes) {
        Snackbar.make(rootView, messageRes, Snackbar.LENGTH_SHORT).show();
    }

    private void goToMain() {
        startActivity(new Intent(LoginActivity.this, MainActivity.class));
        finishAffinity();
    }
}
