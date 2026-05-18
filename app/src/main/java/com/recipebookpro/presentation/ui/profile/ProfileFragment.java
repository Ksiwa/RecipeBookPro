package com.recipebookpro.presentation.ui.profile;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.Manifest;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.os.Environment;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
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
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.recipebookpro.R;
import com.recipebookpro.domain.model.LocalizedText;
import com.recipebookpro.domain.model.User;
import com.recipebookpro.presentation.ui.LocaleHelper;
import com.recipebookpro.util.BilingualTextHelper;
import com.recipebookpro.presentation.ui.auth.LoginActivity;

import coil.Coil;
import coil.request.ImageRequest;
import coil.transform.CircleCropTransformation;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import android.net.Uri;
import android.widget.ImageView;
import android.widget.Toast;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class ProfileFragment extends Fragment {

    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private ChipGroup chipGroupHealthConditions;
    private ChipGroup chipGroupCustomHealthConditions;
    private TextInputEditText etCustomHealthCondition;
    private ImageView ivProfileAvatar;
    private List<String> userHealthConditions = new ArrayList<>();
    private List<LocalizedText> userCustomHealthConditionsI18n = new ArrayList<>();
    private final java.util.Set<String> activeCustomHealthKeys = new java.util.LinkedHashSet<>();
    private java.util.Map<String, java.util.List<String>> userHealthTriggers = new java.util.HashMap<>();
    private java.util.Map<String, String> userHealthWarningTemplates = new java.util.HashMap<>();
    private boolean isLoading = true;
    private Uri cameraImageUri;
    private String lastDisplayedUiLang = "";

    private static final java.util.Map<Integer, String> CHIP_CONDITION_MAP = new java.util.LinkedHashMap<>();
    static {
        CHIP_CONDITION_MAP.put(R.id.chipDiabetes, "diabetes");
        CHIP_CONDITION_MAP.put(R.id.chipKidney, "kidney_disease");
        CHIP_CONDITION_MAP.put(R.id.chipCardiovascular, "cardiovascular");
        CHIP_CONDITION_MAP.put(R.id.chipHypertension, "hypertension");
        CHIP_CONDITION_MAP.put(R.id.chipCeliac, "celiac");
        CHIP_CONDITION_MAP.put(R.id.chipIbs, "ibs");
    }

    private final ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            result -> {
                boolean cameraGranted = Boolean.TRUE.equals(result.get(Manifest.permission.CAMERA));
                String galleryPermission = getGalleryPermission();
                boolean galleryGranted = galleryPermission == null
                        || Boolean.TRUE.equals(result.get(galleryPermission));

                if (cameraGranted && galleryGranted) {
                    showImageSourceChooser();
                    return;
                }

                if (!cameraGranted) {
                    Toast.makeText(requireContext(), R.string.permission_denied_camera, Toast.LENGTH_SHORT).show();
                }
                if (!galleryGranted) {
                    Toast.makeText(requireContext(), R.string.permission_denied_gallery, Toast.LENGTH_SHORT).show();
                }
            }
    );

    private final ActivityResultLauncher<String> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    uploadProfileImage(uri);
                }
            }
    );

    private final ActivityResultLauncher<Uri> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicture(),
            success -> {
                if (success && cameraImageUri != null) {
                    uploadProfileImage(cameraImageUri);
                }
            }
    );

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


        chipGroupHealthConditions = view.findViewById(R.id.chipGroupHealthConditions);
        chipGroupCustomHealthConditions = view.findViewById(R.id.chipGroupCustomHealthConditions);
        etCustomHealthCondition = view.findViewById(R.id.etCustomHealthCondition);
        ivProfileAvatar = view.findViewById(R.id.ivProfileAvatar);




        etCustomHealthCondition.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                String custom = etCustomHealthCondition.getText() != null
                        ? etCustomHealthCondition.getText().toString().trim() : "";
                if (!custom.isEmpty()) {
                    addCustomHealthConditionChip(custom);
                    etCustomHealthCondition.setText("");
                }
                return true;
            }
            return false;
        });

        for (java.util.Map.Entry<Integer, String> entry : CHIP_CONDITION_MAP.entrySet()) {
            Chip chip = view.findViewById(entry.getKey());
            if (chip != null) {
                chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (!isLoading) {
                        onHealthConditionChanged();
                    }
                });
            }
        }

        if (currentUser != null) {
            tvEmail.setText(currentUser.getEmail());
            loadProfileImage(currentUser.getPhotoUrl());
        }

        ivProfileAvatar.setOnClickListener(v -> ensureImagePermissionsAndSelectSource());

        btnLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(requireContext(), LoginActivity.class));
            requireActivity().finishAffinity();
        });

        btnSettings.setOnClickListener(v -> {
            SettingsBottomSheet bottomSheet = new SettingsBottomSheet();
            bottomSheet.show(getChildFragmentManager(), "SettingsBottomSheet");
        });


        loadUserProfileData();

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

    private void loadUserProfileData() {
        if (currentUser == null) return;

        final String uid = currentUser.getUid();
        final com.recipebookpro.data.local.AppDatabase localDb = com.recipebookpro.data.local.AppDatabase.getDatabase(requireContext());

        // 1. Load from Room (Local) first on a background thread
        new Thread(() -> {
            com.recipebookpro.data.local.entity.UserEntity localUser = localDb.userDao().getUserByUid(uid);
            if (localUser != null && isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    userHealthConditions = localUser.getHealthConditions() != null ? new ArrayList<>(localUser.getHealthConditions()) : new ArrayList<>();
                    userCustomHealthConditionsI18n = BilingualTextHelper.mergeLegacyStrings(
                            localUser.getCustomHealthConditions(),
                            localUser.getCustomHealthConditionsI18n());
                    applyActiveCustomHealthKeys(localUser.getActiveCustomHealthConditionKeys());

                    List<String> legacyAllergens = localUser.getAllergens();
                    if (legacyAllergens != null && !legacyAllergens.isEmpty()) {
                        for (String allergen : legacyAllergens) {
                            addLegacyAllergenAsCondition(allergen);
                        }
                    }
                    syncCustomHealthConditionsUi();
                });
            }
            
            // 2. Fetch from Firestore (Remote) and update Room
            // We don't need to be in the thread for this as Firestore is async
            requireActivity().runOnUiThread(() -> {
                db.collection("users").document(uid).get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            User user = doc.toObject(User.class);
                            if (user != null) {
                                // Sync profile image if missing in Firestore but present in Auth (Google)
                                if (TextUtils.isEmpty(user.getProfileImageUrl()) && currentUser.getPhotoUrl() != null) {
                                    String authPhoto = currentUser.getPhotoUrl().toString();
                                    user.setProfileImageUrl(authPhoto);
                                    db.collection("users").document(uid).update("profileImageUrl", authPhoto);
                                }

                                userHealthConditions = user.getHealthConditions() != null ? new ArrayList<>(user.getHealthConditions()) : new ArrayList<>();
                                userCustomHealthConditionsI18n = user.resolveCustomHealthConditionsI18n();
                                applyActiveCustomHealthKeys(user.getActiveCustomHealthConditionKeys());
                                userHealthTriggers = user.getHealthTriggers() != null ? new java.util.HashMap<>(user.getHealthTriggers()) : new java.util.HashMap<>();
                                userHealthWarningTemplates = user.getHealthWarningTemplates() != null ? new java.util.HashMap<>(user.getHealthWarningTemplates()) : new java.util.HashMap<>();

                                boolean needsMigration = false;
                                List<String> legacyAllergens = user.getAllergens();
                                if (legacyAllergens != null && !legacyAllergens.isEmpty()) {
                                    for (String allergen : legacyAllergens) {
                                        if (addLegacyAllergenAsCondition(allergen)) {
                                            needsMigration = true;
                                        }
                                    }
                                }

                                if (userHealthConditions.isEmpty() && activeCustomHealthKeys.isEmpty()) {
                                    persistHealthConditionsToRemote();
                                } else if (needsMigration || user.getCustomHealthConditionsI18n() == null
                                        || user.getCustomHealthConditionsI18n().isEmpty()) {
                                    persistHealthConditionsToRemote();
                                }

                                syncCustomHealthConditionsUi();

                                final User finalUser = user;
                                new Thread(() -> {
                                    com.recipebookpro.data.local.entity.UserEntity entity =
                                            new com.recipebookpro.data.local.entity.UserEntity(
                                                    uid, finalUser.getEmail(), finalUser.getDisplayName(),
                                                    finalUser.getProfileImageUrl(), new ArrayList<>(),
                                                    finalUser.getHealthConditions(), new ArrayList<>());
                                    entity.setCustomHealthConditionsI18n(userCustomHealthConditionsI18n);
                                    entity.setActiveCustomHealthConditionKeys(new ArrayList<>(activeCustomHealthKeys));
                                    entity.setHealthTriggers(userHealthTriggers);
                                    entity.setHealthWarningTemplates(userHealthWarningTemplates);
                                    localDb.userDao().insertUser(entity);
                                }).start();
                            }
                        }
                    });
            });
        }).start();
    }



    private void uploadProfileImage(Uri imageUri) {
        if (currentUser == null) return;
        Toast.makeText(requireContext(), R.string.loading, Toast.LENGTH_SHORT).show();

        StorageReference storageRef = FirebaseStorage.getInstance().getReference()
                .child("profile_images/" + currentUser.getUid() + ".jpg");

        storageRef.putFile(imageUri).addOnSuccessListener(taskSnapshot -> {
            storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                String downloadUrl = uri.toString();
                // Update Firebase Auth profile
                UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                        .setPhotoUri(uri)
                        .build();
                currentUser.updateProfile(profileUpdates);

                // Update Firestore user document
                db.collection("users").document(currentUser.getUid())
                        .update("profileImageUrl", downloadUrl)
                        .addOnSuccessListener(aVoid -> {
                            loadProfileImage(uri);
                            Toast.makeText(requireContext(), R.string.profile_image_updated, Toast.LENGTH_SHORT).show();
                        });
            });
        }).addOnFailureListener(e -> {
            Toast.makeText(requireContext(), R.string.upload_failed, Toast.LENGTH_SHORT).show();
        });
    }

    private void loadProfileImage(Uri photoUrl) {
        if (photoUrl != null && ivProfileAvatar != null) {
            ImageRequest request = new ImageRequest.Builder(requireContext())
                    .data(photoUrl.toString())
                    .target(ivProfileAvatar)
                    .crossfade(true)
                    .transformations(new CircleCropTransformation())
                    .placeholder(R.drawable.ic_nav_profile)
                    .build();
            Coil.imageLoader(requireContext()).enqueue(request);
        }
    }

    private void ensureImagePermissionsAndSelectSource() {
        String galleryPermission = getGalleryPermission();
        boolean cameraGranted = ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        boolean galleryGranted = galleryPermission == null || ContextCompat.checkSelfPermission(
                requireContext(), galleryPermission) == PackageManager.PERMISSION_GRANTED;

        if (cameraGranted && galleryGranted) {
            showImageSourceChooser();
            return;
        }

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.permission_required)
                .setMessage(R.string.camera_permission_rationale)
                .setPositiveButton(R.string.accept, (dialog, which) -> {
                    List<String> permissionsToRequest = new ArrayList<>();
                    if (!cameraGranted) permissionsToRequest.add(Manifest.permission.CAMERA);
                    if (!galleryGranted && galleryPermission != null) permissionsToRequest.add(galleryPermission);
                    permissionLauncher.launch(permissionsToRequest.toArray(new String[0]));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private String getGalleryPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return Manifest.permission.READ_MEDIA_IMAGES;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Manifest.permission.READ_EXTERNAL_STORAGE;
        }
        return null;
    }

    private void showImageSourceChooser() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.profile_photo_select_source)
                .setItems(new String[]{
                        getString(R.string.take_photo),
                        getString(R.string.choose_from_gallery)
                }, (dialog, which) -> {
                    if (which == 0) {
                        openCamera();
                    } else {
                        imagePickerLauncher.launch("image/*");
                    }
                })
                .show();
    }

    private void openCamera() {
        try {
            java.io.File photoFile = java.io.File.createTempFile(
                    "profile_",
                    ".jpg",
                    requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            );
            cameraImageUri = FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".provider",
                    photoFile
            );
            cameraLauncher.launch(cameraImageUri);
        } catch (Exception e) {
            Toast.makeText(requireContext(), R.string.camera_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void populateHealthConditionChips() {
        isLoading = true;
        
        if (getView() != null) {
            for (java.util.Map.Entry<Integer, String> entry : CHIP_CONDITION_MAP.entrySet()) {
                Chip chip = getView().findViewById(entry.getKey());
                if (chip != null) {
                    chip.setChecked(userHealthConditions.contains(entry.getValue()));
                }
            }
        }

        if (chipGroupCustomHealthConditions != null) {
            chipGroupCustomHealthConditions.removeAllViews();
            String uiLang = LocaleHelper.getLanguage(requireContext());
            for (LocalizedText condition : userCustomHealthConditionsI18n) {
                addCustomHealthConditionChipInternal(condition, uiLang,
                        activeCustomHealthKeys.contains(condition.getKey()));
            }
        }
        
        isLoading = false;
    }

    private void addCustomHealthConditionChipInternal(LocalizedText condition, String uiLang, boolean checked) {
        Chip chip = new Chip(requireContext());
        chip.setTag(condition.getKey());
        chip.setText(condition.getForLang(uiLang));
        chip.setCheckable(true);
        chip.setCheckedIconVisible(true);
        chip.setChecked(checked);
        chip.setCloseIconVisible(true);
        chip.setOnCloseIconClickListener(v -> {
            Object tag = chip.getTag();
            if (tag instanceof String) {
                removeCustomConditionByKey((String) tag);
            }
            chipGroupCustomHealthConditions.removeView(chip);
            onHealthConditionChanged();
        });
        chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isLoading) {
                onHealthConditionChanged();
            }
        });
        chipGroupCustomHealthConditions.addView(chip);
    }

    private void addCustomHealthConditionChip(String text) {
        for (int i = 0; i < chipGroupCustomHealthConditions.getChildCount(); i++) {
            Chip existing = (Chip) chipGroupCustomHealthConditions.getChildAt(i);
            if (existing.getText().toString().equalsIgnoreCase(text)) return;
        }

        // Show analyzing toast
        Toast.makeText(requireContext(), R.string.analyzing_health_input, Toast.LENGTH_SHORT).show();

        // Send to Groq for standardization
        com.recipebookpro.data.remote.GroqHealthProfileAnalyzer analyzer =
                new com.recipebookpro.data.remote.GroqHealthProfileAnalyzer();
        analyzer.analyzeHealthInput(text, LocaleHelper.getLanguage(requireContext()),
                new com.recipebookpro.data.remote.GroqHealthProfileAnalyzer.ProfileAnalysisCallback() {
            @Override
            public void onResult(String durumTuru, LocalizedText standartIsim,
                                 java.util.List<String> tetikleyiciler, LocalizedText kisaUyariSablonu) {
                if (!isAdded()) return;

                LocalizedText condition = standartIsim != null ? standartIsim : LocalizedText.fromLegacy(text);
                condition.mergeFrom(LocalizedText.fromLegacy(text));
                prepareConditionForUiLanguage(condition, tetikleyiciler, kisaUyariSablonu);
            }

            @Override
            public void onError(String errorMessage) {
                if (!isAdded()) return;

                LocalizedText fallback = LocalizedText.fromLegacy(text);
                prepareConditionForUiLanguage(fallback, null, null);
            }
        });
    }

    private void prepareConditionForUiLanguage(LocalizedText condition,
                                               java.util.List<String> tetikleyiciler,
                                               LocalizedText kisaUyariSablonu) {
        if (!isAdded() || condition == null) {
            return;
        }
        String uiLang = LocaleHelper.getLanguage(requireContext());
        BilingualTextHelper.syncForAppLanguage(requireContext(), condition, uiLang,
                synced -> finishAddingCustomCondition(synced, tetikleyiciler, kisaUyariSablonu));
    }

    private void finishAddingCustomCondition(LocalizedText condition, java.util.List<String> tetikleyiciler,
                                             LocalizedText kisaUyariSablonu) {
        if (!isAdded() || condition == null) return;

        if (hasCustomCondition(condition.getKey())) {
            String uiLang = LocaleHelper.getLanguage(requireContext());
            Toast.makeText(requireContext(),
                    getString(R.string.health_analysis_success, condition.getForLang(uiLang)),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        isLoading = true;
        userCustomHealthConditionsI18n.add(condition);
        activeCustomHealthKeys.add(condition.getKey());
        addCustomHealthConditionChipInternal(condition, LocaleHelper.getLanguage(requireContext()), true);
        isLoading = false;

        migrateTriggerMapsToKey(condition);

        if (kisaUyariSablonu != null && !TextUtils.isEmpty(kisaUyariSablonu.getTr())) {
            userHealthWarningTemplates.put(condition.getKey(), kisaUyariSablonu.getTr());
        }
        if (kisaUyariSablonu != null && !TextUtils.isEmpty(kisaUyariSablonu.getEn())) {
            userHealthWarningTemplates.put(condition.getKey() + "_en", kisaUyariSablonu.getEn());
        }

        Toast.makeText(requireContext(),
                getString(R.string.health_analysis_success, condition.getForLang(LocaleHelper.getLanguage(requireContext()))),
                Toast.LENGTH_LONG).show();

        if (tetikleyiciler != null && !tetikleyiciler.isEmpty()) {
            String currentLang = LocaleHelper.getLanguage(requireContext());
            String oppositeLang = currentLang.equalsIgnoreCase("tr") ? "en" : "tr";
            com.recipebookpro.util.RiskyIngredientLocaleHelper.ensureLanguage(requireContext(), tetikleyiciler, oppositeLang, translated -> {
                if (!isAdded()) return;
                List<String> combinedTriggers = new ArrayList<>(tetikleyiciler);
                for (String t : translated) {
                    if (!combinedTriggers.contains(t)) {
                        combinedTriggers.add(t);
                    }
                }
                userHealthTriggers.put(condition.getKey(), combinedTriggers);
                onHealthConditionChanged();
            });
        } else {
            onHealthConditionChanged();
        }
    }

    private void removeCustomConditionByKey(String key) {
        userCustomHealthConditionsI18n.removeIf(item -> item.getKey().equals(key));
        activeCustomHealthKeys.remove(key);
        userHealthTriggers.remove(key);
        userHealthWarningTemplates.remove(key);
        userHealthWarningTemplates.remove(key + "_en");
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshCustomHealthChipsForCurrentLanguage(false);
    }

    private void refreshCustomHealthChipsForCurrentLanguage(boolean forcePersist) {
        if (!isAdded() || chipGroupCustomHealthConditions == null) {
            return;
        }
        String uiLang = LocaleHelper.getLanguage(requireContext());
        boolean langChanged = !uiLang.equals(lastDisplayedUiLang);
        lastDisplayedUiLang = uiLang;

        if (userCustomHealthConditionsI18n.isEmpty()) {
            populateHealthConditionChips();
            if (forcePersist) {
                persistHealthConditionsToRemote();
            }
            return;
        }

        if (!langChanged && !forcePersist) {
            populateHealthConditionChips();
            return;
        }

        BilingualTextHelper.syncAllForAppLanguage(requireContext(), userCustomHealthConditionsI18n, uiLang,
                synced -> {
                    if (!isAdded()) {
                        return;
                    }
                    userCustomHealthConditionsI18n = new ArrayList<>(synced);
                    populateHealthConditionChips();
                    if (langChanged || forcePersist) {
                        persistHealthConditionsToRemote();
                    }
                });
    }

    private boolean hasCustomCondition(String key) {
        for (LocalizedText existing : userCustomHealthConditionsI18n) {
            if (existing.getKey().equals(key)) {
                return true;
            }
        }
        return false;
    }

    private void migrateTriggerMapsToKey(LocalizedText condition) {
        String[] legacyKeys = new String[] {condition.getTr(), condition.getEn()};
        for (String legacyKey : legacyKeys) {
            if (!TextUtils.isEmpty(legacyKey) && userHealthTriggers.containsKey(legacyKey)) {
                userHealthTriggers.put(condition.getKey(), userHealthTriggers.remove(legacyKey));
            }
            if (!TextUtils.isEmpty(legacyKey) && userHealthWarningTemplates.containsKey(legacyKey)) {
                userHealthWarningTemplates.put(condition.getKey(), userHealthWarningTemplates.remove(legacyKey));
            }
        }
    }

    private boolean addLegacyAllergenAsCondition(String allergen) {
        if (TextUtils.isEmpty(allergen)) {
            return false;
        }
        LocalizedText condition = LocalizedText.fromLegacy(allergen.trim());
        if (hasCustomCondition(condition.getKey())) {
            return false;
        }
        userCustomHealthConditionsI18n.add(condition);
        activeCustomHealthKeys.add(condition.getKey());
        return true;
    }

    private void applyActiveCustomHealthKeys(List<String> keys) {
        activeCustomHealthKeys.clear();
        if (keys != null && !keys.isEmpty()) {
            activeCustomHealthKeys.addAll(keys);
        }
    }

    private void syncActiveCustomHealthKeysFromChips() {
        activeCustomHealthKeys.clear();
        if (chipGroupCustomHealthConditions == null) {
            return;
        }
        for (int i = 0; i < chipGroupCustomHealthConditions.getChildCount(); i++) {
            Chip chip = (Chip) chipGroupCustomHealthConditions.getChildAt(i);
            if (chip.isChecked() && chip.getTag() instanceof String) {
                activeCustomHealthKeys.add((String) chip.getTag());
            }
        }
    }

    private void syncCustomHealthConditionsUi() {
        if (!isAdded()) {
            return;
        }
        lastDisplayedUiLang = "";
        refreshCustomHealthChipsForCurrentLanguage(true);
    }

    private void onHealthConditionChanged() {
        if (currentUser == null) return;

        List<String> conditions = collectHealthConditions();

        userHealthConditions = conditions;
        syncActiveCustomHealthKeysFromChips();

        persistHealthConditionsToRemote();
    }

    private List<String> collectHealthConditions() {
        List<String> result = new ArrayList<>();
        if (chipGroupHealthConditions == null) return result;
        for (java.util.Map.Entry<Integer, String> entry : CHIP_CONDITION_MAP.entrySet()) {
            Chip chip = chipGroupHealthConditions.findViewById(entry.getKey());
            if (chip != null && chip.isChecked()) {
                result.add(entry.getValue());
            }
        }
        return result;
    }

    private static java.util.List<java.util.Map<String, String>> toFirestoreLocalizedList(
            List<LocalizedText> items) {
        java.util.List<java.util.Map<String, String>> firestoreList = new ArrayList<>();
        if (items == null) {
            return firestoreList;
        }
        for (LocalizedText item : items) {
            java.util.Map<String, String> map = new java.util.HashMap<>();
            map.put("key", item.getKey());
            map.put("tr", item.getTr() != null ? item.getTr() : "");
            map.put("en", item.getEn() != null ? item.getEn() : "");
            firestoreList.add(map);
        }
        return firestoreList;
    }

    private void persistHealthConditionsToRemote() {
        if (currentUser == null) return;

        List<LocalizedText> activeConditions = new ArrayList<>();
        for (LocalizedText item : userCustomHealthConditionsI18n) {
            if (activeCustomHealthKeys.contains(item.getKey())) {
                activeConditions.add(item);
            }
        }
        List<String> legacyLabels = BilingualTextHelper.labelsForLang(activeConditions, "tr");

        java.util.Map<String, Object> updates = new java.util.HashMap<>();
        updates.put("healthConditions", userHealthConditions != null ? userHealthConditions : new ArrayList<>());
        updates.put("customHealthConditions", legacyLabels != null ? legacyLabels : new ArrayList<>());
        updates.put("customHealthConditionsI18n", userCustomHealthConditionsI18n != null ? toFirestoreLocalizedList(userCustomHealthConditionsI18n) : new ArrayList<>());
        updates.put("activeCustomHealthConditionKeys", activeCustomHealthKeys != null ? new ArrayList<>(activeCustomHealthKeys) : new ArrayList<>());
        updates.put("healthTriggers", userHealthTriggers != null ? userHealthTriggers : new java.util.HashMap<>());
        updates.put("healthWarningTemplates", userHealthWarningTemplates != null ? userHealthWarningTemplates : new java.util.HashMap<>());

        db.collection("users").document(currentUser.getUid()).update(updates);

        android.util.Log.d("FIRESTORE_WRITE", "Writing to Firestore: " + updates.toString());

        final String uid = currentUser.getUid();
        final List<String> finalConditions = new ArrayList<>(userHealthConditions);
        final List<LocalizedText> finalCustom = new ArrayList<>(userCustomHealthConditionsI18n);
        final List<String> finalActiveKeys = new ArrayList<>(activeCustomHealthKeys);
        final java.util.Map<String, java.util.List<String>> finalTriggers = new java.util.HashMap<>(userHealthTriggers);
        final java.util.Map<String, String> finalTemplates = new java.util.HashMap<>(userHealthWarningTemplates);
        new Thread(() -> {
            com.recipebookpro.data.local.AppDatabase localDb = com.recipebookpro.data.local.AppDatabase.getDatabase(requireContext());
            com.recipebookpro.data.local.entity.UserEntity entity = localDb.userDao().getUserByUid(uid);
            if (entity != null) {
                entity.setHealthConditions(finalConditions);
                entity.setCustomHealthConditions(legacyLabels);
                entity.setCustomHealthConditionsI18n(finalCustom);
                entity.setActiveCustomHealthConditionKeys(finalActiveKeys);
                entity.setHealthTriggers(finalTriggers);
                entity.setHealthWarningTemplates(finalTemplates);
                entity.setLastUpdated(System.currentTimeMillis());
                localDb.userDao().insertUser(entity);
            }
        }).start();
    }

    /**
     * Persist only healthTriggers and healthWarningTemplates to Firestore/Room.
     * Called from addCustomAllergenChip after Groq analysis adds triggers for allergens.
     */
    private void updateHealthTriggersInDatabase() {
        if (currentUser == null) return;

        java.util.Map<String, Object> updates = new java.util.HashMap<>();
        updates.put("healthTriggers", userHealthTriggers);
        updates.put("healthWarningTemplates", userHealthWarningTemplates);

        db.collection("users").document(currentUser.getUid()).update(updates);

        final String uid = currentUser.getUid();
        final java.util.Map<String, java.util.List<String>> finalTriggers = new java.util.HashMap<>(userHealthTriggers);
        final java.util.Map<String, String> finalTemplates = new java.util.HashMap<>(userHealthWarningTemplates);
        new Thread(() -> {
            com.recipebookpro.data.local.AppDatabase localDb = com.recipebookpro.data.local.AppDatabase.getDatabase(requireContext());
            com.recipebookpro.data.local.entity.UserEntity entity = localDb.userDao().getUserByUid(uid);
            if (entity != null) {
                entity.setHealthTriggers(finalTriggers);
                entity.setHealthWarningTemplates(finalTemplates);
                entity.setLastUpdated(System.currentTimeMillis());
                localDb.userDao().insertUser(entity);
            }
        }).start();
    }
}
