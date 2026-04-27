package com.recipebookpro.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.core.widget.NestedScrollView;
import android.graphics.Rect;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textview.MaterialTextView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.recipebookpro.R;
import com.recipebookpro.model.User;
import com.recipebookpro.ui.auth.LoginActivity;

import java.util.ArrayList;
import java.util.List;

public class ProfileFragment extends Fragment {

    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private ChipGroup chipGroupAllergens;
    private TextInputEditText etCustomAllergen;
    private List<String> userAllergens = new ArrayList<>();
    private boolean isLoading = true;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        MaterialTextView tvEmail = view.findViewById(R.id.tvProfileEmail);
        MaterialButton btnLogout = view.findViewById(R.id.btnLogout);
        MaterialButton btnSettings = view.findViewById(R.id.btnSettings);
        MaterialButton btnAdmin = view.findViewById(R.id.btnAdminPanel);
        chipGroupAllergens = view.findViewById(R.id.chipGroupAllergens);
        etCustomAllergen = view.findViewById(R.id.etCustomAllergen);

        etCustomAllergen.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                String custom = etCustomAllergen.getText() != null
                        ? etCustomAllergen.getText().toString().trim() : "";
                if (!custom.isEmpty()) {
                    addCustomAllergenChip(custom);
                    etCustomAllergen.setText("");
                }
                return true;
            }
            return false;
        });

        if (currentUser != null) {
            tvEmail.setText(currentUser.getEmail());
        }

        btnLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(requireContext(), LoginActivity.class));
            requireActivity().finishAffinity();
        });

        btnSettings.setOnClickListener(v -> { });
        btnAdmin.setOnClickListener(v -> { });

        loadUserAllergens();

        NestedScrollView nsvProfile = view.findViewById(R.id.nsvProfile);

        // Robust keyboard detection using spacer
        final View keyboardSpacer = view.findViewById(R.id.keyboardSpacer);
        view.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (!isAdded()) return;
            Rect r = new Rect();
            view.getWindowVisibleDisplayFrame(r);
            int screenHeight = view.getRootView().getHeight();
            int keypadHeight = screenHeight - r.bottom;

            if (keyboardSpacer != null) {
                android.view.ViewGroup.LayoutParams params = keyboardSpacer.getLayoutParams();
                if (keypadHeight > screenHeight * 0.15) {
                    params.height = keypadHeight;
                } else {
                    params.height = 0;
                }
                keyboardSpacer.setLayoutParams(params);
            }
        });

        // Focus listener to scroll to focused field
        nsvProfile.getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> {
            if (newFocus != null && (newFocus instanceof android.widget.EditText || newFocus instanceof android.widget.AutoCompleteTextView)) {
                nsvProfile.postDelayed(() -> {
                    if (!isAdded()) return;
                    int[] viewPos = new int[2];
                    newFocus.getLocationOnScreen(viewPos);
                    int[] scrollPos = new int[2];
                    nsvProfile.getLocationOnScreen(scrollPos);
                    // Position the view about 300px below the top (more centered)
                    int relativeTop = viewPos[1] - scrollPos[1];
                    nsvProfile.smoothScrollBy(0, relativeTop - 50);
                }, 200);
            }
        });
    }

    private void loadUserAllergens() {
        if (currentUser == null) return;

        db.collection("users").document(currentUser.getUid()).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        User user = doc.toObject(User.class);
                        if (user != null && user.getAllergens() != null) {
                            userAllergens = new ArrayList<>(user.getAllergens());
                        }
                    }
                    populateAllergenChips();
                })
                .addOnFailureListener(e -> populateAllergenChips());
    }

    private void populateAllergenChips() {
        chipGroupAllergens.removeAllViews();
        String[] allergenTags = getResources().getStringArray(R.array.allergen_tags);

        List<String> defaultTags = new ArrayList<>();
        for (String t : allergenTags) defaultTags.add(t.toLowerCase());

        isLoading = true;
        for (String tag : allergenTags) {
            addAllergenChipInternal(tag, false);
        }

        for (String userAllergen : userAllergens) {
            boolean isDefault = false;
            for (String def : allergenTags) {
                if (def.equalsIgnoreCase(userAllergen)) { isDefault = true; break; }
            }
            if (!isDefault) {
                addAllergenChipInternal(userAllergen, true);
            }
        }
        isLoading = false;
    }

    private void addAllergenChipInternal(String tag, boolean isCustom) {
        Chip chip = new Chip(requireContext());
        chip.setText(tag);
        chip.setCheckable(true);
        chip.setCheckedIconVisible(true);
        if (isCustom) {
            chip.setCloseIconVisible(true);
            chip.setOnCloseIconClickListener(v -> {
                chipGroupAllergens.removeView(chip);
                onAllergenChanged();
            });
        }

        boolean isSelected = false;
        for (String userAllergen : userAllergens) {
            if (userAllergen.equalsIgnoreCase(tag)) {
                isSelected = true;
                break;
            }
        }
        chip.setChecked(isSelected);

        chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isLoading) {
                onAllergenChanged();
            }
        });

        chipGroupAllergens.addView(chip);
    }

    private void addCustomAllergenChip(String text) {
        for (int i = 0; i < chipGroupAllergens.getChildCount(); i++) {
            Chip existing = (Chip) chipGroupAllergens.getChildAt(i);
            if (existing.getText().toString().equalsIgnoreCase(text)) return;
        }

        isLoading = true;
        Chip chip = new Chip(requireContext());
        chip.setText(text);
        chip.setCheckable(true);
        chip.setCheckedIconVisible(true);
        chip.setChecked(true);
        chip.setCloseIconVisible(true);
        chip.setOnCloseIconClickListener(v -> {
            chipGroupAllergens.removeView(chip);
            onAllergenChanged();
        });
        chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isLoading) {
                onAllergenChanged();
            }
        });
        chipGroupAllergens.addView(chip);
        isLoading = false;
        onAllergenChanged();
    }

    private void onAllergenChanged() {
        if (currentUser == null) return;

        List<String> selected = new ArrayList<>();
        for (int i = 0; i < chipGroupAllergens.getChildCount(); i++) {
            Chip chip = (Chip) chipGroupAllergens.getChildAt(i);
            if (chip.isChecked()) {
                selected.add(chip.getText().toString());
            }
        }

        userAllergens = selected;
        db.collection("users").document(currentUser.getUid())
                .update("allergens", selected);
    }
}
