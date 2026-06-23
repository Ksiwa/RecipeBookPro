package com.recipebookpro.presentation.ui.discover;

import android.content.Intent;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.recipebookpro.R;
import com.recipebookpro.data.remote.MLKitTranslationService;
import com.recipebookpro.domain.model.Cookbook;
import com.recipebookpro.domain.model.Recipe;
import com.recipebookpro.domain.model.User;
import com.recipebookpro.domain.service.TranslationService;
import com.recipebookpro.presentation.ui.discover.adapter.DiscoverRecipeAdapter;
import com.recipebookpro.presentation.ui.discover.adapter.DiscoverRecipeAdapter.ScoredRecipe;
import com.recipebookpro.presentation.ui.kitchen.CookbookDetailActivity;
import com.recipebookpro.presentation.ui.kitchen.adapter.CookbookAdapter;
import com.recipebookpro.presentation.ui.recipe.RecipeDetailActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class DiscoverFragment extends Fragment implements DiscoverRecipeAdapter.OnDiscoverInteractionListener, CookbookAdapter.OnCookbookClickListener {

    private EditText etSearch;
    private ChipGroup chipGroupIngredients;
    private RecyclerView rvDiscoverResults, rvPublicCookbooks;
    private ProgressBar progressDiscover;
    private TextView tvDiscoverEmpty, tvResultsTitle;
    private NestedScrollView nsvDiscover;
    private View hsvRecipeSort, layoutPublicCookbooksHeader;
    private ChipGroup chipGroupRecipeSort;
    private MaterialButton btnViewAllCookbooks;

    private FirebaseFirestore db;
    private DiscoverRecipeAdapter adapter;
    private CookbookAdapter cookbookAdapter;
    private final List<ScoredRecipe> results = new ArrayList<>();
    private final List<Cookbook> publicCookbooks = new ArrayList<>();
    private final Map<String, User> ownersById = new HashMap<>();
    private final Map<Integer, List<String>> ingredientSearchTermsByChipId = new HashMap<>();
    private final Map<String, List<String>> ingredientSearchTermsByTerm = new HashMap<>();
    private boolean firstResume = true;

    private TranslationService translationService;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_discover, container, false);

        db = FirebaseFirestore.getInstance();
        translationService = new MLKitTranslationService(requireContext());

        etSearch = view.findViewById(R.id.etDiscoverSearch);
        MaterialButton btnSearch = view.findViewById(R.id.btnSearchByIngredient);
        chipGroupIngredients = view.findViewById(R.id.chipGroupIngredients);
        rvDiscoverResults = view.findViewById(R.id.rvDiscoverResults);
        rvPublicCookbooks = view.findViewById(R.id.rvPublicCookbooks);
        progressDiscover = view.findViewById(R.id.progressDiscover);
        tvDiscoverEmpty = view.findViewById(R.id.tvDiscoverEmpty);
        tvResultsTitle = view.findViewById(R.id.tvResultsTitle);
        nsvDiscover = view.findViewById(R.id.nsvDiscover);
        btnViewAllCookbooks = view.findViewById(R.id.btnViewAllCookbooks);
        layoutPublicCookbooksHeader = view.findViewById(R.id.layoutPublicCookbooksHeader);
        hsvRecipeSort = view.findViewById(R.id.hsvRecipeSort);
        chipGroupRecipeSort = view.findViewById(R.id.chipGroupRecipeSort);

        btnViewAllCookbooks.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), PublicCookbooksActivity.class);
            startActivity(intent);
        });

        chipGroupRecipeSort.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!results.isEmpty()) {
                sortRecipesBySelection();
            }
        });

        // Keyboard "Search" action trigger
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                performSearch();
                return true;
            }
            return false;
        });

        adapter = new DiscoverRecipeAdapter(results, this);
        rvDiscoverResults.setLayoutManager(new LinearLayoutManager(getContext()));
        rvDiscoverResults.setAdapter(adapter);

        cookbookAdapter = new CookbookAdapter(publicCookbooks, this);
        cookbookAdapter.setHorizontal(true);
        rvPublicCookbooks.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        rvPublicCookbooks.setAdapter(cookbookAdapter);

        btnSearch.setOnClickListener(v -> performSearch());

        populateIngredients();
        loadAllPublicRecipes(new ArrayList<>(), "", "");
        return view;
    }

    private void populateIngredients() {
        String[] commonIngredients = getResources().getStringArray(R.array.discover_default_ingredients);
        String[] turkishIngredients = getDiscoverDefaultIngredientsForLanguage("tr");
        String[] englishIngredients = getDiscoverDefaultIngredientsForLanguage("en");
        ingredientSearchTermsByChipId.clear();
        ingredientSearchTermsByTerm.clear();

        for (int i = 0; i < commonIngredients.length; i++) {
            String ing = commonIngredients[i];
            Chip chip = new Chip(requireContext());
            chip.setId(View.generateViewId());
            // Capitalize first letter for UI
            String capitalized = ing.substring(0, 1).toUpperCase() + ing.substring(1);
            chip.setText(capitalized);
            chip.setCheckable(true);
            chip.setClickable(true);
            chip.setOnCheckedChangeListener((buttonView, isChecked) -> performSearch());
            List<String> searchTerms = buildIngredientSearchTerms(ing, turkishIngredients, englishIngredients, i);
            ingredientSearchTermsByChipId.put(chip.getId(), searchTerms);
            for (String term : searchTerms) {
                ingredientSearchTermsByTerm.put(term, searchTerms);
            }
            chipGroupIngredients.addView(chip);
        }
    }

    private String[] getDiscoverDefaultIngredientsForLanguage(String languageCode) {
        Configuration configuration = new Configuration(getResources().getConfiguration());
        configuration.setLocale(new Locale(languageCode));
        Context localizedContext = requireContext().createConfigurationContext(configuration);
        return localizedContext.getResources().getStringArray(R.array.discover_default_ingredients);
    }

    private List<String> buildIngredientSearchTerms(
            String visibleIngredient,
            String[] turkishIngredients,
            String[] englishIngredients,
            int index
    ) {
        Set<String> terms = new LinkedHashSet<>();
        addSearchTerm(terms, visibleIngredient);
        if (index < turkishIngredients.length) {
            addSearchTerm(terms, turkishIngredients[index]);
        }
        if (index < englishIngredients.length) {
            addSearchTerm(terms, englishIngredients[index]);
        }
        return new ArrayList<>(terms);
    }

    private void addSearchTerm(Set<String> terms, String value) {
        String normalized = normalizeSearchTerm(value);
        if (!normalized.isEmpty()) {
            terms.add(normalized);
        }
    }

    private String normalizeSearchTerm(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private void performSearch() {
        hideKeyboard();
        List<String> selected = getSelectedIngredients();
        String textQuery = etSearch.getText() != null ? etSearch.getText().toString().trim() : "";

        progressDiscover.setVisibility(View.VISIBLE);
        tvDiscoverEmpty.setVisibility(View.GONE);
        rvDiscoverResults.setVisibility(View.GONE);
        tvResultsTitle.setVisibility(View.GONE);
        setPublicCookbooksVisibility(false);

        if (textQuery.isEmpty()) {
            loadAllPublicRecipes(selected, textQuery, "");
            return;
        }

        // 1. Immediate Manual Patch for common search terms (Instant results)
        String manualTranslated = applyManualPatchForSearch(textQuery);
        if (!manualTranslated.isEmpty()) {
            loadAllPublicRecipes(selected, textQuery, manualTranslated);
            return;
        }

        // 2. Fallback to TranslationService
        translationService.translateSingleField(textQuery, "tr", "en")
                .addOnSuccessListener(translated -> {
                    if (translated != null && !translated.equalsIgnoreCase(textQuery)) {
                        loadAllPublicRecipes(selected, textQuery, translated);
                    } else {
                        translationService.translateSingleField(textQuery, "en", "tr")
                                .addOnSuccessListener(trTranslated -> {
                                    loadAllPublicRecipes(selected, textQuery, trTranslated);
                                })
                                .addOnFailureListener(e -> loadAllPublicRecipes(selected, textQuery, ""));
                    }
                })
                .addOnFailureListener(e -> loadAllPublicRecipes(selected, textQuery, ""));
    }

    private void loadAllPublicRecipes(List<String> selectedIngredients, String textQuery, String translatedQuery) {
        Set<String> collectedRecipeIds = new LinkedHashSet<>();
        Set<String> queuedCookbookRecipeIds = new LinkedHashSet<>();
        List<Recipe> allRecipes = new ArrayList<>();
        publicCookbooks.clear();
        
        final int[] pending = {4};

        Runnable checkDone = () -> {
            pending[0]--;
            if (pending[0] == 0) {
                loadAllOwnersForSearch(allRecipes, () -> 
                    filterAndDisplay(allRecipes, selectedIngredients, textQuery, translatedQuery)
                );
            }
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
                            publicCookbooks.add(book);
                            if (book.getRecipeIds() != null) {
                                for (String rid : book.getRecipeIds()) {
                                    if (rid != null && !rid.isEmpty()
                                            && !collectedRecipeIds.contains(rid)
                                            && queuedCookbookRecipeIds.add(rid)) {
                                        recipeIds.add(rid);
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

    private void filterAndDisplay(List<Recipe> allRecipes, List<String> selectedIngredients, String textQuery, String translatedQuery) {
        if (!isAdded()) return;

        List<Recipe> filtered = new ArrayList<>();
        List<Set<String>> selectedIngredientGroups = buildIngredientSearchGroups(selectedIngredients);

        for (Recipe r : allRecipes) {
            Set<String> recipeSearchTerms = getRecipeSearchTerms(r);

            if (!selectedIngredientGroups.isEmpty()) {
                boolean hasAny = false;
                for (Set<String> selectedGroup : selectedIngredientGroups) {
                    for (String term : recipeSearchTerms) {
                        if (matchesSelectedIngredient(selectedGroup, term)) {
                            hasAny = true;
                            break;
                        }
                    }
                    if (hasAny) break;
                }
                if (!hasAny) continue;
            }

            if (!textQuery.isEmpty()) {
                String q1 = textQuery.toLowerCase();
                String q2 = translatedQuery != null ? translatedQuery.toLowerCase() : "";
                
                boolean matchInTitleDesc = r.getTitle().toLowerCase().contains(q1) || 
                                         r.getDescription().toLowerCase().contains(q1) ||
                                         (!q2.isEmpty() && (r.getTitle().toLowerCase().contains(q2) || r.getDescription().toLowerCase().contains(q2)));
                
                boolean matchAuthor = false;
                User owner = ownersById.get(r.getUserId());
                if (owner != null && owner.getDisplayName() != null) {
                    String authorName = owner.getDisplayName().toLowerCase();
                    if (authorName.contains(q1) || (!q2.isEmpty() && authorName.contains(q2))) {
                        matchAuthor = true;
                    }
                }

                boolean matchInTerms = false;
                for (String term : recipeSearchTerms) {
                    if (term.contains(q1) || (!q2.isEmpty() && term.contains(q2))) {
                        matchInTerms = true;
                        break;
                    }
                }
                
                if (!matchInTitleDesc && !matchInTerms && !matchAuthor) {
                    continue;
                }
            }

            filtered.add(r);
        }

        scoreAndDisplay(filtered, selectedIngredients);
    }

    private void loadAllOwnersForSearch(List<Recipe> recipes, Runnable onDone) {
        Set<String> uids = new HashSet<>();
        for (Recipe r : recipes) {
            if (r.getUserId() != null) uids.add(r.getUserId());
        }
        
        if (uids.isEmpty()) {
            onDone.run();
            return;
        }

        List<com.google.android.gms.tasks.Task<DocumentSnapshot>> tasks = new ArrayList<>();
        for (String uid : uids) {
            if (!ownersById.containsKey(uid)) {
                tasks.add(db.collection("users").document(uid).get());
            }
        }

        if (tasks.isEmpty()) {
            onDone.run();
            return;
        }

        com.google.android.gms.tasks.Tasks.whenAllComplete(tasks)
                .addOnCompleteListener(t -> {
                    for (com.google.android.gms.tasks.Task<DocumentSnapshot> task : tasks) {
                        if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                            User u = task.getResult().toObject(User.class);
                            if (u != null) {
                                u.setUid(task.getResult().getId());
                                ownersById.put(u.getUid(), u);
                            }
                        }
                    }
                    onDone.run();
                });
    }

    private Set<String> getRecipeSearchTerms(Recipe r) {
        Set<String> terms = new HashSet<>();
        if (r.getIngredientNames() != null) {
            for (String name : r.getIngredientNames()) terms.add(name.toLowerCase().trim());
        }
        if (r.getIngredients() != null) {
            for (Recipe.Ingredient ing : r.getIngredients()) {
                terms.add(ing.getName().toLowerCase().trim());
                if (ing.getUnit() != null) terms.add(ing.getUnit().toLowerCase().trim());
                if (ing.getTranslatedName() != null && !ing.getTranslatedName().isEmpty()) {
                    terms.add(ing.getTranslatedName().toLowerCase().trim());
                }
                if (ing.getTranslatedUnit() != null && !ing.getTranslatedUnit().isEmpty()) {
                    terms.add(ing.getTranslatedUnit().toLowerCase().trim());
                }
            }
        }
        return terms;
    }

    private void scoreAndDisplay(List<Recipe> recipes, List<String> selectedIngredients) {
        results.clear();
        List<Set<String>> selectedIngredientGroups = buildIngredientSearchGroups(selectedIngredients);

        for (Recipe recipe : recipes) {
            Set<String> recipeIngs = getRecipeSearchTerms(recipe);

            int matchPercent;
            List<String> missing = new ArrayList<>();

            if (selectedIngredientGroups.isEmpty()) {
                matchPercent = 100;
            } else {
                int matched = 0;
                for (Set<String> selectedGroup : selectedIngredientGroups) {
                    boolean found = false;
                    for (String ri : recipeIngs) {
                        if (matchesSelectedIngredient(selectedGroup, ri)) {
                            found = true;
                            break;
                        }
                    }
                    if (found) matched++;
                    else missing.add(getDisplayTerm(selectedGroup));
                }
                matchPercent = (matched * 100) / selectedIngredientGroups.size();
            }

            results.add(new ScoredRecipe(recipe, matchPercent, missing));
        }

        sortRecipesBySelection();
        adapter.setOwnerMap(ownersById);
        
        tvResultsTitle.setText(getString(R.string.results_count, results.size()));
        
        updateResultVisibility();
    }

    private void sortRecipesBySelection() {
        int checkedId = chipGroupRecipeSort.getCheckedChipId();
        
        if (checkedId == R.id.chipSortRecipePopular) {
            results.sort((a, b) -> Integer.compare(b.recipe.getLikes(), a.recipe.getLikes()));
        } else if (checkedId == R.id.chipSortRecipeNewest) {
            results.sort((a, b) -> Long.compare(b.recipe.getCreatedAt(), a.recipe.getCreatedAt()));
        } else if (checkedId == R.id.chipSortRecipeAdded) {
            // "Most Added" usually maps to popularity or total saves. 
            // Since we don't have a separate save counter, we'll use likes as a proxy or stick to match percent as primary.
            // Let's use likes but prioritize match percent if they are equal.
            results.sort((a, b) -> {
                int res = Integer.compare(b.recipe.getLikes(), a.recipe.getLikes());
                return res != 0 ? res : Integer.compare(b.matchPercent, a.matchPercent);
            });
        } else {
            // Default: Match Percent
            results.sort((a, b) -> Integer.compare(b.matchPercent, a.matchPercent));
        }
        adapter.notifyDataSetChanged();
    }

    private void updateResultVisibility() {
        progressDiscover.setVisibility(View.GONE);
        if (results.isEmpty()) {
            tvDiscoverEmpty.setVisibility(View.VISIBLE);
            rvDiscoverResults.setVisibility(View.GONE);
            tvResultsTitle.setVisibility(View.GONE);
            hsvRecipeSort.setVisibility(View.GONE);
        } else {
            tvDiscoverEmpty.setVisibility(View.GONE);
            rvDiscoverResults.setVisibility(View.VISIBLE);
            tvResultsTitle.setVisibility(View.VISIBLE);
            hsvRecipeSort.setVisibility(View.VISIBLE);
            adapter.notifyDataSetChanged();
            
            // Scroll the NestedScrollView to the results section
            nsvDiscover.post(() -> {
                int targetY = tvResultsTitle.getTop();
                nsvDiscover.smoothScrollTo(0, targetY);
            });
        }

        if (!publicCookbooks.isEmpty()) {
            // Show only first 10 for discover page, sorted by popularity
            Collections.sort(publicCookbooks, (c1, c2) -> Integer.compare(c2.getFollowerCount(), c1.getFollowerCount()));
            cookbookAdapter.notifyDataSetChanged();
            setPublicCookbooksVisibility(true);
        } else {
            setPublicCookbooksVisibility(false);
        }
    }

    private void setPublicCookbooksVisibility(boolean show) {
        int visibility = show ? View.VISIBLE : View.GONE;
        if (layoutPublicCookbooksHeader != null) layoutPublicCookbooksHeader.setVisibility(visibility);
        if (rvPublicCookbooks != null) rvPublicCookbooks.setVisibility(visibility);
    }

    private List<String> getSelectedIngredients() {
        Set<String> selected = new LinkedHashSet<>();
        for (int i = 0; i < chipGroupIngredients.getChildCount(); i++) {
            Chip chip = (Chip) chipGroupIngredients.getChildAt(i);
            if (chip.isChecked()) {
                List<String> searchTerms = ingredientSearchTermsByChipId.get(chip.getId());
                if (searchTerms != null && !searchTerms.isEmpty()) {
                    selected.addAll(searchTerms);
                } else {
                    addSearchTerm(selected, chip.getText().toString());
                }
            }
        }
        return new ArrayList<>(selected);
    }

    private List<Set<String>> buildIngredientSearchGroups(List<String> selectedIngredients) {
        List<Set<String>> groups = new ArrayList<>();
        Set<String> consumedTerms = new HashSet<>();

        for (String selectedIngredient : selectedIngredients) {
            String normalized = normalizeSearchTerm(selectedIngredient);
            if (normalized.isEmpty() || consumedTerms.contains(normalized)) {
                continue;
            }

            Set<String> group = new LinkedHashSet<>();
            List<String> chipTerms = ingredientSearchTermsByTerm.get(normalized);
            if (chipTerms != null && !chipTerms.isEmpty()) {
                group.addAll(chipTerms);
            } else {
                group.add(normalized);
                String translated = applyManualPatchForSearch(normalized);
                if (!translated.isEmpty()) {
                    group.add(translated);
                }
            }

            consumedTerms.addAll(group);
            groups.add(group);
        }

        return groups;
    }

    private boolean matchesSelectedIngredient(Set<String> selectedTerms, String recipeTerm) {
        if (recipeTerm == null) {
            return false;
        }

        String normalizedRecipeTerm = normalizeSearchTerm(recipeTerm);
        for (String selectedTerm : selectedTerms) {
            if (normalizedRecipeTerm.contains(selectedTerm) || selectedTerm.contains(normalizedRecipeTerm)) {
                return true;
            }

            String translated = applyManualPatchForSearch(selectedTerm);
            if (!translated.isEmpty()
                    && (normalizedRecipeTerm.contains(translated) || translated.contains(normalizedRecipeTerm))) {
                return true;
            }
        }
        return false;
    }

    private String getDisplayTerm(Set<String> selectedGroup) {
        return selectedGroup.isEmpty() ? "" : selectedGroup.iterator().next();
    }

    private String applyManualPatchForSearch(String ingredient) {
        String lower = normalizeSearchTerm(ingredient);
        Map<String, String> patches = new HashMap<>();
        // English -> Turkish
        patches.put("egg", "yumurta");
        patches.put("milk", "süt");
        patches.put("water", "su");
        patches.put("flour", "un");
        patches.put("corn", "mısır");
        patches.put("sugar", "şeker");
        patches.put("oil", "yağ");
        patches.put("salt", "tuz");
        patches.put("chicken", "tavuk");
        patches.put("meat", "et");
        patches.put("potato", "patates");
        patches.put("onion", "soğan");
        patches.put("tomato", "domates");
        patches.put("cheese", "peynir");
        patches.put("butter", "tereyağı");
        patches.put("rice", "pirinç");
        patches.put("pasta", "makarna");
        
        // Turkish -> English
        patches.put("yumurta", "egg");
        patches.put("süt", "milk");
        patches.put("su", "water");
        patches.put("un", "flour");
        patches.put("mısır", "corn");
        patches.put("şeker", "sugar");
        patches.put("yağ", "oil");
        patches.put("tuz", "salt");
        patches.put("tavuk", "chicken");
        patches.put("et", "meat");
        patches.put("patates", "potato");
        patches.put("soğan", "onion");
        patches.put("domates", "tomato");
        patches.put("peynir", "cheese");
        patches.put("tereyağı", "butter");
        patches.put("pirinç", "rice");
        patches.put("makarna", "pasta");
        
        return patches.getOrDefault(lower, "");
    }

    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }

    @Override
    public void onRecipeClick(Recipe recipe) {
        android.content.Intent intent = new android.content.Intent(getContext(), RecipeDetailActivity.class);
        intent.putExtra(RecipeDetailActivity.EXTRA_RECIPE, recipe);
        startActivity(intent);
    }

    @Override
    public void onAddMissingToShopping(Recipe recipe, List<String> missing) {
        Toast.makeText(getContext(), R.string.missing_added_to_shopping, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onAuthorClick(String userId) {
        Intent intent = new Intent(getContext(), com.recipebookpro.presentation.ui.kitchen.PublicProfileActivity.class);
        intent.putExtra(com.recipebookpro.presentation.ui.kitchen.PublicProfileActivity.EXTRA_USER_ID, userId);
        startActivity(intent);
    }

    @Override
    public void onToggleFollowAuthor(String userId, boolean currentlyFollowing) {
        String currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
        if (currentUid == null || currentUid.equals(userId)) return;

        db.runTransaction(transaction -> {
            DocumentSnapshot targetUserDoc = transaction.get(db.collection("users").document(userId));
            DocumentSnapshot currentUserDoc = transaction.get(db.collection("users").document(currentUid));

            List<String> targetFollowerIds = (List<String>) targetUserDoc.get("followerIds");
            targetFollowerIds = targetFollowerIds != null ? new ArrayList<>(targetFollowerIds) : new ArrayList<>();
            long targetFollowerCount = targetUserDoc.getLong("followerCount") != null ? targetUserDoc.getLong("followerCount") : 0;

            List<String> currentFollowingIds = (List<String>) currentUserDoc.get("followingIds");
            currentFollowingIds = currentFollowingIds != null ? new ArrayList<>(currentFollowingIds) : new ArrayList<>();
            long currentFollowingCount = currentUserDoc.getLong("followingCount") != null ? currentUserDoc.getLong("followingCount") : 0;

            if (currentlyFollowing) {
                targetFollowerIds.remove(currentUid);
                targetFollowerCount = Math.max(0, targetFollowerCount - 1);
                currentFollowingIds.remove(userId);
                currentFollowingCount = Math.max(0, currentFollowingCount - 1);
            } else {
                targetFollowerIds.add(currentUid);
                targetFollowerCount++;
                currentFollowingIds.add(userId);
                currentFollowingCount++;
            }

            transaction.update(db.collection("users").document(userId),
                    "followerIds", targetFollowerIds,
                    "followerCount", targetFollowerCount);
            
            transaction.update(db.collection("users").document(currentUid),
                    "followingIds", currentFollowingIds,
                    "followingCount", currentFollowingCount);

            return null;
        }).addOnSuccessListener(unused -> {
            updateLocalFollowState(userId, currentUid, currentlyFollowing);
        }).addOnFailureListener(e -> {
            if (getContext() != null) {
                Toast.makeText(getContext(), R.string.follow_action_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateLocalFollowState(String targetUserId, String currentUid, boolean wasFollowing) {
        User targetUser = ownersById.get(targetUserId);
        if (targetUser == null || currentUid == null) return;

        List<String> followerIds = new ArrayList<>(targetUser.getFollowerIds());
        int followerCount = targetUser.getFollowerCount();

        if (wasFollowing) {
            followerIds.remove(currentUid);
            followerCount = Math.max(0, followerCount - 1);
        } else if (!followerIds.contains(currentUid)) {
            followerIds.add(currentUid);
            followerCount++;
        }

        targetUser.setFollowerIds(followerIds);
        targetUser.setFollowerCount(followerCount);
        adapter.setOwnerMap(ownersById);
    }

    @Override
    public void onCookbookClick(Cookbook cookbook) {
        android.content.Intent intent = new android.content.Intent(getContext(), CookbookDetailActivity.class);
        intent.putExtra(CookbookDetailActivity.EXTRA_COOKBOOK_ID, cookbook.getId());
        startActivity(intent);
    }

    private void hideKeyboard() {
        View view = requireActivity().getCurrentFocus();
        if (view != null) {
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (firstResume) {
            firstResume = false;
            return;
        }
        performSearch();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (translationService != null) translationService.close();
    }
}
