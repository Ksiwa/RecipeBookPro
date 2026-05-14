package com.recipebookpro.presentation.ui.kitchen;

import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import android.graphics.Rect;
import android.view.View;
import com.recipebookpro.R;
import com.recipebookpro.data.remote.CookbookDescriptionLocalizer;
import com.recipebookpro.domain.model.Cookbook;
import com.recipebookpro.presentation.ui.BaseActivity;
import com.recipebookpro.presentation.ui.LocaleHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import coil.Coil;
import coil.request.ImageRequest;

import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

public class CookbookAddEditActivity extends BaseActivity {

    public static final String EXTRA_COOKBOOK_ID = "cookbook_id";

    private TextInputEditText etTitle, etDescription, etTagInput;
    private MaterialSwitch switchPublic;
    private ImageView ivCover;
    private ChipGroup chipGroupTags;
    private MaterialButton btnSave;
    private MaterialToolbar toolbar;
    private NestedScrollView nsvCookbook;

    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private Uri selectedImageUri;
    private Uri cameraImageUri;
    private List<String> tags = new ArrayList<>();

    private String editCookbookId;
    private Cookbook existingCookbook;
    private boolean isEditMode = false;

    private final ExecutorService cookbookTitleDescExecutor = Executors.newSingleThreadExecutor();
    private int cookbookLocalizeJobSeq;

    private final ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            result -> {
                Boolean cameraGranted = result.getOrDefault(Manifest.permission.CAMERA, false);
                if (cameraGranted != null && cameraGranted) {
                    takePhoto();
                } else {
                    Toast.makeText(this, R.string.permission_denied_camera, Toast.LENGTH_SHORT).show();
                }
            }
    );

    private final ActivityResultLauncher<Uri> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicture(),
            success -> {
                if (success && cameraImageUri != null) {
                    selectedImageUri = cameraImageUri;
                    ivCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    ivCover.setPadding(0, 0, 0, 0);
                    ivCover.setBackground(null);
                    ivCover.setImageTintList(null);
                    ivCover.setImageURI(selectedImageUri);
                }
            }
    );

    private final ActivityResultLauncher<String> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    try {
                        java.io.InputStream is = getContentResolver().openInputStream(uri);
                        java.io.File file = new java.io.File(getFilesDir(), "cb_img_" + System.currentTimeMillis() + ".jpg");
                        java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = is.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                        fos.close();
                        if (is != null) is.close();

                        selectedImageUri = Uri.fromFile(file);
                        ivCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        ivCover.setPadding(0, 0, 0, 0);
                        ivCover.setBackground(null);
                        ivCover.setImageTintList(null);
                        ImageRequest request = new ImageRequest.Builder(this)
                                .data(selectedImageUri)
                                .target(ivCover)
                                .build();
                        Coil.imageLoader(this).enqueue(request);
                    } catch (Exception e) {
                        Toast.makeText(this, R.string.photo_pick_failed, Toast.LENGTH_SHORT).show();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cookbook_add_edit);

        applyTopInsetToView(findViewById(R.id.appBarCookbookAddEdit));

        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        editCookbookId = getIntent().getStringExtra(EXTRA_COOKBOOK_ID);
        isEditMode = !TextUtils.isEmpty(editCookbookId);

        initViews();

        if (isEditMode) {
            loadExistingCookbook();
        }
    }

    @Override
    protected void onDestroy() {
        cookbookTitleDescExecutor.shutdown();
        super.onDestroy();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbarCookbookAddEdit);
        toolbar.setNavigationOnClickListener(v -> finish());

        etTitle = findViewById(R.id.etCookbookTitle);
        etDescription = findViewById(R.id.etCookbookDescription);
        etTagInput = findViewById(R.id.etTagInput);
        switchPublic = findViewById(R.id.switchPublic);
        ivCover = findViewById(R.id.ivCookbookCoverEdit);
        chipGroupTags = findViewById(R.id.chipGroupTags);
        btnSave = findViewById(R.id.btnSaveCookbook);
        MaterialCardView cardCover = findViewById(R.id.cardCoverImage);

        if (isEditMode) {
            toolbar.setTitle(R.string.cookbook_edit);
            btnSave.setText(R.string.update);
        }

        cardCover.setOnClickListener(v -> galleryLauncher.launch("image/*"));
        findViewById(R.id.btnCookbookCamera).setOnClickListener(v -> checkCameraPermissionAndTake());
        findViewById(R.id.btnCookbookGallery).setOnClickListener(v -> galleryLauncher.launch("image/*"));

        etTagInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE ||
               (event != null && event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                String tag = etTagInput.getText().toString().trim();
                if (!tag.isEmpty() && !tags.contains(tag)) {
                    addTagChip(tag);
                    etTagInput.setText("");
                }
                return true;
            }
            return false;
        });

        btnSave.setOnClickListener(v -> saveCookbook());

        nsvCookbook = findViewById(R.id.nsvCookbookAddEdit);

        // Toolbar save button | Toolbar kaydet butonu
        toolbar.inflateMenu(R.menu.menu_recipe_add_edit);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_save) {
                saveCookbook();
                return true;
            }
            return false;
        });

        // Robust keyboard detection and padding adjustment | Güçlü klavye algılama ve padding ayarlama
        View rootView = findViewById(android.R.id.content);
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            Rect r = new Rect();
            rootView.getWindowVisibleDisplayFrame(r);
            int screenHeight = rootView.getRootView().getHeight();
            int keypadHeight = screenHeight - r.bottom;

            if (keypadHeight > screenHeight * 0.15) {
                nsvCookbook.setPadding(nsvCookbook.getPaddingLeft(), nsvCookbook.getPaddingTop(), nsvCookbook.getPaddingRight(), keypadHeight + 100);
            } else {
                nsvCookbook.setPadding(nsvCookbook.getPaddingLeft(), nsvCookbook.getPaddingTop(), nsvCookbook.getPaddingRight(), 100);
            }
        });

        // Focus listener to scroll to focused field | Odaklanılan alana kaydırma dinleyicisi
        nsvCookbook.getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> {
            if (newFocus != null && (newFocus instanceof android.widget.EditText || newFocus instanceof android.widget.AutoCompleteTextView)) {
                nsvAddEditPostScroll(newFocus);
            }
        });
    }

    private void nsvAddEditPostScroll(View newFocus) {
        nsvCookbook.postDelayed(() -> {
            int[] viewPos = new int[2];
            newFocus.getLocationOnScreen(viewPos);
            int[] scrollPos = new int[2];
            nsvCookbook.getLocationOnScreen(scrollPos);
            int relativeTop = viewPos[1] - scrollPos[1];
            nsvCookbook.smoothScrollBy(0, relativeTop - 100);
        }, 200);
    }

    private void loadExistingCookbook() {
        btnSave.setEnabled(false);
        db.collection("cookbooks").document(editCookbookId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        existingCookbook = Cookbook.fromDocument(doc);
                        populateForm();
                    } else {
                        Toast.makeText(this, R.string.cookbook_not_found, Toast.LENGTH_SHORT).show();
                        finish();
                    }
                    btnSave.setEnabled(true);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, R.string.load_failed, Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void populateForm() {
        if (existingCookbook == null) return;

        final String rawName = existingCookbook.getName() == null ? "" : existingCookbook.getName().trim();
        final String rawDesc = existingCookbook.getDescription() == null ? "" : existingCookbook.getDescription().trim();
        etTitle.setText(rawName);
        etDescription.setText(rawDesc);
        switchPublic.setChecked(existingCookbook.isPublic());

        final int job = ++cookbookLocalizeJobSeq;
        final String uiLang = LocaleHelper.getLanguage(this);
        cookbookTitleDescExecutor.execute(() -> {
            try {
                String locName = rawName.isEmpty()
                        ? ""
                        : CookbookDescriptionLocalizer.localizeSync(getApplicationContext(), rawName, uiLang);
                String locDesc = rawDesc.isEmpty()
                        ? ""
                        : CookbookDescriptionLocalizer.localizeSync(getApplicationContext(), rawDesc, uiLang);
                runOnUiThread(() -> {
                    if (isFinishing() || job != cookbookLocalizeJobSeq || existingCookbook == null) {
                        return;
                    }
                    String nameNow = existingCookbook.getName() == null ? "" : existingCookbook.getName().trim();
                    String descNow = existingCookbook.getDescription() == null ? "" : existingCookbook.getDescription().trim();
                    if (!rawName.equals(nameNow) || !rawDesc.equals(descNow)) {
                        return;
                    }
                    etTitle.setText(TextUtils.isEmpty(locName) ? rawName : locName);
                    etDescription.setText(TextUtils.isEmpty(locDesc) ? rawDesc : locDesc);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || job != cookbookLocalizeJobSeq || existingCookbook == null) {
                        return;
                    }
                    String nameNow = existingCookbook.getName() == null ? "" : existingCookbook.getName().trim();
                    String descNow = existingCookbook.getDescription() == null ? "" : existingCookbook.getDescription().trim();
                    if (!rawName.equals(nameNow) || !rawDesc.equals(descNow)) {
                        return;
                    }
                    etTitle.setText(rawName);
                    etDescription.setText(rawDesc);
                });
            }
        });

        // Default placeholder state
        ivCover.setScaleType(ImageView.ScaleType.FIT_CENTER);
        ivCover.setImageResource(R.drawable.ic_book);
        ivCover.setPadding(0, 0, 0, 0);
        
        android.util.TypedValue typedValue = new android.util.TypedValue();
        getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimaryContainer, typedValue, true);
        ivCover.setBackgroundColor(typedValue.data);
        getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnPrimaryContainer, typedValue, true);
        ivCover.setImageTintList(android.content.res.ColorStateList.valueOf(typedValue.data));

        if (!TextUtils.isEmpty(existingCookbook.getCoverImageUrl())) {
            ImageRequest request = new ImageRequest.Builder(this)
                .data(existingCookbook.getCoverImageUrl())
                .target(new coil.target.Target() {
                    @Override
                    public void onStart(@Nullable android.graphics.drawable.Drawable placeholder) {}

                    @Override
                    public void onSuccess(@NonNull android.graphics.drawable.Drawable result) {
                        ivCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        ivCover.setBackground(null);
                        ivCover.setImageTintList(null);
                        ivCover.setImageDrawable(result);
                    }

                    @Override
                    public void onError(@Nullable android.graphics.drawable.Drawable error) {
                        // Keep placeholder state
                    }
                })
                .build();
            Coil.imageLoader(this).enqueue(request);
        }

        if (existingCookbook.getTags() != null) {
            for (String tag : existingCookbook.getTags()) {
                addTagChip(tag);
            }
        }
    }

    private void addTagChip(String tag) {
        tags.add(tag);
        Chip chip = new Chip(this);
        chip.setText(tag);
        chip.setCloseIconVisible(true);
        chip.setOnCloseIconClickListener(v -> {
            chipGroupTags.removeView(chip);
            tags.remove(tag);
        });
        chipGroupTags.addView(chip);
    }

    private void saveCookbook() {
        String title = etTitle.getText().toString().trim();
        String desc = etDescription.getText().toString().trim();

        if (TextUtils.isEmpty(title)) {
            etTitle.setError(getString(R.string.required_field));
            return;
        }

        btnSave.setEnabled(false);

        if (selectedImageUri != null && !selectedImageUri.toString().startsWith("http")) {
            uploadImageAndSave(title, desc);
        } else {
            String existingImage = (isEditMode && existingCookbook != null)
                    ? existingCookbook.getCoverImageUrl() : "";
            saveToFirestore(title, desc, existingImage);
        }
    }

    private void uploadImageAndSave(String title, String desc) {
        String docId = isEditMode ? editCookbookId : db.collection("cookbooks").document().getId();
        String path = "cookbooks/" + docId + "/cover.jpg";
        StorageReference ref = FirebaseStorage.getInstance().getReference().child(path);

        ref.putFile(selectedImageUri)
           .continueWithTask(task -> {
               if (!task.isSuccessful()) {
                   if (task.getException() != null) throw task.getException();
               }
               return ref.getDownloadUrl();
           })
           .addOnSuccessListener(uri -> {
               if (!isEditMode) {
                   saveToFirestoreWithId(docId, title, desc, uri.toString());
               } else {
                   saveToFirestore(title, desc, uri.toString());
               }
           })
           .addOnFailureListener(e -> {
               Toast.makeText(this, getString(R.string.image_upload_failed_with_reason, e.getMessage()), Toast.LENGTH_LONG).show();
               String existingImage = (isEditMode && existingCookbook != null)
                       ? existingCookbook.getCoverImageUrl() : "";
               saveToFirestore(title, desc, existingImage);
           });
    }

    // Yeni defterler için ID'yi önceden belirlediğimiz için bu yardımcı metodu ekliyorum | Adding this helper method since we pre-determine the ID for new cookbooks
    private void saveToFirestoreWithId(String docId, String title, String desc, String imageUrl) {
        Cookbook book = new Cookbook(currentUser.getUid(), title);
        book.setId(docId);
        book.setDescription(desc);
        book.setCoverImageUrl(imageUrl);
        book.setPublic(switchPublic.isChecked());
        book.setTags(tags);

        db.collection("cookbooks").document(docId).set(book)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, R.string.cookbook_saved, Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, R.string.save_failed, Toast.LENGTH_SHORT).show();
                    btnSave.setEnabled(true);
                });
    }

    private void saveToFirestore(String title, String desc, String imageUrl) {
        if (isEditMode && existingCookbook != null) {
            existingCookbook.setName(title);
            existingCookbook.setDescription(desc);
            existingCookbook.setCoverImageUrl(imageUrl);
            existingCookbook.setPublic(switchPublic.isChecked());
            existingCookbook.setTags(tags);

            db.collection("cookbooks").document(editCookbookId).set(existingCookbook)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, R.string.cookbook_updated, Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, R.string.update_failed, Toast.LENGTH_SHORT).show();
                        btnSave.setEnabled(true);
                    });
        } else {
            Cookbook book = new Cookbook(currentUser.getUid(), title);
            book.setDescription(desc);
            book.setCoverImageUrl(imageUrl);
            book.setPublic(switchPublic.isChecked());
            book.setTags(tags);

            db.collection("cookbooks").add(book)
                    .addOnSuccessListener(ref -> {
                        Toast.makeText(this, R.string.cookbook_saved, Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, R.string.save_failed, Toast.LENGTH_SHORT).show();
                        btnSave.setEnabled(true);
                    });
        }
    }

    private void checkCameraPermissionAndTake() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            takePhoto();
        } else {
            permissionLauncher.launch(new String[]{Manifest.permission.CAMERA});
        }
    }

    private void takePhoto() {
        try {
            java.io.File photoFile = java.io.File.createTempFile("cb_", ".jpg", getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES));
            cameraImageUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", photoFile);
            cameraLauncher.launch(cameraImageUri);
        } catch (Exception e) {
            Toast.makeText(this, R.string.camera_error, Toast.LENGTH_SHORT).show();
        }
    }
}
