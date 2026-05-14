package com.recipebookpro.presentation.ui.recipe;

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
import androidx.annotation.Nullable;
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
import com.recipebookpro.domain.model.Recipe;
import com.recipebookpro.domain.model.Step;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import java.util.UUID;
import com.recipebookpro.domain.service.TranslationService;
import com.recipebookpro.data.remote.MLKitTranslationService;
import com.recipebookpro.data.remote.CookbookDescriptionLocalizer;
import com.recipebookpro.domain.usecase.TranslateRecipeUseCase;
import com.recipebookpro.presentation.ui.BaseActivity;
import com.recipebookpro.presentation.ui.LocaleHelper;
import com.recipebookpro.util.FractionUtils;
import com.recipebookpro.presentation.ui.recipe.adapter.EditableIngredientAdapter;
import com.recipebookpro.presentation.ui.recipe.adapter.EditableStepAdapter;
import com.recipebookpro.util.NotificationTrigger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import coil.Coil;
import coil.request.ImageRequest;

public class RecipeAddEditActivity extends BaseActivity {

    public static final String EXTRA_RECIPE = "extra_recipe";

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;

    private Recipe currentRecipe;
    private boolean isEditMode = false;
    private TranslateRecipeUseCase translateRecipeUseCase;

    // Views
    private ImageView ivPreview;
    private View llImagePlaceholder;
    private TextInputEditText etTitle, etDescription, etServings, etCalories, etRecipeNotes;
    private AutoCompleteTextView actvCategory, actvCookbook;
    private ChipGroup cgAllergens;
    private RecyclerView rvIngredientsEdit, rvStepsEdit;
    private MaterialButton btnSave;
    private CircularProgressIndicator progressSave;
    private androidx.core.widget.NestedScrollView nsvAddEdit;

    // Data lists
    private List<Recipe.Ingredient> ingredientList = new ArrayList<>();
    private List<Step> stepList = new ArrayList<>();
    private List<com.recipebookpro.domain.model.Cookbook> userCookbooks = new ArrayList<>();
    private String selectedCookbookId = null;
    private EditableIngredientAdapter ingredientAdapter;
    private EditableStepAdapter stepAdapter;
    private ItemTouchHelper itemTouchHelper;

    private final ExecutorService recipeNoteLocalizeExecutor = Executors.newSingleThreadExecutor();
    private int recipeNoteLocalizeJobSeq;

    /** True when the form shows translated fields while originals are kept for Firestore. */
    private boolean editFormUsesTranslatedLayer;
    private String snapTitle;
    private String snapDescription;
    private List<Recipe.Ingredient> snapIngredients;
    private List<Step> snapSteps;
    private List<String> snapAllergens;

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
                        // Resmi uygulamanın içine kopyalıyoruz (kalıcı olması için) | Copying the image into the app (for persistence)
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
                        Toast.makeText(this, R.string.photo_pick_failed, Toast.LENGTH_SHORT).show();
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
        
        translateRecipeUseCase = new TranslateRecipeUseCase(new MLKitTranslationService(this));

        currentRecipe = (Recipe) getIntent().getSerializableExtra(EXTRA_RECIPE);
        if (currentRecipe != null) {
            isEditMode = true;
            populateData();
        } else {
            currentRecipe = new Recipe();
            // Add initial empty rows | İlk boş satırları ekle
            ingredientList.add(new Recipe.Ingredient("", "", ""));
            stepList.add(new Step(1, "", 0, ""));
        }
    }

    @Override
    protected void onDestroy() {
        recipeNoteLocalizeExecutor.shutdown();
        super.onDestroy();
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
        etCalories = findViewById(R.id.etCalories);
        etRecipeNotes = findViewById(R.id.etRecipeNotes);
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

        String[] categories = getResources().getStringArray(R.array.recipe_category_labels);
        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, categories);
        actvCategory.setAdapter(catAdapter);

        findViewById(R.id.btnAddIngredient).setOnClickListener(v -> addIngredientRow());

        findViewById(R.id.btnAddStep).setOnClickListener(v -> addStepRow());

        btnSave.setOnClickListener(v -> saveRecipe());

        // Robust keyboard detection and padding adjustment | Güçlü klavye algılama ve padding ayarlama
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

        // Focus listener to scroll to focused field | Odaklanılan alana kaydırma dinleyicisi
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
                    com.recipebookpro.domain.model.Cookbook cb = com.recipebookpro.domain.model.Cookbook.fromDocument(doc);
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

    private void addIngredientRow() {
        ingredientList.add(new Recipe.Ingredient("", "", ""));
        if (snapIngredients != null) {
            snapIngredients.add(new Recipe.Ingredient("", "", ""));
        }
        ingredientAdapter.notifyItemInserted(ingredientList.size() - 1);
    }

    private void addStepRow() {
        int nextOrder = stepList.size() + 1;
        stepList.add(new Step(nextOrder, "", 0, ""));
        if (snapSteps != null) {
            snapSteps.add(new Step(nextOrder, "", 0, ""));
        }
        stepAdapter.notifyItemInserted(stepList.size() - 1);
    }

    private static boolean hasAnyTranslation(Recipe r) {
        if (r == null) {
            return false;
        }
        if (!TextUtils.isEmpty(r.getTranslatedTitle())) {
            return true;
        }
        if (!TextUtils.isEmpty(r.getTranslatedDescription())) {
            return true;
        }
        if (!TextUtils.isEmpty(r.getTranslatedInstructions())) {
            return true;
        }
        for (Recipe.Ingredient ing : r.getIngredients()) {
            if (!TextUtils.isEmpty(ing.getTranslatedName()) || !TextUtils.isEmpty(ing.getTranslatedUnit())) {
                return true;
            }
        }
        for (Step s : r.getStepList()) {
            if (!TextUtils.isEmpty(s.getTranslatedDescription())) {
                return true;
            }
        }
        for (String a : r.getTranslatedAllergens()) {
            if (!TextUtils.isEmpty(a)) {
                return true;
            }
        }
        return false;
    }

    private static boolean shouldEditTranslatedLayer(Recipe r, String appLang) {
        if (r == null || TextUtils.isEmpty(appLang)) {
            return false;
        }
        String orig = r.getOriginalLanguage();
        if (TextUtils.isEmpty(orig)) {
            return false;
        }
        if (orig.equalsIgnoreCase(appLang)) {
            return false;
        }
        return hasAnyTranslation(r);
    }

    private static String pickTranslatedText(String translated, String fallback) {
        return !TextUtils.isEmpty(translated) ? translated : fallback;
    }

    private static Recipe.Ingredient copyIngredientSnapshot(Recipe.Ingredient ing) {
        Recipe.Ingredient out = new Recipe.Ingredient(ing.getName(), ing.getAmount(), ing.getUnit());
        out.setNumericAmount(ing.getNumericAmount());
        return out;
    }

    private static Step copyStepSnapshot(Step s) {
        return new Step(s.getOrder(), s.getDescription(), s.getTimerMinutes(), s.getImageUrl());
    }

    private static Recipe.Ingredient mergeIngredientForTranslatedSave(Recipe.Ingredient form, Recipe.Ingredient snap) {
        String snapName = snap != null ? snap.getName() : "";
        String snapUnit = snap != null ? snap.getUnit() : "";
        String formName = form.getName() != null ? form.getName().trim() : "";
        String formUnit = form.getUnit() != null ? form.getUnit().trim() : "";
        String formAmount = form.getAmount() != null ? form.getAmount().trim() : "";

        if (TextUtils.isEmpty(snapName) && TextUtils.isEmpty(snapUnit)) {
            Recipe.Ingredient out = new Recipe.Ingredient(formName, formAmount, formUnit);
            double num = form.getNumericAmount() > 0 ? form.getNumericAmount() : FractionUtils.parseAmount(formAmount);
            out.setNumericAmount(num);
            return out;
        }
        Recipe.Ingredient out = new Recipe.Ingredient(snapName, formAmount, snapUnit);
        double num = form.getNumericAmount() > 0 ? form.getNumericAmount() : FractionUtils.parseAmount(formAmount);
        out.setNumericAmount(num);
        if (!formName.equals(snapName)) {
            out.setTranslatedName(formName);
        }
        if (!formUnit.equals(snapUnit)) {
            out.setTranslatedUnit(formUnit);
        }
        return out;
    }

    private static Step mergeStepForTranslatedSave(Step form, Step snap) {
        String snapDesc = snap != null ? snap.getDescription() : "";
        String formDesc = form.getDescription() != null ? form.getDescription().trim() : "";
        if (TextUtils.isEmpty(snapDesc)) {
            return new Step(form.getOrder(), formDesc, form.getTimerMinutes(), form.getImageUrl());
        }
        Step out = new Step(form.getOrder(), snapDesc, form.getTimerMinutes(), form.getImageUrl());
        if (!formDesc.equals(snapDesc)) {
            out.setTranslatedDescription(formDesc);
        }
        return out;
    }

    private static String buildLegacyStepsText(List<Step> steps) {
        StringBuilder sb = new StringBuilder();
        for (Step s : steps) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            String desc = s.getDescription() != null ? s.getDescription() : "";
            sb.append(s.getOrder()).append(". ").append(desc);
        }
        return sb.toString();
    }

    private static String buildTranslatedInstructionsText(List<Step> steps) {
        StringBuilder sb = new StringBuilder();
        for (Step s : steps) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            String line = !TextUtils.isEmpty(s.getTranslatedDescription()) ? s.getTranslatedDescription() : s.getDescription();
            sb.append(line);
        }
        return sb.toString().trim();
    }

    private void populateData() {
        ((com.google.android.material.appbar.MaterialToolbar) findViewById(R.id.toolbarAddEdit)).setTitle(R.string.edit_recipe);
        btnSave.setText(R.string.update_recipe);

        String appLang = LocaleHelper.getLanguage(this);
        editFormUsesTranslatedLayer = shouldEditTranslatedLayer(currentRecipe, appLang);
        if (editFormUsesTranslatedLayer) {
            snapTitle = currentRecipe.getTitle();
            snapDescription = currentRecipe.getDescription();
            snapIngredients = new ArrayList<>();
            for (Recipe.Ingredient ing : currentRecipe.getIngredients()) {
                snapIngredients.add(copyIngredientSnapshot(ing));
            }
            snapSteps = new ArrayList<>();
            for (Step s : currentRecipe.getStepList()) {
                snapSteps.add(copyStepSnapshot(s));
            }
            snapAllergens = new ArrayList<>(currentRecipe.getAllergens());
            etTitle.setText(pickTranslatedText(currentRecipe.getTranslatedTitle(), snapTitle));
            etDescription.setText(pickTranslatedText(currentRecipe.getTranslatedDescription(), snapDescription));
        } else {
            snapTitle = null;
            snapDescription = null;
            snapIngredients = null;
            snapSteps = null;
            snapAllergens = null;
            etTitle.setText(currentRecipe.getTitle());
            etDescription.setText(currentRecipe.getDescription());
        }

        etServings.setText(String.valueOf(currentRecipe.getServings()));
        etCalories.setText(String.valueOf(currentRecipe.getCalories()));
        actvCategory.setText(com.recipebookpro.util.CategoryLocalization.getDisplayName(this, currentRecipe.getCategory()), false);

        // Default placeholder state
        ivPreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        ivPreview.setImageResource(R.drawable.ic_cook);
        ivPreview.setPadding(0, 0, 0, 0);
        
        android.util.TypedValue typedValue = new android.util.TypedValue();
        getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimaryContainer, typedValue, true);
        ivPreview.setBackgroundColor(typedValue.data);
        getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnPrimaryContainer, typedValue, true);
        ivPreview.setImageTintList(android.content.res.ColorStateList.valueOf(typedValue.data));
        llImagePlaceholder.setVisibility(View.GONE);

        if (!TextUtils.isEmpty(currentRecipe.getImageUrl())) {
            ImageRequest request = new ImageRequest.Builder(this)
                .data(currentRecipe.getImageUrl())
                .target(new coil.target.Target() {
                    @Override
                    public void onStart(@Nullable android.graphics.drawable.Drawable placeholder) {}

                    @Override
                    public void onSuccess(@NonNull android.graphics.drawable.Drawable result) {
                        ivPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        ivPreview.setBackground(null);
                        ivPreview.setImageTintList(null);
                        ivPreview.setImageDrawable(result);
                    }

                    @Override
                    public void onError(@Nullable android.graphics.drawable.Drawable error) {
                        // Keep placeholder state
                    }
                })
                .build();
            Coil.imageLoader(this).enqueue(request);
        }

        ingredientList.clear();
        if (editFormUsesTranslatedLayer) {
            for (Recipe.Ingredient ing : currentRecipe.getIngredients()) {
                String dispName = !TextUtils.isEmpty(ing.getTranslatedName()) ? ing.getTranslatedName() : ing.getName();
                String dispUnit = !TextUtils.isEmpty(ing.getTranslatedUnit()) ? ing.getTranslatedUnit() : ing.getUnit();
                Recipe.Ingredient row = new Recipe.Ingredient(dispName, ing.getAmount(), dispUnit);
                row.setNumericAmount(ing.getNumericAmount());
                ingredientList.add(row);
            }
        } else {
            ingredientList.addAll(currentRecipe.getIngredients());
        }
        ingredientAdapter.notifyDataSetChanged();

        stepList.clear();
        if (editFormUsesTranslatedLayer) {
            for (Step s : currentRecipe.getStepList()) {
                String d = !TextUtils.isEmpty(s.getTranslatedDescription()) ? s.getTranslatedDescription() : s.getDescription();
                stepList.add(new Step(s.getOrder(), d, s.getTimerMinutes(), s.getImageUrl()));
            }
        } else {
            stepList.addAll(currentRecipe.getStepList());
        }
        stepAdapter.notifyDataSetChanged();

        List<String> selectedAllergens;
        if (editFormUsesTranslatedLayer && !currentRecipe.getTranslatedAllergens().isEmpty()) {
            selectedAllergens = new ArrayList<>(currentRecipe.getTranslatedAllergens());
        } else {
            selectedAllergens = new ArrayList<>(currentRecipe.getAllergens());
        }
        for (int i = 0; i < cgAllergens.getChildCount(); i++) {
            Chip chip = (Chip) cgAllergens.getChildAt(i);
            chip.setChecked(selectedAllergens.contains(chip.getText().toString()));
        }
        loadUserNoteIfEditing();
    }

    private void loadUserNoteIfEditing() {
        if (!isEditMode || currentRecipe == null || TextUtils.isEmpty(currentRecipe.getId()) || currentUser == null || etRecipeNotes == null) {
            return;
        }
        db.collection("recipes").document(currentRecipe.getId())
                .collection("notes").document(currentUser.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    if (isFinishing() || etRecipeNotes == null) {
                        return;
                    }
                    String text = "";
                    if (doc.exists()) {
                        String t = doc.getString("text");
                        if (t != null) {
                            text = t;
                        }
                    }
                    etRecipeNotes.setText(text);
                    scheduleLocalizeRecipeNoteForEditor(text);
                });
    }

    private void scheduleLocalizeRecipeNoteForEditor(String rawSnapshot) {
        if (TextUtils.isEmpty(rawSnapshot) || etRecipeNotes == null) {
            return;
        }
        final int job = ++recipeNoteLocalizeJobSeq;
        final String raw = rawSnapshot;
        final String uiLang = LocaleHelper.getLanguage(this);
        recipeNoteLocalizeExecutor.execute(() -> {
            try {
                String localized = CookbookDescriptionLocalizer.localizeSync(
                        getApplicationContext(), raw, uiLang);
                runOnUiThread(() -> {
                    if (isFinishing() || etRecipeNotes == null || job != recipeNoteLocalizeJobSeq) {
                        return;
                    }
                    String current = etRecipeNotes.getText() != null ? etRecipeNotes.getText().toString() : "";
                    if (!raw.equals(current)) {
                        return;
                    }
                    etRecipeNotes.setText(TextUtils.isEmpty(localized) ? raw : localized);
                });
            } catch (Exception ignored) {
            }
        });
    }

    private void persistUserNoteForRecipe(String recipeId, Runnable then) {
        if (TextUtils.isEmpty(recipeId) || currentUser == null || etRecipeNotes == null) {
            then.run();
            return;
        }
        String noteText = etRecipeNotes.getText() != null ? etRecipeNotes.getText().toString().trim() : "";
        Map<String, Object> data = new HashMap<>();
        data.put("text", noteText);
        data.put("updatedAt", System.currentTimeMillis());
        db.collection("recipes").document(recipeId)
                .collection("notes").document(currentUser.getUid())
                .set(data)
                .addOnCompleteListener(task -> {
                    if (!isFinishing() && !task.isSuccessful()) {
                        Toast.makeText(this, R.string.note_save_failed, Toast.LENGTH_SHORT).show();
                    }
                    then.run();
                });
    }

    private void saveRecipe() {
        String formTitle = etTitle.getText() != null ? etTitle.getText().toString().trim() : "";
        if (TextUtils.isEmpty(formTitle)) {
            etTitle.setError(getString(R.string.required_field));
            Toast.makeText(this, R.string.enter_recipe_title, Toast.LENGTH_SHORT).show();
            return;
        }

        btnSave.setEnabled(false);
        progressSave.setVisibility(View.VISIBLE);

        String formDescription = etDescription.getText() != null ? etDescription.getText().toString().trim() : "";
        String selectedCategoryLabel = actvCategory.getText() != null ? actvCategory.getText().toString().trim() : "";
        currentRecipe.setCategory(com.recipebookpro.util.CategoryLocalization.getCategoryValue(this, selectedCategoryLabel));
        
        int servings = 1;
        try {
            servings = Integer.parseInt(etServings.getText().toString().trim());
        } catch (Exception ignored) {}
        currentRecipe.setServings(servings);
        
        int calories = 0;
        try {
            calories = Integer.parseInt(etCalories.getText().toString().trim());
        } catch (Exception ignored) {}
        currentRecipe.setCalories(calories);

        // Filter out empty ingredients and validate amount/unit | Boş malzemeleri filtrele ve miktar/birim doğrula
        List<Recipe.Ingredient> validIngredients = new ArrayList<>();
        for (Recipe.Ingredient ing : ingredientList) {
            String name = ing.getName().trim();
            String amount = ing.getAmount().trim();
            String unit = ing.getUnit().trim();
            
            if (!TextUtils.isEmpty(name)) {
                if (TextUtils.isEmpty(amount)) {
                    Toast.makeText(this, getString(R.string.enter_amount_for_ingredient, name), Toast.LENGTH_SHORT).show();
                    btnSave.setEnabled(true);
                    progressSave.setVisibility(View.GONE);
                    return;
                }
                if (TextUtils.isEmpty(unit)) {
                    Toast.makeText(this, getString(R.string.select_unit_for_ingredient, name), Toast.LENGTH_SHORT).show();
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

        // Filter out empty steps and fix order | Boş adımları filtrele ve sırayı düzelt
        List<Step> validSteps = new ArrayList<>();
        int order = 1;
        for (Step step : stepList) {
            if (!TextUtils.isEmpty(step.getDescription().trim())) {
                step.setOrder(order++);
                validSteps.add(step);
            }
        }

        List<String> chipAllergens = new ArrayList<>();
        for (int i = 0; i < cgAllergens.getChildCount(); i++) {
            Chip chip = (Chip) cgAllergens.getChildAt(i);
            if (chip.isChecked()) {
                chipAllergens.add(chip.getText().toString());
            }
        }

        if (editFormUsesTranslatedLayer && snapIngredients != null && snapSteps != null && snapAllergens != null) {
            currentRecipe.setTitle(snapTitle != null ? snapTitle : currentRecipe.getTitle());
            currentRecipe.setTranslatedTitle(formTitle);
            currentRecipe.setDescription(snapDescription != null ? snapDescription : currentRecipe.getDescription());
            currentRecipe.setTranslatedDescription(formDescription);

            List<Recipe.Ingredient> mergedIngredients = new ArrayList<>();
            for (int i = 0; i < validIngredients.size(); i++) {
                Recipe.Ingredient form = validIngredients.get(i);
                Recipe.Ingredient snap = i < snapIngredients.size() ? snapIngredients.get(i) : null;
                mergedIngredients.add(mergeIngredientForTranslatedSave(form, snap));
            }
            currentRecipe.setIngredients(mergedIngredients);
            currentRecipe.buildIngredientNames();

            List<Step> mergedSteps = new ArrayList<>();
            for (int i = 0; i < validSteps.size(); i++) {
                Step form = validSteps.get(i);
                Step snap = i < snapSteps.size() ? snapSteps.get(i) : null;
                mergedSteps.add(mergeStepForTranslatedSave(form, snap));
            }
            currentRecipe.setStepList(mergedSteps);
            currentRecipe.setSteps(buildLegacyStepsText(mergedSteps));
            currentRecipe.setTranslatedInstructions(buildTranslatedInstructionsText(mergedSteps));

            currentRecipe.setAllergens(new ArrayList<>(snapAllergens));
            currentRecipe.setTranslatedAllergens(chipAllergens);
        } else {
            for (Recipe.Ingredient ing : validIngredients) {
                ing.clearTranslation();
            }
            for (Step s : validSteps) {
                s.setTranslatedDescription(null);
            }
            currentRecipe.setTitle(formTitle);
            currentRecipe.setDescription(formDescription);
            currentRecipe.setIngredients(validIngredients);
            currentRecipe.buildIngredientNames();
            currentRecipe.setStepList(validSteps);
            currentRecipe.setSteps(buildLegacyStepsText(validSteps));
            currentRecipe.setTranslatedInstructions(null);
            currentRecipe.setAllergens(chipAllergens);
            currentRecipe.setTranslatedAllergens(new ArrayList<>());

            String appLang = LocaleHelper.getLanguage(this);
            String orig = currentRecipe.getOriginalLanguage();
            if (!TextUtils.isEmpty(orig) && orig.equalsIgnoreCase(appLang)) {
                currentRecipe.clearAllTranslations();
            }
        }

        if (!isEditMode) {
            currentRecipe.setUserId(currentUser.getUid());
            currentRecipe.setCreatedAt(System.currentTimeMillis());
            currentRecipe.setPublic(true);
        }

        String docId = isEditMode ? currentRecipe.getId() : db.collection("recipes").document().getId();
        currentRecipe.setId(docId);

        proceedToSave(docId);
    }

    private void proceedToSave(String docId) {
        if (selectedImageUri != null && !selectedImageUri.toString().startsWith("http")) {
            uploadImageAndSave(docId);
        } else {
            saveToFirestore(docId);
        }
    }

    private void uploadImageAndSave(String docId) {
        // İstediğin dosya yapısı: recipes/{recipe_id}/main.jpg | Desired file structure: recipes/{recipe_id}/main.jpg
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
                Toast.makeText(this, getString(R.string.image_upload_failed_with_reason, e.getMessage()), Toast.LENGTH_LONG).show();
                // Yükleme başarısız olsa da tarif kaydedilsin (resimsiz olarak) | Save recipe even if upload fails (without image)
                saveToFirestore(docId);
            });
    }

    private void saveToFirestore(String docId) {
        db.collection("recipes").document(docId)
            .set(currentRecipe)
            .addOnSuccessListener(aVoid -> {
                persistUserNoteForRecipe(docId, () -> {
                    if (!isEditMode) {
                        NotificationTrigger.triggerNewRecipeFromUser(currentRecipe, currentUser.getDisplayName());
                    }

                    if (selectedCookbookId != null) {
                        String cookbookName = actvCookbook.getText().toString();
                        db.collection("cookbooks").document(selectedCookbookId)
                                .update("recipeIds", com.google.firebase.firestore.FieldValue.arrayUnion(docId))
                                .addOnCompleteListener(t -> {
                                    if (!isEditMode) {
                                        NotificationTrigger.triggerNewRecipeInCookbook(currentRecipe, selectedCookbookId, cookbookName);
                                    }
                                    Toast.makeText(this, R.string.recipe_saved, Toast.LENGTH_SHORT).show();
                                    finish();
                                });
                    } else {
                        Toast.makeText(this, R.string.recipe_saved, Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });
            })
            .addOnFailureListener(e -> {
                Toast.makeText(this, R.string.recipe_save_failed, Toast.LENGTH_SHORT).show();
                btnSave.setEnabled(true);
                progressSave.setVisibility(View.GONE);
            });
    }

}
