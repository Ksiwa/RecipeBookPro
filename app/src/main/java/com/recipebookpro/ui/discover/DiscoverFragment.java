package com.recipebookpro.ui.discover;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.widget.NestedScrollView;
import android.graphics.Rect;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textview.MaterialTextView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.recipebookpro.R;
import com.recipebookpro.model.Cookbook;
import com.recipebookpro.model.Recipe;
import com.recipebookpro.model.ShoppingList;
import com.recipebookpro.ui.discover.adapter.DiscoverRecipeAdapter;
import com.recipebookpro.ui.discover.adapter.DiscoverRecipeAdapter.ScoredRecipe;
import com.recipebookpro.ui.kitchen.CookbookDetailActivity;
import com.recipebookpro.ui.kitchen.adapter.CookbookAdapter;
import com.recipebookpro.ui.recipe.RecipeDetailActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DiscoverFragment extends Fragment
        implements DiscoverRecipeAdapter.OnDiscoverInteractionListener {

    private static final String[] DEFAULT_INGREDIENTS = {
            "tavuk", "soğan", "domates", "biber", "patates", "havuç",
            "sarımsak", "pirinç", "makarna", "un", "yumurta", "süt",
            "tereyağı", "zeytinyağı", "peynir", "limon", "tuz", "şeker",
            "salça", "mercimek", "nohut", "bulgur", "kabak", "patlıcan",
            "ispanak", "fasulye", "et", "kıyma", "balık"
    };

    private FirebaseFirestore db;
    private FirebaseUser currentUser;

    private ChipGroup chipGroupIngredients;
    private RecyclerView rvResults;
    private RecyclerView rvPublicCookbooks;
    private ProgressBar progress;
    private MaterialTextView tvEmpty, tvResultsTitle, tvPublicCookbooksLabel, tvEmptyPublicCookbooks;
    private TextInputEditText etSearch;

    private DiscoverRecipeAdapter adapter;
    private CookbookAdapter publicCookbookAdapter;
    private final List<ScoredRecipe> results = new ArrayList<>();
    private final List<Cookbook> publicCookbooks = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_discover, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        chipGroupIngredients = view.findViewById(R.id.chipGroupIngredients);
        rvResults = view.findViewById(R.id.rvDiscoverResults);
        rvPublicCookbooks = view.findViewById(R.id.rvPublicCookbooks);
        progress = view.findViewById(R.id.progressDiscover);
        tvEmpty = view.findViewById(R.id.tvDiscoverEmpty);
        tvResultsTitle = view.findViewById(R.id.tvResultsTitle);
        tvPublicCookbooksLabel = view.findViewById(R.id.tvPublicCookbooksLabel);
        tvEmptyPublicCookbooks = view.findViewById(R.id.tvEmptyPublicCookbooks);
        etSearch = view.findViewById(R.id.etDiscoverSearch);
        MaterialButton btnSearch = view.findViewById(R.id.btnSearchByIngredient);

        adapter = new DiscoverRecipeAdapter(results, this);
        rvResults.setLayoutManager(new LinearLayoutManager(getContext()));
        rvResults.setAdapter(adapter);

        publicCookbookAdapter = new CookbookAdapter(publicCookbooks, cookbook -> {
            Intent intent = new Intent(getContext(), CookbookDetailActivity.class);
            intent.putExtra(CookbookDetailActivity.EXTRA_COOKBOOK_ID, cookbook.getId());
            startActivity(intent);
        });
        rvPublicCookbooks.setLayoutManager(new GridLayoutManager(getContext(), 2));
        rvPublicCookbooks.setAdapter(publicCookbookAdapter);

        btnSearch.setOnClickListener(v -> performSearch());

        loadIngredientMasterList();
        loadPublicCookbooks();

        NestedScrollView nsvDiscover = view.findViewById(R.id.nsvDiscover);

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
        nsvDiscover.getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> {
            if (newFocus != null && (newFocus instanceof android.widget.EditText || newFocus instanceof android.widget.AutoCompleteTextView)) {
                nsvDiscover.postDelayed(() -> {
                    if (!isAdded()) return;
                    int[] viewPos = new int[2];
                    newFocus.getLocationOnScreen(viewPos);
                    int[] scrollPos = new int[2];
                    nsvDiscover.getLocationOnScreen(scrollPos);
                    int relativeTop = viewPos[1] - scrollPos[1];
                    nsvDiscover.smoothScrollBy(0, relativeTop - 100);
                }, 200);
            }
        });
    }

    private void loadPublicCookbooks() {
        Set<String> seenIds = new HashSet<>();
        publicCookbooks.clear();
        final int[] pending = {2};

        for (String fieldName : new String[]{"isPublic", "public"}) {
            db.collection("cookbooks")
                    .whereEqualTo(fieldName, true)
                    .get()
                    .addOnSuccessListener(snap -> {
                        if (!isAdded()) return;
                        for (QueryDocumentSnapshot doc : snap) {
                            Cookbook book = Cookbook.fromDocument(doc);
                            if (seenIds.add(book.getId()) &&
                                (currentUser == null || !currentUser.getUid().equals(book.getUserId()))) {
                                publicCookbooks.add(book);
                            }
                        }
                        pending[0]--;
                        if (pending[0] == 0) {
                            Collections.sort(publicCookbooks, (b1, b2) -> Long.compare(b2.getCreatedAt(), b1.getCreatedAt()));
                            publicCookbookAdapter.notifyDataSetChanged();
                            tvEmptyPublicCookbooks.setVisibility(publicCookbooks.isEmpty() ? View.VISIBLE : View.GONE);
                        }
                    })
                    .addOnFailureListener(e -> {
                        pending[0]--;
                        if (pending[0] == 0 && isAdded()) {
                            publicCookbookAdapter.notifyDataSetChanged();
                            tvEmptyPublicCookbooks.setVisibility(publicCookbooks.isEmpty() ? View.VISIBLE : View.GONE);
                        }
                    });
        }
    }

    private void loadIngredientMasterList() {
        db.collection("ingredients_master").document("master").get()
                .addOnSuccessListener(doc -> {
                    if (!isAdded()) return;
                    List<String> names = null;
                    if (doc.exists()) {
                        Object raw = doc.get("names");
                        if (raw instanceof List<?>) {
                            names = new ArrayList<>();
                            for (Object item : (List<?>) raw) {
                                if (item != null) names.add(String.valueOf(item));
                            }
                        }
                    }
                    if (names == null || names.isEmpty()) {
                        names = new ArrayList<>();
                        Collections.addAll(names, DEFAULT_INGREDIENTS);
                    }
                    populateIngredientChips(names);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    List<String> fallback = new ArrayList<>();
                    Collections.addAll(fallback, DEFAULT_INGREDIENTS);
                    populateIngredientChips(fallback);
                });
    }

    private void populateIngredientChips(List<String> ingredients) {
        chipGroupIngredients.removeAllViews();
        for (String name : ingredients) {
            Chip chip = new Chip(requireContext());
            chip.setText(name);
            chip.setCheckable(true);
            chip.setCheckedIconVisible(true);
            chipGroupIngredients.addView(chip);
        }
    }

    private List<String> getSelectedIngredients() {
        List<String> selected = new ArrayList<>();
        for (int i = 0; i < chipGroupIngredients.getChildCount(); i++) {
            Chip chip = (Chip) chipGroupIngredients.getChildAt(i);
            if (chip.isChecked()) {
                selected.add(chip.getText().toString().toLowerCase().trim());
            }
        }
        return selected;
    }

    private void performSearch() {
        List<String> selected = getSelectedIngredients();
        String textQuery = etSearch.getText() != null ? etSearch.getText().toString().trim() : "";

        progress.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
        rvResults.setVisibility(View.GONE);
        tvResultsTitle.setVisibility(View.GONE);

        loadAllPublicRecipes(selected, textQuery);
    }

    private void loadAllPublicRecipes(List<String> selectedIngredients, String textQuery) {
        Set<String> collectedRecipeIds = new LinkedHashSet<>();
        List<Recipe> allRecipes = new ArrayList<>();
        // 2 queries for recipes (isPublic + public) + 2 for cookbooks (isPublic + public)
        final int[] pending = {4};

        Runnable checkDone = () -> {
            pending[0]--;
            if (pending[0] == 0) filterAndDisplay(allRecipes, selectedIngredients, textQuery);
        };

        for (String field : new String[]{"isPublic", "public"}) {
            db.collection("recipes")
                    .whereEqualTo(field, true)
                    .get()
                    .addOnSuccessListener(snap -> {
                        for (DocumentSnapshot doc : snap.getDocuments()) {
                            if (collectedRecipeIds.add(doc.getId())) {
                                allRecipes.add(Recipe.fromDocument(doc));
                            }
                        }
                        checkDone.run();
                    })
                    .addOnFailureListener(e -> checkDone.run());
        }

        for (String field : new String[]{"isPublic", "public"}) {
            db.collection("cookbooks")
                    .whereEqualTo(field, true)
                    .get()
                    .addOnSuccessListener(cookbookSnap -> {
                        List<String> recipeIds = new ArrayList<>();
                        for (QueryDocumentSnapshot doc : cookbookSnap) {
                            Cookbook book = Cookbook.fromDocument(doc);
                            if (book.getRecipeIds() != null) {
                                for (String rid : book.getRecipeIds()) {
                                    if (!collectedRecipeIds.contains(rid)) {
                                        recipeIds.add(rid);
                                        collectedRecipeIds.add(rid);
                                    }
                                }
                            }
                        }

                        if (recipeIds.isEmpty()) {
                            checkDone.run();
                            return;
                        }

                        List<List<String>> chunks = partition(recipeIds, 10);
                        final int[] chunkPending = {chunks.size()};

                        for (List<String> chunk : chunks) {
                            db.collection("recipes")
                                    .whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk)
                                    .get()
                                    .addOnSuccessListener(recipeSnap -> {
                                        for (DocumentSnapshot doc : recipeSnap.getDocuments()) {
                                            if (collectedRecipeIds.add(doc.getId())) {
                                                allRecipes.add(Recipe.fromDocument(doc));
                                            }
                                        }
                                        chunkPending[0]--;
                                        if (chunkPending[0] == 0) checkDone.run();
                                    })
                                    .addOnFailureListener(e -> {
                                        chunkPending[0]--;
                                        if (chunkPending[0] == 0) checkDone.run();
                                    });
                        }
                    })
                    .addOnFailureListener(e -> checkDone.run());
        }
    }

    private void filterAndDisplay(List<Recipe> allRecipes, List<String> selectedIngredients, String textQuery) {
        if (!isAdded()) return;

        List<Recipe> filtered = new ArrayList<>();
        Set<String> selectedSet = new HashSet<>(selectedIngredients);

        for (Recipe r : allRecipes) {
            Set<String> recipeIngs = getRecipeIngredientNames(r);

            if (!selectedSet.isEmpty()) {
                boolean hasAny = false;
                for (String sel : selectedSet) {
                    for (String ri : recipeIngs) {
                        if (ri.contains(sel) || sel.contains(ri)) {
                            hasAny = true;
                            break;
                        }
                    }
                    if (hasAny) break;
                }
                if (!hasAny) continue;
            }

            if (!textQuery.isEmpty()) {
                String q = textQuery.toLowerCase();
                if (!r.getTitle().toLowerCase().contains(q) &&
                    !r.getDescription().toLowerCase().contains(q)) {
                    continue;
                }
            }

            filtered.add(r);
        }

        scoreAndDisplay(filtered, selectedIngredients);
    }

    private Set<String> getRecipeIngredientNames(Recipe r) {
        Set<String> names = new HashSet<>();
        for (String name : r.getIngredientNames()) {
            names.add(name.toLowerCase().trim());
        }
        if (r.getIngredients() != null) {
            for (Recipe.Ingredient ing : r.getIngredients()) {
                names.add(ing.getName().toLowerCase().trim());
            }
        }
        return names;
    }

    private void scoreAndDisplay(List<Recipe> recipes, List<String> selectedIngredients) {
        results.clear();
        Set<String> selectedSet = new HashSet<>(selectedIngredients);

        for (Recipe recipe : recipes) {
            Set<String> recipeIngs = getRecipeIngredientNames(recipe);

            int matchPercent;
            List<String> missing = new ArrayList<>();

            if (selectedSet.isEmpty()) {
                matchPercent = 100;
            } else {
                int matched = 0;
                for (String ri : recipeIngs) {
                    boolean found = false;
                    for (String sel : selectedSet) {
                        if (ri.contains(sel) || sel.contains(ri)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        missing.add(ri);
                    } else {
                        matched++;
                    }
                }
                matchPercent = recipeIngs.isEmpty() ? 0 :
                        (int) ((double) matched / recipeIngs.size() * 100);
            }

            results.add(new ScoredRecipe(recipe, matchPercent, missing));
        }

        results.sort((a, b) -> Integer.compare(b.matchPercent, a.matchPercent));

        progress.setVisibility(View.GONE);
        if (results.isEmpty()) {
            tvEmpty.setText("Sonuç bulunamadı");
            tvEmpty.setVisibility(View.VISIBLE);
            rvResults.setVisibility(View.GONE);
            tvResultsTitle.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            tvResultsTitle.setVisibility(View.VISIBLE);
            tvResultsTitle.setText("Sonuçlar (" + results.size() + ")");
            rvResults.setVisibility(View.VISIBLE);
        }
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onRecipeClick(Recipe recipe) {
        Intent intent = new Intent(getContext(), RecipeDetailActivity.class);
        intent.putExtra(RecipeDetailActivity.EXTRA_RECIPE, recipe);
        startActivity(intent);
    }

    @Override
    public void onAddMissingToShopping(Recipe recipe, List<String> missing) {
        if (currentUser == null || missing == null || missing.isEmpty()) return;

        ShoppingList list = new ShoppingList(currentUser.getUid(),
                recipe.getTitle() + " - Eksik Malzemeler");
        List<ShoppingList.ShoppingItem> items = new ArrayList<>();
        for (String name : missing) {
            items.add(new ShoppingList.ShoppingItem(name, "", ""));
        }
        list.setItems(items);

        db.collection("shopping_lists").add(list)
                .addOnSuccessListener(ref ->
                        Toast.makeText(getContext(), "Eksikler alışveriş listesine eklendi", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Hata oluştu", Toast.LENGTH_SHORT).show());
    }

    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> parts = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            parts.add(new ArrayList<>(list.subList(i, Math.min(list.size(), i + size))));
        }
        return parts;
    }
}
