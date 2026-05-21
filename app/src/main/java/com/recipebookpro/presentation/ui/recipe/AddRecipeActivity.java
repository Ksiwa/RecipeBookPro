package com.recipebookpro.presentation.ui.recipe;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Scroller;

import com.recipebookpro.presentation.ui.BaseActivity;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.recipebookpro.R;
import com.recipebookpro.domain.model.Recipe;

import java.util.ArrayList;
import java.util.List;

public class AddRecipeActivity extends BaseActivity {

    private TextInputLayout tilTitle;
    private TextInputLayout tilCategory;
    private TextInputLayout tilSteps;
    private TextInputEditText etTitle;
    private MaterialAutoCompleteTextView actCategory;
    private TextInputEditText etDescription;
    private TextInputEditText etSteps;
    private LinearLayout layoutIngredients;
    private NestedScrollView scrollView;
    private MaterialButton btnAddIngredient;
    private MaterialButton btnSave;
    private CircularProgressIndicator progressIndicator;
    private View rootView;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private String[] categories;
    private String[] units;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_recipe);

        applyInsetsToView(findViewById(R.id.addRecipeRoot));

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        categories = getResources().getStringArray(R.array.recipe_category_labels);
        units = getResources().getStringArray(R.array.ingredient_units);

        rootView = findViewById(R.id.addRecipeRoot);
        scrollView = findViewById(R.id.scrollAddRecipe);
        MaterialToolbar toolbar = findViewById(R.id.toolbarAddRecipe);
        toolbar.setNavigationOnClickListener(v -> finish());

        tilTitle = findViewById(R.id.tilRecipeTitle);
        tilCategory = findViewById(R.id.tilRecipeCategory);
        tilSteps = findViewById(R.id.tilRecipeSteps);
        etTitle = findViewById(R.id.etRecipeTitle);
        RecipeTitleInputConfigurator.configure(etTitle);
        actCategory = findViewById(R.id.actRecipeCategory);
        etDescription = findViewById(R.id.etRecipeDescription);
        etSteps = findViewById(R.id.etRecipeSteps);
        layoutIngredients = findViewById(R.id.layoutIngredients);
        btnAddIngredient = findViewById(R.id.btnAddIngredient);
        btnSave = findViewById(R.id.btnSaveRecipe);
        progressIndicator = findViewById(R.id.progressSaveRecipe);

        actCategory.setSimpleItems(categories);
        configureStableInput(etDescription);
        configureStableInput(etSteps);
        btnAddIngredient.setOnClickListener(v -> addIngredientRow(null));
        btnSave.setOnClickListener(v -> saveRecipe());

        addIngredientRow(null);
    }

    private void configureStableInput(TextInputEditText editText) {
        editText.setScroller(new Scroller(this));
        editText.setVerticalScrollBarEnabled(true);
        editText.setHorizontallyScrolling(false);
        editText.setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                scrollView.requestDisallowInterceptTouchEvent(true);
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                scrollView.requestDisallowInterceptTouchEvent(false);
            }
            return false;
        });
    }

    private void addIngredientRow(Recipe.Ingredient ingredient) {
        View ingredientView = LayoutInflater.from(this)
                .inflate(R.layout.ingredient_item, layoutIngredients, false);

        TextInputLayout tilIngredientName = ingredientView.findViewById(R.id.tilIngredientName);
        TextInputEditText etIngredientName = ingredientView.findViewById(R.id.etIngredientName);
        TextInputEditText etIngredientAmount = ingredientView.findViewById(R.id.etIngredientAmount);
        MaterialAutoCompleteTextView actUnit = ingredientView.findViewById(R.id.actIngredientUnit);
        View btnRemove = ingredientView.findViewById(R.id.btnRemoveIngredient);

        ArrayAdapter<String> unitAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                units
        );
        actUnit.setAdapter(unitAdapter);

        if (ingredient != null) {
            etIngredientName.setText(ingredient.getName());
            etIngredientAmount.setText(ingredient.getAmount());
            actUnit.setText(ingredient.getUnit(), false);
        }

        btnRemove.setOnClickListener(v -> {
            if (layoutIngredients.getChildCount() == 1) {
                tilIngredientName.setError(getString(R.string.at_least_one_ingredient));
                return;
            }
            layoutIngredients.removeView(ingredientView);
        });

        layoutIngredients.addView(ingredientView);
    }

    private void saveRecipe() {
        clearErrors();

        String title = getText(etTitle);
        String categoryLabel = getText(actCategory);
        String category = com.recipebookpro.util.CategoryLocalization.getCategoryValue(this, categoryLabel);
        String description = getText(etDescription);
        String steps = getText(etSteps);
        List<Recipe.Ingredient> ingredients = collectIngredients();

        boolean hasError = false;
        if (TextUtils.isEmpty(title)) {
            tilTitle.setError(getString(R.string.required_field));
            hasError = true;
        }
        if (TextUtils.isEmpty(category)) {
            tilCategory.setError(getString(R.string.required_field));
            hasError = true;
        }
        if (ingredients.isEmpty()) {
            showMessage(R.string.ingredient_required_message);
            hasError = true;
        }
        if (TextUtils.isEmpty(steps)) {
            tilSteps.setError(getString(R.string.required_field));
            hasError = true;
        }
        if (hasError) {
            return;
        }

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            showMessage(R.string.login_failed);
            return;
        }

        setLoading(true);

        String docId = db.collection("recipes").document().getId();
        Recipe recipe = new Recipe(
                docId,
                title,
                description,
                category,
                ingredients,
                steps,
                currentUser.getUid(),
                System.currentTimeMillis()
        );

        db.collection("recipes").document(docId).set(recipe)
                .addOnCompleteListener(task -> {
                    setLoading(false);
                    if (task.isSuccessful()) {
                        Snackbar.make(rootView, R.string.recipe_saved, Snackbar.LENGTH_SHORT)
                                .addCallback(new Snackbar.Callback() {
                                    @Override
                                    public void onDismissed(Snackbar transientBottomBar, int event) {
                                        finish();
                                    }
                                })
                                .show();
                    } else {
                        showMessage(R.string.recipe_save_failed);
                    }
                });
    }

    private List<Recipe.Ingredient> collectIngredients() {
        List<Recipe.Ingredient> ingredients = new ArrayList<>();
        for (int i = 0; i < layoutIngredients.getChildCount(); i++) {
            View row = layoutIngredients.getChildAt(i);
            TextInputLayout tilName = row.findViewById(R.id.tilIngredientName);
            TextInputLayout tilAmount = row.findViewById(R.id.tilIngredientAmount);
            TextInputLayout tilUnit = row.findViewById(R.id.tilIngredientUnit);
            TextInputEditText etName = row.findViewById(R.id.etIngredientName);
            TextInputEditText etAmount = row.findViewById(R.id.etIngredientAmount);
            MaterialAutoCompleteTextView actUnit = row.findViewById(R.id.actIngredientUnit);

            String name = getText(etName);
            String amount = getText(etAmount);
            String unit = getText(actUnit);

            tilName.setError(null);
            tilAmount.setError(null);
            tilUnit.setError(null);

            if (TextUtils.isEmpty(name) && TextUtils.isEmpty(amount) && TextUtils.isEmpty(unit)) {
                continue;
            }

            boolean rowHasError = false;
            if (TextUtils.isEmpty(name)) {
                tilName.setError(getString(R.string.required_field));
                rowHasError = true;
            }
            if (TextUtils.isEmpty(amount)) {
                tilAmount.setError(getString(R.string.required_field));
                rowHasError = true;
            }
            if (TextUtils.isEmpty(unit)) {
                tilUnit.setError(getString(R.string.required_field));
                rowHasError = true;
            }

            if (!rowHasError) {
                ingredients.add(new Recipe.Ingredient(name, amount, unit));
            }
        }
        return ingredients;
    }

    private void clearErrors() {
        tilTitle.setError(null);
        tilCategory.setError(null);
        tilSteps.setError(null);
    }

    private void setLoading(boolean isLoading) {
        progressIndicator.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        btnSave.setEnabled(!isLoading);
        btnAddIngredient.setEnabled(!isLoading);
    }

    private String getText(TextInputEditText editText) {
        return editText.getText() != null ? editText.getText().toString().trim() : "";
    }

    private String getText(MaterialAutoCompleteTextView textView) {
        return textView.getText() != null ? textView.getText().toString().trim() : "";
    }

    private void showMessage(int messageRes) {
        Snackbar.make(rootView, messageRes, Snackbar.LENGTH_SHORT).show();
    }
}
