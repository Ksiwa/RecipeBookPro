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
    private AutoCompleteTextView actvCategory;
    private ChipGroup cgAllergens;
    private RecyclerView rvIngredientsEdit, rvStepsEdit;
    private MaterialButton btnSave;
    private CircularProgressIndicator progressSave;

    // Data lists
    private List<Recipe.Ingredient> ingredientList = new ArrayList<>();
    private List<Step> stepList = new ArrayList<>();
    private EditableIngredientAdapter ingredientAdapter;
    private EditableStepAdapter stepAdapter;
    private ItemTouchHelper itemTouchHelper;

    // Image Picker
    private Uri selectedImageUri = null;
    private final ActivityResultLauncher<String> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    try {
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

        applyInsetsToView(findViewById(R.id.addEditRoot));

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
        findViewById(R.id.toolbarAddEdit).setOnClickListener(v -> finish());

        ivPreview = findViewById(R.id.ivPreview);
        llImagePlaceholder = findViewById(R.id.llImagePlaceholder);
        etTitle = findViewById(R.id.etTitle);
        etDescription = findViewById(R.id.etDescription);
        etServings = findViewById(R.id.etServings);
        actvCategory = findViewById(R.id.actvCategory);
        cgAllergens = findViewById(R.id.cgAllergens);
        rvIngredientsEdit = findViewById(R.id.rvIngredientsEdit);
        rvStepsEdit = findViewById(R.id.rvStepsEdit);
        btnSave = findViewById(R.id.btnSave);
        progressSave = findViewById(R.id.progressSave);

        findViewById(R.id.btnGallery).setOnClickListener(v -> galleryLauncher.launch("image/*"));
        // Camera intent skipped for brevity, could use ActivityResultContracts.TakePicture()

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
            Chip chip = new Chip(this);
            chip.setText(tag);
            chip.setCheckable(true);
            chip.setCheckedIconVisible(true);
            cgAllergens.addView(chip);
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

        // Filter out empty ingredients
        List<Recipe.Ingredient> validIngredients = new ArrayList<>();
        for (Recipe.Ingredient ing : ingredientList) {
            if (!TextUtils.isEmpty(ing.getName().trim())) {
                validIngredients.add(ing);
            }
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
            currentRecipe.setPublic(true); // default to public or add a switch
        }

        if (selectedImageUri != null) {
            currentRecipe.setImageUrl(selectedImageUri.toString());
        }
        
        String docId = isEditMode ? currentRecipe.getId() : db.collection("recipes").document().getId();
        currentRecipe.setId(docId);

        db.collection("recipes").document(docId)
            .set(currentRecipe)
            .addOnSuccessListener(aVoid -> {
                Toast.makeText(this, R.string.recipe_saved, Toast.LENGTH_SHORT).show();
                finish();
            })
            .addOnFailureListener(e -> {
                Toast.makeText(this, R.string.recipe_save_failed, Toast.LENGTH_SHORT).show();
                btnSave.setEnabled(true);
                progressSave.setVisibility(View.GONE);
            });
    }
}
