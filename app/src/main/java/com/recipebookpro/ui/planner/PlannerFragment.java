package com.recipebookpro.ui.planner;

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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textview.MaterialTextView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.recipebookpro.R;
import com.recipebookpro.model.Recipe;
import com.recipebookpro.model.ShoppingList;
import com.recipebookpro.model.WeeklyMenu;
import com.recipebookpro.ui.planner.adapter.DayCardAdapter;
import com.recipebookpro.ui.recipe.RecipeDetailActivity;
import com.recipebookpro.ui.shopping.ShoppingListDetailActivity;
import com.recipebookpro.ui.shopping.adapter.ShoppingListAdapter;

import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import com.recipebookpro.worker.MergeIngredientsWorker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlannerFragment extends Fragment implements DayCardAdapter.OnDayInteractionListener {

    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private WeeklyMenu weeklyMenu;

    private RecyclerView rvDayCards;
    private RecyclerView rvShoppingListsInline;
    private ProgressBar progressPlanner;
    private View tvPlannerEmpty;
    private MaterialTextView tvEmptyShoppingInline;
    private DayCardAdapter dayCardAdapter;
    private ShoppingListAdapter shoppingListAdapter;
    private final List<ShoppingList> shoppingLists = new ArrayList<>();

    private final Map<String, List<Recipe>> resolvedRecipes = new HashMap<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_planner, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        rvDayCards = view.findViewById(R.id.rvDayCards);
        rvShoppingListsInline = view.findViewById(R.id.rvShoppingListsInline);
        progressPlanner = view.findViewById(R.id.progressPlanner);
        tvPlannerEmpty = view.findViewById(R.id.tvPlannerEmpty);
        tvEmptyShoppingInline = view.findViewById(R.id.tvEmptyShoppingInline);
        MaterialButton btnGenerate = view.findViewById(R.id.btnGenerateShoppingList);

        dayCardAdapter = new DayCardAdapter(this);
        rvDayCards.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        rvDayCards.setAdapter(dayCardAdapter);

        shoppingListAdapter = new ShoppingListAdapter(shoppingLists, list -> {
            Intent intent = new Intent(getContext(), ShoppingListDetailActivity.class);
            intent.putExtra(ShoppingListDetailActivity.EXTRA_LIST_ID, list.getId());
            startActivity(intent);
        });
        rvShoppingListsInline.setLayoutManager(new LinearLayoutManager(getContext()));
        rvShoppingListsInline.setAdapter(shoppingListAdapter);

        btnGenerate.setOnClickListener(v -> generateShoppingList());

        loadWeeklyMenu();
        loadShoppingLists();
    }

    private void loadWeeklyMenu() {
        if (currentUser == null) return;
        progressPlanner.setVisibility(View.VISIBLE);

        db.collection("weekly_menus").document(currentUser.getUid())
                .addSnapshotListener((doc, error) -> {
                    if (error != null) {
                        progressPlanner.setVisibility(View.GONE);
                        return;
                    }

                    if (doc != null && doc.exists()) {
                        weeklyMenu = WeeklyMenu.fromDocument(doc);
                    } else {
                        weeklyMenu = new WeeklyMenu(currentUser.getUid());
                        db.collection("weekly_menus").document(currentUser.getUid())
                                .set(weeklyMenu);
                    }

                    resolveAllRecipes();
                });
    }

    private void resolveAllRecipes() {
        List<String> allIds = weeklyMenu.getAllRecipeIds();

        if (allIds.isEmpty()) {
            progressPlanner.setVisibility(View.GONE);
            populateDayCards(new HashMap<>());
            return;
        }

        List<List<String>> chunks = partition(allIds, 10);
        List<Task<QuerySnapshot>> tasks = new ArrayList<>();

        for (List<String> chunk : chunks) {
            tasks.add(db.collection("recipes").whereIn(
                    com.google.firebase.firestore.FieldPath.documentId(), chunk).get());
        }

        Tasks.whenAllSuccess(tasks).addOnSuccessListener(results -> {
            Map<String, Recipe> recipeMap = new HashMap<>();
            for (Object result : results) {
                QuerySnapshot snap = (QuerySnapshot) result;
                for (DocumentSnapshot docSnap : snap.getDocuments()) {
                    Recipe r = Recipe.fromDocument(docSnap);
                    recipeMap.put(r.getId(), r);
                }
            }

            Map<String, List<Recipe>> dayData = new HashMap<>();
            for (String dayKey : WeeklyMenu.DAY_KEYS) {
                List<String> dayIds = weeklyMenu.getRecipeIdsForDay(dayKey);
                List<Recipe> dayRecipes = new ArrayList<>();
                for (String id : dayIds) {
                    Recipe r = recipeMap.get(id);
                    if (r != null) dayRecipes.add(r);
                }
                dayData.put(dayKey, dayRecipes);
            }

            progressPlanner.setVisibility(View.GONE);
            populateDayCards(dayData);
        }).addOnFailureListener(e -> progressPlanner.setVisibility(View.GONE));
    }

    private void populateDayCards(Map<String, List<Recipe>> dayData) {
        resolvedRecipes.clear();
        resolvedRecipes.putAll(dayData);
        dayCardAdapter.setAllDayRecipes(dayData);

        boolean allEmpty = true;
        for (List<Recipe> list : dayData.values()) {
            if (!list.isEmpty()) { allEmpty = false; break; }
        }
        tvPlannerEmpty.setVisibility(allEmpty ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onAddRecipeClick(String dayKey, int dayIndex) {
        RecipeSearchBottomSheet sheet = RecipeSearchBottomSheet.newInstance(dayKey);
        sheet.setOnRecipeSelectedListener((key, recipe) -> {
            if (currentUser == null) return;
            db.collection("weekly_menus").document(currentUser.getUid())
                    .update("days." + key, FieldValue.arrayUnion(recipe.getId()));
        });
        sheet.show(getChildFragmentManager(), "RecipeSearch");
    }

    @Override
    public void onRecipeLongPress(String dayKey, Recipe recipe) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(recipe.getTitle())
                .setMessage(dayKey + " gününden kaldırılsın mı?")
                .setPositiveButton("Kaldır", (d, w) -> {
                    if (currentUser == null) return;
                    db.collection("weekly_menus").document(currentUser.getUid())
                            .update("days." + dayKey, FieldValue.arrayRemove(recipe.getId()));
                })
                .setNegativeButton("İptal", null)
                .show();
    }

    @Override
    public void onRecipeClick(Recipe recipe) {
        Intent intent = new Intent(getContext(), RecipeDetailActivity.class);
        intent.putExtra(RecipeDetailActivity.EXTRA_RECIPE, recipe);
        startActivity(intent);
    }

    private void generateShoppingList() {
        if (currentUser == null || weeklyMenu == null) return;

        List<String> allIds = weeklyMenu.getAllRecipeIdsWithDuplicates();
        if (allIds.isEmpty()) {
            Toast.makeText(getContext(), "Menüde tarif yok", Toast.LENGTH_SHORT).show();
            return;
        }

        Data inputData = new Data.Builder()
                .putString(MergeIngredientsWorker.KEY_USER_ID, currentUser.getUid())
                .putStringArray(MergeIngredientsWorker.KEY_RECIPE_IDS, allIds.toArray(new String[0]))
                .putString(MergeIngredientsWorker.KEY_LIST_NAME, "Haftalık Menü Alışverişi")
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(MergeIngredientsWorker.class)
                .setInputData(inputData)
                .build();

        WorkManager.getInstance(requireContext()).enqueue(request);
        Toast.makeText(getContext(), "Alışveriş listesi hazırlanıyor…", Toast.LENGTH_SHORT).show();
    }

    private void loadShoppingLists() {
        if (currentUser == null) return;
        db.collection("shopping_lists")
                .whereEqualTo("userId", currentUser.getUid())
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null) return;
                    shoppingLists.clear();
                    if (value != null) {
                        for (QueryDocumentSnapshot doc : value) {
                            shoppingLists.add(ShoppingList.fromDocument(doc));
                        }
                    }
                    shoppingListAdapter.notifyDataSetChanged();
                    if (tvEmptyShoppingInline != null) {
                        tvEmptyShoppingInline.setVisibility(
                                shoppingLists.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                });
    }

    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> parts = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            parts.add(new ArrayList<>(list.subList(i, Math.min(list.size(), i + size))));
        }
        return parts;
    }
}
