package com.recipebookpro.presentation.ui.planner;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.recipebookpro.R;
import com.recipebookpro.domain.model.Cookbook;
import com.recipebookpro.domain.model.Recipe;
import com.recipebookpro.domain.model.RecipeCollection;
import com.recipebookpro.presentation.ui.planner.adapter.DayRecipeAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RecipeSearchBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_DAY_KEY = "day_key";

    public interface OnRecipeSelectedListener {
        void onRecipeSelected(String dayKey, Recipe recipe);
    }

    private String dayKey;
    private OnRecipeSelectedListener listener;
    private final List<Recipe> allRecipes = new ArrayList<>();
    private final List<Recipe> filteredRecipes = new ArrayList<>();
    private DayRecipeAdapter adapter;

    public static RecipeSearchBottomSheet newInstance(String dayKey) {
        RecipeSearchBottomSheet sheet = new RecipeSearchBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_DAY_KEY, dayKey);
        sheet.setArguments(args);
        return sheet;
    }

    public void setOnRecipeSelectedListener(OnRecipeSelectedListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            dayKey = getArguments().getString(ARG_DAY_KEY);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_recipe_search, container, false);

        TextInputEditText etSearch = view.findViewById(R.id.etRecipeSearch);
        RecyclerView rvResults = view.findViewById(R.id.rvRecipeSearch);
        ProgressBar progress = view.findViewById(R.id.progressRecipeSearch);

        adapter = new DayRecipeAdapter(filteredRecipes, recipe -> {
            if (listener != null) {
                listener.onRecipeSelected(dayKey, recipe);
            }
            dismiss();
        });
        rvResults.setLayoutManager(new LinearLayoutManager(getContext()));
        rvResults.setAdapter(adapter);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterRecipes(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        loadUserRecipes(progress);

        return view;
    }

    /**
     * Planner tarif seçicisi: kullanıcıya ait tarifler + defter (cookbook) ve koleksiyon
     * içindeki recipeIds ile eşleşen tüm tarifler. Yalnızca recipes.userId sorgusu, deftere
     * eklenmiş farklı userId’li tarifleri listelemezdi.
     */
    private void loadUserRecipes(ProgressBar progress) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        progress.setVisibility(View.VISIBLE);
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String uid = user.getUid();

        Task<QuerySnapshot> taskOwnRecipes = db.collection("recipes").whereEqualTo("userId", uid).get();
        Task<QuerySnapshot> taskOwnedCookbooks = db.collection("cookbooks").whereEqualTo("userId", uid).get();
        Task<QuerySnapshot> taskCollabCookbooks = db.collection("cookbooks")
                .whereArrayContains("collaboratorIds", uid)
                .get();
        Task<QuerySnapshot> taskUserCollections = db.collection("collections")
                .whereEqualTo("userId", uid)
                .get();

        Tasks.whenAllSuccess(taskOwnRecipes, taskOwnedCookbooks, taskCollabCookbooks, taskUserCollections)
                .addOnSuccessListener(results -> {
                    if (!isAdded()) return;

                    Map<String, Recipe> mergedById = new HashMap<>();
                    QuerySnapshot snapOwnRecipes = (QuerySnapshot) results.get(0);
                    for (QueryDocumentSnapshot doc : snapOwnRecipes) {
                        Recipe recipe = Recipe.fromDocument(doc);
                        mergedById.put(recipe.getId(), recipe);
                    }

                    Set<String> idsFromCookbooks = new HashSet<>();
                    collectRecipeIdsFromCookbookQuery((QuerySnapshot) results.get(1), idsFromCookbooks);
                    collectRecipeIdsFromCookbookQuery((QuerySnapshot) results.get(2), idsFromCookbooks);
                    collectRecipeIdsFromCollectionQuery((QuerySnapshot) results.get(3), idsFromCookbooks);

                    List<String> missingIds = new ArrayList<>();
                    for (String recipeId : idsFromCookbooks) {
                        if (!mergedById.containsKey(recipeId)) {
                            missingIds.add(recipeId);
                        }
                    }

                    if (missingIds.isEmpty()) {
                        progress.setVisibility(View.GONE);
                        publishMergedRecipes(mergedById);
                        return;
                    }

                    List<Task<QuerySnapshot>> fetchByIdTasks = new ArrayList<>();
                    for (List<String> chunk : partition(missingIds, 10)) {
                        fetchByIdTasks.add(db.collection("recipes")
                                .whereIn(FieldPath.documentId(), chunk)
                                .get());
                    }

                    Tasks.whenAllSuccess(fetchByIdTasks)
                            .addOnSuccessListener(fetchResults -> {
                                if (!isAdded()) return;
                                progress.setVisibility(View.GONE);
                                for (Object obj : fetchResults) {
                                    QuerySnapshot snap = (QuerySnapshot) obj;
                                    for (DocumentSnapshot doc : snap.getDocuments()) {
                                        Recipe recipe = Recipe.fromDocument(doc);
                                        mergedById.put(recipe.getId(), recipe);
                                    }
                                }
                                publishMergedRecipes(mergedById);
                            })
                            .addOnFailureListener(e -> {
                                if (!isAdded()) return;
                                progress.setVisibility(View.GONE);
                                publishMergedRecipes(mergedById);
                            });
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    progress.setVisibility(View.GONE);
                });
    }

    private void collectRecipeIdsFromCookbookQuery(QuerySnapshot cookbookSnap, Set<String> outIds) {
        if (cookbookSnap == null) return;
        for (QueryDocumentSnapshot doc : cookbookSnap) {
            Cookbook book = Cookbook.fromDocument(doc);
            for (String recipeId : book.getRecipeIds()) {
                if (recipeId != null && !recipeId.isEmpty()) {
                    outIds.add(recipeId);
                }
            }
        }
    }

    private void collectRecipeIdsFromCollectionQuery(QuerySnapshot collectionSnap, Set<String> outIds) {
        if (collectionSnap == null) return;
        for (QueryDocumentSnapshot doc : collectionSnap) {
            RecipeCollection coll = RecipeCollection.fromDocument(doc);
            for (String recipeId : coll.getRecipeIds()) {
                if (recipeId != null && !recipeId.isEmpty()) {
                    outIds.add(recipeId);
                }
            }
        }
    }

    private void publishMergedRecipes(Map<String, Recipe> mergedById) {
        allRecipes.clear();
        allRecipes.addAll(mergedById.values());
        Collections.sort(allRecipes, (a, b) -> a.getTitle().compareToIgnoreCase(b.getTitle()));
        filterRecipes("");
    }

    private <T> List<List<T>> partition(List<T> list, int chunkSize) {
        List<List<T>> parts = new ArrayList<>();
        for (int i = 0; i < list.size(); i += chunkSize) {
            parts.add(new ArrayList<>(list.subList(i, Math.min(list.size(), i + chunkSize))));
        }
        return parts;
    }

    private void filterRecipes(String query) {
        filteredRecipes.clear();
        String q = query.toLowerCase().trim();
        for (Recipe r : allRecipes) {
            if (q.isEmpty() || r.getTitle().toLowerCase().contains(q)) {
                filteredRecipes.add(r);
            }
        }
        adapter.notifyDataSetChanged();
    }
}
