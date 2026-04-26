package com.recipebookpro.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
import com.recipebookpro.ui.book.BookReaderActivity;
import com.recipebookpro.ui.recipe.RecipeAddEditActivity;
import com.recipebookpro.ui.recipe.RecipeDetailActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HomeFragment extends Fragment implements RecipeAdapter.OnRecipeClickListener {

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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        rootView = view.findViewById(R.id.homeRoot);

        tvSubtitle = view.findViewById(R.id.tvSubtitle);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        rvRecipes = view.findViewById(R.id.rvRecipes);
        chipGroupCategories = view.findViewById(R.id.chipGroupCategories);
        progressIndicator = view.findViewById(R.id.progressRecipes);
        FloatingActionButton fabAdd = view.findViewById(R.id.fabAddRecipe);
        ExtendedFloatingActionButton btnOpenBook = view.findViewById(R.id.btnOpenBook);

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            tvSubtitle.setText(currentUser.getEmail());
        }

        adapter = new RecipeAdapter(this);
        rvRecipes.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvRecipes.setAdapter(adapter);

        setupCategoryFilters();

        fabAdd.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), RecipeAddEditActivity.class)));
        btnOpenBook.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), BookReaderActivity.class)));
    }

    @Override
    public void onResume() {
        super.onResume();
        listenForRecipes();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
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
        Chip chip = (Chip) LayoutInflater.from(requireContext())
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
                .addSnapshotListener((snapshots, error) -> {
                    showLoading(false);
                    if (error != null || snapshots == null) {
                        if (rootView != null) {
                            Snackbar.make(rootView, R.string.recipes_load_failed,
                                    Snackbar.LENGTH_LONG).show();
                        }
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
                    
                    // Local sort to avoid Firestore index requirement
                    java.util.Collections.sort(allRecipes, (r1, r2) -> Long.compare(r2.getCreatedAt(), r1.getCreatedAt()));
                    
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
        Intent intent = new Intent(requireContext(), RecipeDetailActivity.class);
        intent.putExtra(RecipeDetailActivity.EXTRA_RECIPE, recipe);
        startActivity(intent);
    }
}
