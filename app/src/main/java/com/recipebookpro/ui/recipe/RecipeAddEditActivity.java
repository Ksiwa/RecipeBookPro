package com.recipebookpro.ui.recipe;

import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import android.content.Intent;
import android.provider.MediaStore;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.graphics.Rect;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.recipebookpro.R;
import com.recipebookpro.model.Recipe;
import com.recipebookpro.model.Step;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import java.util.UUID;
import com.recipebookpro.ui.BaseActivity;
import com.recipebookpro.ui.recipe.adapter.EditableIngredientAdapter;
import com.recipebookpro.ui.recipe.adapter.EditableStepAdapter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import coil.Coil;
import coil.request.ImageRequest;

public class RecipeAddEditActivity extends BaseActivity {

    public static final String EXTRA_RECIPE = "extra_recipe";

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;

    private Recipe currentRecipe;
    private boolean isEditMode = false;

    // Views
    private ImageView ivPreview;
    private View llImagePlaceholder;
    private TextInputEditText etTitle, etDescription, etServings;
    private AutoCompleteTextView actvCategory, actvCookbook;
    private ChipGroup cgAllergens;
    private RecyclerView rvIngredientsEdit, rvStepsEdit;
    private MaterialButton btnSave;
    private CircularProgressIndicator progressSave;
    private androidx.core.widget.NestedScrollView nsvAddEdit;

    // Data lists
    private List<Recipe.Ingredient> ingredientList = new ArrayList<>();
    private List<Step> stepList = new ArrayList<>();
    private List<com.recipebookpro.model.Cookbook> userCookbooks = new ArrayList<>();
    private String selectedCookbookId = null;
    private EditableIngredientAdapter ingredientAdapter;
    private EditableStepAdapter stepAdapter;
    private ItemTouchHelper itemTouchHelper;

    // Permissions
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

    // Image Pickers
    // Image Picker
    private Uri selectedImageUri = null;
    private Uri cameraImageUri = null;
    private final ActivityResultLauncher<Uri> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicture(),
            success -> {
                if (success && cameraImageUri != null) {
                    selectedImageUri = cameraImageUri;
                    ivPreview.setImageURI(selectedImageUri);
                    llImagePlaceholder.setVisibility(View.GONE);
                }
            }
    );

    private final ActivityResultLauncher<String> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    try {
                        // Resmi uygulamanın içine kopyalıyoruz (kalıcı olması için)
                        java.io.InputStream is = getContentResolver().openInputStream(uri);
                        java.io.File file = new java.io.File(getFilesDir(), "recipe_img_" + System.currentTimeMillis() + ".jpg");
                        java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = is.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                        fos.close();
                        if (is != null) is.close();

                        selectedImageUri = Uri.fromFile(file);
                        ivPreview.setImageURI(selectedImageUri);
                        llImagePlaceholder.setVisibility(View.GONE);
                    } catch (Exception e) {
                        Toast.makeText(this, "Fotoğraf alınamadı", Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recipe_add_edit);

        applyTopInsetToView(findViewById(R.id.appBarAddEdit));

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        setupAdapters();
        setupAllergens();
        fetchCookbooks();

        currentRecipe = (Recipe) getIntent().getSerializableExtra(EXTRA_RECIPE);
        if (currentRecipe != null) {
            isEditMode = true;
            populateData();
        } else {
            currentRecipe = new Recipe();
            // Add initial empty rows
            ingredientList.add(new Recipe.Ingredient("", "", ""));
            stepList.add(new Step(1, "", 0, ""));
        }
    }

    private void initViews() {
        MaterialToolbar toolbar = findViewById(R.id.toolbarAddEdit);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.inflateMenu(R.menu.menu_recipe_add_edit);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_save) {
                saveRecipe();
                return true;
            }
            return false;
        });

        ivPreview = findViewById(R.id.ivPreview);
        llImagePlaceholder = findViewById(R.id.llImagePlaceholder);
        etTitle = findViewById(R.id.etTitle);
        etDescription = findViewById(R.id.etDescription);
        etServings = findViewById(R.id.etServings);
        actvCategory = findViewById(R.id.actvCategory);
        actvCookbook = findViewById(R.id.actvCookbook);
        cgAllergens = findViewById(R.id.cgAllergens);
        rvIngredientsEdit = findViewById(R.id.rvIngredientsEdit);
        rvStepsEdit = findViewById(R.id.rvStepsEdit);
        btnSave = findViewById(R.id.btnSave);
        progressSave = findViewById(R.id.progressSave);
        nsvAddEdit = findViewById(R.id.nsvAddEdit);

        findViewById(R.id.btnCamera).setOnClickListener(v -> checkCameraPermissionAndTake());
        findViewById(R.id.btnGallery).setOnClickListener(v -> galleryLauncher.launch("image/*"));
        findViewById(R.id.btnAddCustomAllergen).setOnClickListener(v -> showAddCustomAllergenDialog());

        String[] categories = getResources().getStringArray(R.array.recipe_categories);
        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, categories);
        actvCategory.setAdapter(catAdapter);

        findViewById(R.id.btnAddIngredient).setOnClickListener(v -> {
            ingredientList.add(new Recipe.Ingredient("", "", ""));
            ingredientAdapter.notifyItemInserted(ingredientList.size() - 1);
        });

        findViewById(R.id.btnAddStep).setOnClickListener(v -> {
            stepList.add(new Step(stepList.size() + 1, "", 0, ""));
            stepAdapter.notifyItemInserted(stepList.size() - 1);
        });

        btnSave.setOnClickListener(v -> saveRecipe());

        // Robust keyboard detection and padding adjustment
        View rootView = findViewById(android.R.id.content);
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            Rect r = new Rect();
            rootView.getWindowVisibleDisplayFrame(r);
            int screenHeight = rootView.getRootView().getHeight();
            int keypadHeight = screenHeight - r.bottom;

            if (keypadHeight > screenHeight * 0.15) { // 15% threshold for keyboard
                nsvAddEdit.setPadding(nsvAddEdit.getPaddingLeft(), nsvAddEdit.getPaddingTop(), nsvAddEdit.getPaddingRight(), keypadHeight + 100);
            } else {
                nsvAddEdit.setPadding(nsvAddEdit.getPaddingLeft(), nsvAddEdit.getPaddingTop(), nsvAddEdit.getPaddingRight(), 100);
            }
        });

        // Focus listener to scroll to focused field
        nsvAddEdit.getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> {
            if (newFocus != null && (newFocus instanceof android.widget.EditText || newFocus instanceof android.widget.AutoCompleteTextView)) {
                nsvAddEdit.postDelayed(() -> {
                    int[] viewPos = new int[2];
                    newFocus.getLocationOnScreen(viewPos);
                    
                    int[] scrollPos = new int[2];
                    nsvAddEdit.getLocationOnScreen(scrollPos);
                    
                    // Target: Position the view about 100px below the top of the visible ScrollView area
                    int relativeTop = viewPos[1] - scrollPos[1];
                    nsvAddEdit.smoothScrollBy(0, relativeTop - 100);
                }, 200); // Small delay to allow layout to stabilize
            }
        });
    }

    private int[] viewLocationOnScreen(View v) {
        int[] location = new int[2];
        v.getLocationOnScreen(location);
        return location;
    }

    private void setupAdapters() {
        String[] units = getResources().getStringArray(R.array.ingredient_units);
        
        rvIngredientsEdit.setLayoutManager(new LinearLayoutManager(this));
        ingredientAdapter = new EditableIngredientAdapter(ingredientList, units);
        rvIngredientsEdit.setAdapter(ingredientAdapter);

        rvStepsEdit.setLayoutManager(new LinearLayoutManager(this));
        stepAdapter = new EditableStepAdapter(stepList, viewHolder -> itemTouchHelper.startDrag(viewHolder));
        rvStepsEdit.setAdapter(stepAdapter);

        itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                stepAdapter.onItemMove(viewHolder.getAdapterPosition(), target.getAdapterPosition());
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) { }
        });
        itemTouchHelper.attachToRecyclerView(rvStepsEdit);
    }

    private void setupAllergens() {
        String[] allergenTags = getResources().getStringArray(R.array.allergen_tags);
        for (String tag : allergenTags) {
            addAllergenChip(tag);
        }
    }

    private void addAllergenChip(String tag) {
        Chip chip = new Chip(this);
        chip.setText(tag);
        chip.setCheckable(true);
        chip.setCheckedIconVisible(true);
        cgAllergens.addView(chip);
    }

    private void showAddCustomAllergenDialog() {
        android.widget.EditText et = new android.widget.EditText(this);
        et.setHint(R.string.allergen_name);
        new AlertDialog.Builder(this)
            .setTitle(R.string.add_custom_allergen)
            .setView(et)
            .setPositiveButton(R.string.add_custom, (d, w) -> {
                String name = et.getText().toString().trim();
                if (!TextUtils.isEmpty(name)) {
                    addAllergenChip(name);
                    ((Chip) cgAllergens.getChildAt(cgAllergens.getChildCount() - 1)).setChecked(true);
                }
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void fetchCookbooks() {
        db.collection("cookbooks")
            .whereEqualTo("userId", currentUser.getUid())
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                userCookbooks.clear();
                List<String> names = new ArrayList<>();
                for (com.google.firebase.firestore.DocumentSnapshot doc : queryDocumentSnapshots) {
                    com.recipebookpro.model.Cookbook cb = com.recipebookpro.model.Cookbook.fromDocument(doc);
                    userCookbooks.add(cb);
                    names.add(cb.getName());
                }
                
                if (names.isEmpty()) {
                    actvCookbook.setHint(R.string.no_cookbooks_found);
                    actvCookbook.setEnabled(false);
                } else {
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, names);
                    actvCookbook.setAdapter(adapter);
                    actvCookbook.setOnItemClickListener((parent, view, position, id) -> {
                        selectedCookbookId = userCookbooks.get(position).getId();
                    });
                }
            });
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
            java.io.File photoFile = java.io.File.createTempFile("recipe_", ".jpg", getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES));
            cameraImageUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", photoFile);
            cameraLauncher.launch(cameraImageUri);
        } catch (Exception e) {
            Toast.makeText(this, R.string.camera_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void populateData() {
        ((com.google.android.material.appbar.MaterialToolbar) findViewById(R.id.toolbarAddEdit)).setTitle(R.string.edit_recipe);
        btnSave.setText(R.string.update_recipe);

        etTitle.setText(currentRecipe.getTitle());
        etDescription.setText(currentRecipe.getDescription());
        etServings.setText(String.valueOf(currentRecipe.getServings()));
        actvCategory.setText(currentRecipe.getCategory(), false);

        if (!TextUtils.isEmpty(currentRecipe.getImageUrl())) {
            ImageRequest request = new ImageRequest.Builder(this)
                .data(currentRecipe.getImageUrl())
                .target(ivPreview)
                .build();
            Coil.imageLoader(this).enqueue(request);
            llImagePlaceholder.setVisibility(View.GONE);
        }

        ingredientList.clear();
        ingredientList.addAll(currentRecipe.getIngredients());
        ingredientAdapter.notifyDataSetChanged();

        stepList.clear();
        stepList.addAll(currentRecipe.getStepList());
        stepAdapter.notifyDataSetChanged();

        List<String> currentAllergens = currentRecipe.getAllergens();
        for (int i = 0; i < cgAllergens.getChildCount(); i++) {
            Chip chip = (Chip) cgAllergens.getChildAt(i);
            if (currentAllergens.contains(chip.getText().toString())) {
                chip.setChecked(true);
            }
        }
    }

    private void saveRecipe() {
        String title = etTitle.getText() != null ? etTitle.getText().toString().trim() : "";
        if (TextUtils.isEmpty(title)) {
            etTitle.setError(getString(R.string.required_field));
            Toast.makeText(this, "Lütfen tarif adını girin", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSave.setEnabled(false);
        progressSave.setVisibility(View.VISIBLE);

        currentRecipe.setTitle(title);
        currentRecipe.setDescription(etDescription.getText() != null ? etDescription.getText().toString().trim() : "");
        currentRecipe.setCategory(actvCategory.getText() != null ? actvCategory.getText().toString().trim() : "");
        
        int servings = 1;
        try {
            servings = Integer.parseInt(etServings.getText().toString().trim());
        } catch (Exception ignored) {}
        currentRecipe.setServings(servings);

        // Filter out empty ingredients and validate amount/unit
        List<Recipe.Ingredient> validIngredients = new ArrayList<>();
        for (Recipe.Ingredient ing : ingredientList) {
            String name = ing.getName().trim();
            String amount = ing.getAmount().trim();
            String unit = ing.getUnit().trim();
            
            if (!TextUtils.isEmpty(name)) {
                if (TextUtils.isEmpty(amount)) {
                    Toast.makeText(this, "Lütfen " + name + " için miktar girin", Toast.LENGTH_SHORT).show();
                    btnSave.setEnabled(true);
                    progressSave.setVisibility(View.GONE);
                    return;
                }
                if (TextUtils.isEmpty(unit)) {
                    Toast.makeText(this, "Lütfen " + name + " için birim seçin", Toast.LENGTH_SHORT).show();
                    btnSave.setEnabled(true);
                    progressSave.setVisibility(View.GONE);
                    return;
                }
                validIngredients.add(ing);
            }
        }
        
        if (validIngredients.isEmpty()) {
            Toast.makeText(this, R.string.ingredient_required_message, Toast.LENGTH_SHORT).show();
            btnSave.setEnabled(true);
            progressSave.setVisibility(View.GONE);
            return;
        }
        currentRecipe.setIngredients(validIngredients);
        currentRecipe.buildIngredientNames();

        // Filter out empty steps and fix order
        List<Step> validSteps = new ArrayList<>();
        int order = 1;
        for (Step step : stepList) {
            if (!TextUtils.isEmpty(step.getDescription().trim())) {
                step.setOrder(order++);
                validSteps.add(step);
            }
        }
        currentRecipe.setStepList(validSteps);
        
        // Collect allergens
        List<String> allergens = new ArrayList<>();
        for (int i = 0; i < cgAllergens.getChildCount(); i++) {
            Chip chip = (Chip) cgAllergens.getChildAt(i);
            if (chip.isChecked()) allergens.add(chip.getText().toString());
        }
        currentRecipe.setAllergens(allergens);

        if (!isEditMode) {
            currentRecipe.setUserId(currentUser.getUid());
            currentRecipe.setCreatedAt(System.currentTimeMillis());
            currentRecipe.setPublic(true);
        }

        String docId = isEditMode ? currentRecipe.getId() : db.collection("recipes").document().getId();
        currentRecipe.setId(docId);

        if (selectedImageUri != null && !selectedImageUri.toString().startsWith("http")) {
            uploadImageAndSave(docId);
        } else {
            saveToFirestore(docId);
        }
    }

    private void uploadImageAndSave(String docId) {
        // İstediğin dosya yapısı: recipes/{recipe_id}/main.jpg
        String path = "recipes/" + docId + "/main.jpg";
        StorageReference ref = FirebaseStorage.getInstance().getReference().child(path);

        ref.putFile(selectedImageUri)
            .continueWithTask(task -> {
                if (!task.isSuccessful()) {
                    if (task.getException() != null) throw task.getException();
                }
                return ref.getDownloadUrl();
            })
            .addOnSuccessListener(uri -> {
                currentRecipe.setImageUrl(uri.toString());
                saveToFirestore(docId);
            })
            .addOnFailureListener(e -> {
                Toast.makeText(this, "Resim yüklenemedi: " + e.getMessage(), Toast.LENGTH_LONG).show();
                // Yükleme başarısız olsa da tarif kaydedilsin (resimsiz olarak)
                saveToFirestore(docId);
            });
    }

    private void saveToFirestore(String docId) {
        db.collection("recipes").document(docId)
            .set(currentRecipe)
            .addOnSuccessListener(aVoid -> {
                if (selectedCookbookId != null) {
                    db.collection("cookbooks").document(selectedCookbookId)
                        .update("recipeIds", com.google.firebase.firestore.FieldValue.arrayUnion(docId))
                        .addOnCompleteListener(t -> {
                            Toast.makeText(this, R.string.recipe_saved, Toast.LENGTH_SHORT).show();
                            finish();
                        });
                } else {
                    Toast.makeText(this, R.string.recipe_saved, Toast.LENGTH_SHORT).show();
                    finish();
                }
            })
            .addOnFailureListener(e -> {
                Toast.makeText(this, R.string.recipe_save_failed, Toast.LENGTH_SHORT).show();
                btnSave.setEnabled(true);
                progressSave.setVisibility(View.GONE);
            });
    }
    }

