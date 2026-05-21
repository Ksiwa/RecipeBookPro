package com.recipebookpro.presentation.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.view.LayoutInflater;
import android.os.CountDownTimer;
import android.app.AlertDialog;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Color;
import android.util.TypedValue;

import com.recipebookpro.presentation.ui.BaseActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textview.MaterialTextView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.android.material.textfield.TextInputLayout;
import com.recipebookpro.R;
import com.recipebookpro.domain.model.User;
import com.recipebookpro.presentation.ui.MainActivity;
import com.recipebookpro.util.EmailValidator;
import com.recipebookpro.util.EmailSender;

/**
 * RegisterActivity -> user registration
 */
public class RegisterActivity extends BaseActivity {

    private TextInputEditText etDisplayName;
    private TextInputEditText etEmail;
    private TextInputEditText etPassword;
    private TextInputEditText etConfirmPassword;
    private MaterialButton btnRegister;
    private CircularProgressIndicator progressIndicator;
    private MaterialTextView tvLoginLink;
    private TextInputLayout tilDisplayName;
    private TextInputLayout tilEmail;
    private TextInputLayout tilPassword;
    private TextInputLayout tilConfirmPassword;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private View rootView;
    private CountDownTimer countDownTimer;
    private String currentOtpCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        applyInsetsToView(findViewById(R.id.registerRoot));

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
        tilDisplayName = findViewById(R.id.tilDisplayName);
        tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        tilConfirmPassword = findViewById(R.id.tilConfirmPassword);

        AuthLanguageSelector.setup(this);

        setupRealTimeValidation();

        btnRegister.setOnClickListener(v -> registerUser());
        tvLoginLink.setOnClickListener(v -> finish());

        // Handle 'Done' action on keyboard for confirm password field
        etConfirmPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                registerUser();
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

    private void setupRealTimeValidation() {
        TextInputLayout tilDisplayName = findViewById(R.id.tilDisplayName);

        etDisplayName.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                String name = s.toString().trim();
                if (!TextUtils.isEmpty(name) && !name.matches("^[a-zA-ZğüşıöçĞÜŞİÖÇ\\s]+$")) {
                    tilDisplayName.setError(getString(R.string.invalid_name_format));
                } else {
                    tilDisplayName.setError(null);
                }
            }
        });

        etEmail.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                String email = s.toString().trim();
                if (!TextUtils.isEmpty(email)) {
                    EmailValidator.ValidationResult result = EmailValidator.validate(email);
                    if (result == EmailValidator.ValidationResult.INVALID_FORMAT) {
                        tilEmail.setError(getString(R.string.invalid_email));
                    } else if (result == EmailValidator.ValidationResult.DISPOSABLE_OR_FAKE) {
                        tilEmail.setError(getString(R.string.invalid_email_disposable));
                    } else {
                        tilEmail.setError(null);
                    }
                } else {
                    tilEmail.setError(null);
                }
            }
        });

        etPassword.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                String pass = s.toString().trim();
                if (!TextUtils.isEmpty(pass) && pass.length() < 6) {
                    tilPassword.setError(getString(R.string.password_too_short));
                    tilPassword.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
                } else {
                    tilPassword.setError(null);
                    tilPassword.setErrorEnabled(false);
                    tilPassword.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
                }
                // Also re-check confirm password if it's not empty
                String confirm = etConfirmPassword.getText().toString().trim();
                if (!TextUtils.isEmpty(confirm)) {
                    if (!pass.equals(confirm)) {
                        tilConfirmPassword.setError(getString(R.string.passwords_not_match));
                        tilConfirmPassword.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
                    } else {
                        tilConfirmPassword.setError(null);
                        tilConfirmPassword.setErrorEnabled(false);
                        tilConfirmPassword.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
                    }
                }
            }
        });

        etConfirmPassword.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                String confirm = s.toString().trim();
                String pass = etPassword.getText().toString().trim();
                if (!TextUtils.isEmpty(confirm) && !confirm.equals(pass)) {
                    tilConfirmPassword.setError(getString(R.string.passwords_not_match));
                    tilConfirmPassword.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
                } else {
                    tilConfirmPassword.setError(null);
                    tilConfirmPassword.setErrorEnabled(false);
                    tilConfirmPassword.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
                }
            }
        });
    }

    private void registerUser() {
        hideKeyboard();
        String displayName = etDisplayName.getText() != null ? etDisplayName.getText().toString().trim() : "";
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";
        String confirmPassword = etConfirmPassword.getText() != null ? etConfirmPassword.getText().toString().trim() : "";

        boolean hasError = false;
        tilDisplayName.setError(null);
        tilEmail.setError(null);
        tilPassword.setError(null);
        tilPassword.setErrorEnabled(false);
        tilPassword.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
        tilConfirmPassword.setError(null);
        tilConfirmPassword.setErrorEnabled(false);
        tilConfirmPassword.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);

        if (TextUtils.isEmpty(displayName)) {
            tilDisplayName.setError(getString(R.string.required_field));
            hasError = true;
        }
        if (TextUtils.isEmpty(email)) {
            tilEmail.setError(getString(R.string.required_field));
            hasError = true;
        }
        if (TextUtils.isEmpty(password)) {
            tilPassword.setError(getString(R.string.required_field));
            tilPassword.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
            hasError = true;
        }
        if (TextUtils.isEmpty(confirmPassword)) {
            tilConfirmPassword.setError(getString(R.string.required_field));
            tilConfirmPassword.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
            hasError = true;
        }

        if (hasError) return;

        EmailValidator.ValidationResult emailResult = EmailValidator.validate(email);
        if (emailResult == EmailValidator.ValidationResult.INVALID_FORMAT) {
            tilEmail.setError(getString(R.string.invalid_email));
            hasError = true;
        } else if (emailResult == EmailValidator.ValidationResult.DISPOSABLE_OR_FAKE) {
            tilEmail.setError(getString(R.string.invalid_email_disposable));
            hasError = true;
        }

        if (!displayName.matches("^[a-zA-ZğüşıöçĞÜŞİÖÇ\\s]+$")) {
            tilDisplayName.setError(getString(R.string.invalid_name_format));
            hasError = true;
        }

        if (password.length() < 6) {
            tilPassword.setError(getString(R.string.password_too_short));
            tilPassword.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
            hasError = true;
        }

        if (!password.equals(confirmPassword)) {
            tilConfirmPassword.setError(getString(R.string.passwords_not_match));
            tilConfirmPassword.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
            hasError = true;
        }

        if (hasError) return;

        setLoading(true);
        db.collection("users")
                .whereEqualTo("email", email)
                .get()
                .addOnCompleteListener(task -> {
                    setLoading(false);
                    if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                        tilEmail.setError(getString(R.string.error_email_already_registered));
                        Toast.makeText(RegisterActivity.this, R.string.error_email_already_registered, Toast.LENGTH_LONG).show();
                    } else {
                        showOtpVerificationDialog(email, password, displayName);
                    }
                });
    }

    private String generateOtpCode() {
        return String.format(java.util.Locale.US, "%06d", (int) (Math.random() * 900000 + 100000));
    }

    private void showOtpToast(String code) {
        String message = getString(R.string.otp_dev_toast, code);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        android.util.Log.d("OTP_VERIFICATION", "========================================");
        android.util.Log.d("OTP_VERIFICATION", "Verification Code: " + code);
        android.util.Log.d("OTP_VERIFICATION", "========================================");
    }

    private void sendVerificationEmail(final String email, final String code, final TextView tvSubtitle) {
        EmailSender.sendOtpEmail(email, code, new EmailSender.EmailSendListener() {
            @Override
            public void onSuccess(boolean isRealEmailSent) {
                if (isRealEmailSent) {
                    if (tvSubtitle != null) {
                        tvSubtitle.setText(getString(R.string.otp_subtitle, email));
                    }
                    Toast.makeText(RegisterActivity.this, R.string.otp_sent_success, Toast.LENGTH_SHORT).show();
                } else {
                    if (tvSubtitle != null) {
                        tvSubtitle.setText(getString(R.string.otp_subtitle, email));
                    }
                    showOtpToast(code);
                }
            }

            @Override
            public void onFailure(Exception e) {
                if (tvSubtitle != null) {
                    tvSubtitle.setText(getString(R.string.otp_subtitle, email));
                }
                Toast.makeText(RegisterActivity.this, getString(R.string.otp_send_failed, e.getMessage()), Toast.LENGTH_LONG).show();
                showOtpToast(code);
            }
        });
    }

    private int getThemeColor(int attr) {
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(attr, typedValue, true);
        return typedValue.data;
    }

    private void showOtpVerificationDialog(final String email, final String password, final String displayName) {
        currentOtpCode = generateOtpCode();

        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_otp_verification, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        builder.setCancelable(false);

        final AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.show();

        final EditText et1 = dialogView.findViewById(R.id.etOtpDigit1);
        final EditText et2 = dialogView.findViewById(R.id.etOtpDigit2);
        final EditText et3 = dialogView.findViewById(R.id.etOtpDigit3);
        final EditText et4 = dialogView.findViewById(R.id.etOtpDigit4);
        final EditText et5 = dialogView.findViewById(R.id.etOtpDigit5);
        final EditText et6 = dialogView.findViewById(R.id.etOtpDigit6);

        final TextView tvSubtitle = dialogView.findViewById(R.id.tvOtpSubtitle);
        tvSubtitle.setText(getString(R.string.otp_sending));

        TextView tvCountdown = dialogView.findViewById(R.id.tvOtpCountdown);
        final TextView btnResend = dialogView.findViewById(R.id.btnOtpResend);
        com.google.android.material.button.MaterialButton btnVerify = dialogView.findViewById(R.id.btnOtpVerify);
        com.google.android.material.button.MaterialButton btnCancel = dialogView.findViewById(R.id.btnOtpCancel);

        setupOtpDigitWatcher(et1, et2, null);
        setupOtpDigitWatcher(et2, et3, et1);
        setupOtpDigitWatcher(et3, et4, et2);
        setupOtpDigitWatcher(et4, et5, et3);
        setupOtpDigitWatcher(et5, et6, et4);
        setupOtpDigitWatcher(et6, null, et5);

        startOtpTimer(tvCountdown, btnResend, email, et1, et2, et3, et4, et5, et6);
        sendVerificationEmail(email, currentOtpCode, tvSubtitle);

        btnCancel.setOnClickListener(v -> {
            if (countDownTimer != null) {
                countDownTimer.cancel();
            }
            dialog.dismiss();
        });

        btnVerify.setOnClickListener(v -> {
            String enteredCode = et1.getText().toString().trim() +
                                 et2.getText().toString().trim() +
                                 et3.getText().toString().trim() +
                                 et4.getText().toString().trim() +
                                 et5.getText().toString().trim() +
                                 et6.getText().toString().trim();

            if (enteredCode.length() < 6) {
                Toast.makeText(RegisterActivity.this, R.string.empty_fields, Toast.LENGTH_SHORT).show();
                return;
            }

            if (currentOtpCode == null) {
                Toast.makeText(RegisterActivity.this, R.string.otp_expired, Toast.LENGTH_SHORT).show();
                return;
            }

            if (enteredCode.equals(currentOtpCode)) {
                if (countDownTimer != null) {
                    countDownTimer.cancel();
                }
                dialog.dismiss();
                performActualRegistration(email, password, displayName);
            } else {
                Toast.makeText(RegisterActivity.this, R.string.otp_incorrect, Toast.LENGTH_SHORT).show();
                et1.setError("");
                et2.setError("");
                et3.setError("");
                et4.setError("");
                et5.setError("");
                et6.setError("");
            }
        });
    }

    private void setupOtpDigitWatcher(final EditText current, final EditText next, final EditText previous) {
        current.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                if (s.length() == 1 && next != null) {
                    next.requestFocus();
                }
            }
        });

        current.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == android.view.KeyEvent.ACTION_DOWN && keyCode == android.view.KeyEvent.KEYCODE_DEL) {
                if (current.getText().length() == 0 && previous != null) {
                    previous.requestFocus();
                    previous.setText("");
                    return true;
                }
            }
            return false;
        });
    }

    private void startOtpTimer(TextView tvCountdown, TextView btnResend, String email,
                               final EditText et1, final EditText et2, final EditText et3,
                               final EditText et4, final EditText et5, final EditText et6) {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        btnResend.setEnabled(false);
        btnResend.setClickable(false);
        int outlineColor = getThemeColor(com.google.android.material.R.attr.colorOutline);
        btnResend.setTextColor(outlineColor);
        btnResend.setAlpha(0.6f);

        countDownTimer = new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long minutes = (millisUntilFinished / 1000) / 60;
                long seconds = (millisUntilFinished / 1000) % 60;
                tvCountdown.setText(getString(R.string.otp_countdown, minutes, seconds));
            }

            @Override
            public void onFinish() {
                tvCountdown.setText(getString(R.string.otp_expired));
                currentOtpCode = null; // Invalidate the code immediately upon expiration
                btnResend.setEnabled(true);
                btnResend.setClickable(true);
                btnResend.setAlpha(1.0f);
                int primaryColor = getThemeColor(com.google.android.material.R.attr.colorPrimary);
                btnResend.setTextColor(primaryColor);
            }
        }.start();

        btnResend.setOnClickListener(v -> {
            currentOtpCode = generateOtpCode();

            et1.setText("");
            et2.setText("");
            et3.setText("");
            et4.setText("");
            et5.setText("");
            et6.setText("");

            et1.setError(null);
            et2.setError(null);
            et3.setError(null);
            et4.setError(null);
            et5.setError(null);
            et6.setError(null);

            et1.requestFocus();

            final TextView tvSubtitle = ((View) tvCountdown.getParent()).findViewById(R.id.tvOtpSubtitle);
            if (tvSubtitle != null) {
                tvSubtitle.setText(getString(R.string.otp_sending));
            }
            sendVerificationEmail(email, currentOtpCode, tvSubtitle);

            startOtpTimer(tvCountdown, btnResend, email, et1, et2, et3, et4, et5, et6);
        });
    }

    private void performActualRegistration(String email, String password, String displayName) {
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

    @Override
    protected void onDestroy() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        super.onDestroy();
    }

    private void saveUserToFirestore(String uid, String email, String displayName) {
        User user = new User(uid, email, displayName, System.currentTimeMillis());

        db.collection("users").document(uid).set(user)
                .addOnCompleteListener(task -> {
                    setLoading(false);
                    if (task.isSuccessful()) {
                        Toast.makeText(RegisterActivity.this, R.string.register_success, Toast.LENGTH_LONG).show();
                        startActivity(MainActivity.createProfileStartIntent(RegisterActivity.this));
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
