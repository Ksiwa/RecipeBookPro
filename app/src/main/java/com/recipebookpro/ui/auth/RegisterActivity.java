package com.recipebookpro.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textview.MaterialTextView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.recipebookpro.R;
import com.recipebookpro.model.User;
import com.recipebookpro.ui.MainActivity;

/**
 * RegisterActivity -> user registration
 */
public class RegisterActivity extends AppCompatActivity {

    private TextInputEditText etDisplayName;
    private TextInputEditText etEmail;
    private TextInputEditText etPassword;
    private TextInputEditText etConfirmPassword;
    private MaterialButton btnRegister;
    private CircularProgressIndicator progressIndicator;
    private MaterialTextView tvLoginLink;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private View rootView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        rootView = findViewById(R.id.registerRoot);

        etDisplayName = findViewById(R.id.etDisplayName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnRegister = findViewById(R.id.btnRegister);
        progressIndicator = findViewById(R.id.progressRegister);
        tvLoginLink = findViewById(R.id.tvLoginLink);

        btnRegister.setOnClickListener(v -> registerUser());
        tvLoginLink.setOnClickListener(v -> finish());
    }

    private void registerUser() {
        String displayName = etDisplayName.getText() != null ? etDisplayName.getText().toString().trim() : "";
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";
        String confirmPassword = etConfirmPassword.getText() != null ? etConfirmPassword.getText().toString().trim() : "";

        if (TextUtils.isEmpty(displayName) || TextUtils.isEmpty(email)
                || TextUtils.isEmpty(password) || TextUtils.isEmpty(confirmPassword)) {
            showMessage(R.string.empty_fields);
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showMessage(R.string.invalid_email);
            return;
        }

        if (password.length() < 6) {
            showMessage(R.string.password_too_short);
            return;
        }

        if (!password.equals(confirmPassword)) {
            showMessage(R.string.passwords_not_match);
            return;
        }

        setLoading(true);

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser firebaseUser = mAuth.getCurrentUser();
                        if (firebaseUser != null) {
                            saveUserToFirestore(firebaseUser.getUid(), email, displayName);
                        } else {
                            setLoading(false);
                            showMessage(R.string.register_failed);
                        }
                    } else {
                        setLoading(false);
                        showMessage(R.string.register_failed);
                    }
                });
    }

    private void saveUserToFirestore(String uid, String email, String displayName) {
        User user = new User(uid, email, displayName, System.currentTimeMillis());

        db.collection("users").document(uid).set(user)
                .addOnCompleteListener(task -> {
                    setLoading(false);
                    if (task.isSuccessful()) {
                        startActivity(new Intent(RegisterActivity.this, MainActivity.class));
                        finishAffinity();
                    } else {
                        showMessage(R.string.register_failed);
                    }
                });
    }

    private void setLoading(boolean isLoading) {
        progressIndicator.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        btnRegister.setEnabled(!isLoading);
    }

    private void showMessage(int messageRes) {
        Snackbar.make(rootView, messageRes, Snackbar.LENGTH_SHORT).show();
    }
}
