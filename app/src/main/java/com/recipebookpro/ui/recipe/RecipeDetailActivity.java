package com.recipebookpro.ui.recipe;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textview.MaterialTextView;
import com.google.firebase.firestore.FirebaseFirestore;
import com.recipebookpro.R;
import com.recipebookpro.model.Recipe;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class RecipeDetailActivity extends AppCompatActivity {

    public static final String EXTRA_RECIPE = "extra_recipe";

    private Recipe recipe;
    private FirebaseFirestore db;
    private View rootView;
    private CircularProgressIndicator progressIndicator;
    private MaterialButton btnDelete;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recipe_detail);

        recipe = (Recipe) getIntent().getSerializableExtra(EXTRA_RECIPE);
        if (recipe == null) {
            finish();
            return;
        }

        db = FirebaseFirestore.getInstance();
        rootView = findViewById(R.id.recipeDetailRoot);
        progressIndicator = findViewById(R.id.progressDeleteRecipe);
        btnDelete = findViewById(R.id.btnDeleteRecipe);

        MaterialToolbar toolbar = findViewById(R.id.toolbarDetail);
        toolbar.setNavigationOnClickListener(v -> finish());

        MaterialTextView tvTitle = findViewById(R.id.tvDetailTitle);
        Chip chipCategory = findViewById(R.id.chipDetailCategory);
        MaterialTextView tvDate = findViewById(R.id.tvDetailDate);
        MaterialTextView tvDescription = findViewById(R.id.tvDetailDescription);
        MaterialTextView tvIngredients = findViewById(R.id.tvDetailIngredients);
        MaterialTextView tvSteps = findViewById(R.id.tvDetailSteps);

        tvTitle.setText(recipe.getTitle());
        chipCategory.setText(recipe.getCategory());
        chipCategory.setVisibility(recipe.getCategory().isEmpty() ? View.GONE : View.VISIBLE);
        tvDescription.setText(recipe.getDescription().isEmpty()
                ? getString(R.string.no_description)
                : recipe.getDescription());
        tvIngredients.setText(recipe.getFormattedIngredients().isEmpty()
                ? getString(R.string.no_ingredients)
                : formatBullets(recipe.getFormattedIngredients()));
        tvSteps.setText(recipe.getSteps().isEmpty()
                ? getString(R.string.no_steps)
                : formatSteps(recipe.getSteps()));

        if (recipe.getCreatedAt() > 0) {
            tvDate.setText(new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                    .format(new Date(recipe.getCreatedAt())));
        }

        btnDelete.setOnClickListener(v -> confirmDelete());
    }

    private String formatBullets(String text) {
        StringBuilder builder = new StringBuilder();
        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            String cleaned = line.trim();
            if (cleaned.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append("• ").append(cleaned);
        }
        return builder.toString();
    }

    private String formatSteps(String text) {
        StringBuilder builder = new StringBuilder();
        String[] lines = text.split("\\r?\\n");
        int step = 1;
        for (String line : lines) {
            String cleaned = line.trim();
            if (cleaned.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append(step).append(". ")
                    .append(cleaned.replaceFirst("^[\\-•*\\d.)\\s]+", "").trim());
            step++;
        }
        return builder.length() == 0 ? text : builder.toString();
    }

    private void confirmDelete() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete)
                .setMessage(R.string.confirm_delete)
                .setPositiveButton(R.string.delete, (dialog, which) -> deleteRecipe())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void deleteRecipe() {
        if (TextUtils.isEmpty(recipe.getId())) {
            return;
        }
        progressIndicator.setVisibility(View.VISIBLE);
        btnDelete.setEnabled(false);
        db.collection("recipes").document(recipe.getId()).delete()
                .addOnCompleteListener(task -> {
                    progressIndicator.setVisibility(View.GONE);
                    btnDelete.setEnabled(true);
                    if (task.isSuccessful()) {
                        Snackbar.make(rootView, R.string.recipe_deleted, Snackbar.LENGTH_SHORT)
                                .addCallback(new Snackbar.Callback() {
                                    @Override
                                    public void onDismissed(Snackbar transientBottomBar, int event) {
                                        finish();
                                    }
                                })
                                .show();
                    } else {
                        Snackbar.make(rootView, R.string.recipe_delete_failed, Snackbar.LENGTH_SHORT).show();
                    }
                });
    }
}
