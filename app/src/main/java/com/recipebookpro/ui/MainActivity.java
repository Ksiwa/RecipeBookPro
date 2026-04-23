package com.recipebookpro.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textview.MaterialTextView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.recipebookpro.R;
import com.recipebookpro.adapter.RecipeAdapter;
import com.recipebookpro.model.Recipe;
import com.recipebookpro.ui.auth.LoginActivity;
import com.recipebookpro.ui.book.BookReaderActivity;
import com.recipebookpro.ui.recipe.AddRecipeActivity;
import com.recipebookpro.ui.recipe.RecipeDetailActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements RecipeAdapter.OnRecipeClickListener {

    private static final String FILTER_ALL = "__ALL__";

    private RecyclerView rvRecipes;
    private MaterialTextView tvEmpty;
    private MaterialTextView tvSubtitle;
    private ChipGroup chipGroupCategories;
    private CircularProgressIndicator progressIndicator;
    private View rootView;
    private RecipeAdapter adapter;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private ListenerRegistration recipeListener;
    private final List<Recipe> allRecipes = new ArrayList<>();
    private String selectedCategory = FILTER_ALL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        rootView = findViewById(R.id.mainRoot);

        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        setSupportActionBar(toolbar);

        tvSubtitle = findViewById(R.id.tvSubtitle);
        tvEmpty = findViewById(R.id.tvEmpty);
        rvRecipes = findViewById(R.id.rvRecipes);
        chipGroupCategories = findViewById(R.id.chipGroupCategories);
        progressIndicator = findViewById(R.id.progressRecipes);
        MaterialButton btnLogout = findViewById(R.id.btnLogout);
        FloatingActionButton fabAdd = findViewById(R.id.fabAddRecipe);
        ExtendedFloatingActionButton btnOpenBook = findViewById(R.id.btnOpenBook);

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            tvSubtitle.setText(currentUser.getEmail());
        }

        adapter = new RecipeAdapter(this);
        rvRecipes.setLayoutManager(new LinearLayoutManager(this));
        rvRecipes.setAdapter(adapter);

        setupCategoryFilters();

        btnLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(this, LoginActivity.class));
            finishAffinity();
        });

        fabAdd.setOnClickListener(v -> startActivity(new Intent(this, AddRecipeActivity.class)));
        btnOpenBook.setOnClickListener(v -> startActivity(new Intent(this, BookReaderActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        listenForRecipes();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (recipeListener != null) {
            recipeListener.remove();
            recipeListener = null;
        }
    }

    private void setupCategoryFilters() {
        chipGroupCategories.removeAllViews();
        addCategoryChip(getString(R.string.category_all), FILTER_ALL, true);
        String[] categories = getResources().getStringArray(R.array.recipe_categories);
        for (String category : categories) {
            addCategoryChip(category, category, false);
        }
    }

    private void addCategoryChip(String text, String value, boolean checked) {
        Chip chip = (Chip) LayoutInflater.from(this)
                .inflate(R.layout.item_category_chip, chipGroupCategories, false);
        chip.setId(View.generateViewId());
        chip.setText(text);
        chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                selectedCategory = value;
                applyFilter();
            }
        });
        chipGroupCategories.addView(chip);
        chip.setChecked(checked);
    }

    private void listenForRecipes() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            return;
        }

        if (recipeListener != null) {
            recipeListener.remove();
        }

        allRecipes.clear();
        adapter.clearRecipes();
        showLoading(true);

        recipeListener = db.collection("recipes")
                .whereEqualTo("userId", currentUser.getUid())
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    showLoading(false);
                    if (error != null || snapshots == null) {
                        Snackbar.make(rootView, R.string.recipes_load_failed, Snackbar.LENGTH_LONG).show();
                        updateEmptyState(new ArrayList<>());
                        return;
                    }

                    allRecipes.clear();
                    Set<String> addedIds = new HashSet<>();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        if (addedIds.add(doc.getId())) {
                            allRecipes.add(Recipe.fromDocument(doc));
                        }
                    }
                    applyFilter();
                });
    }

    private void applyFilter() {
        List<Recipe> filteredRecipes = new ArrayList<>();
        for (Recipe recipe : allRecipes) {
            if (FILTER_ALL.equals(selectedCategory)
                    || selectedCategory.equalsIgnoreCase(recipe.getCategory())) {
                filteredRecipes.add(recipe);
            }
        }
        adapter.setRecipeList(filteredRecipes);
        updateEmptyState(filteredRecipes);
    }

    private void updateEmptyState(List<Recipe> recipes) {
        boolean isEmpty = recipes == null || recipes.isEmpty();
        tvEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        rvRecipes.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        tvEmpty.setText(FILTER_ALL.equals(selectedCategory)
                ? getString(R.string.no_recipes)
                : getString(R.string.no_recipes_for_category, selectedCategory));
    }

    private void showLoading(boolean isLoading) {
        progressIndicator.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        if (isLoading) {
            tvEmpty.setVisibility(View.GONE);
            rvRecipes.setVisibility(View.GONE);
        }
    }

    @Override
    public void onRecipeClick(Recipe recipe) {
        Intent intent = new Intent(this, RecipeDetailActivity.class);
        intent.putExtra(RecipeDetailActivity.EXTRA_RECIPE, recipe);
        startActivity(intent);
    }
}
