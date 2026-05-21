package com.recipebookpro.presentation.ui.planner;

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
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.recipebookpro.R;
import com.recipebookpro.domain.model.Recipe;
import com.recipebookpro.domain.model.ShoppingList;
import com.recipebookpro.domain.model.MealPlan;
import com.recipebookpro.domain.repository.MealPlanRepository;
import com.recipebookpro.data.remote.GroqAiNutritionService;
import com.recipebookpro.data.repository.MealPlanRepositoryImpl;
import com.recipebookpro.domain.model.PlannerCalorieSummary;
import com.recipebookpro.presentation.ui.planner.adapter.DayCardAdapter;
import com.recipebookpro.presentation.ui.LocaleHelper;
import com.recipebookpro.presentation.ui.recipe.RecipeDetailActivity;
import com.recipebookpro.presentation.ui.shopping.ShoppingListDetailActivity;
import com.recipebookpro.presentation.ui.shopping.adapter.ShoppingListAdapter;
import com.recipebookpro.domain.repository.RecipeRepository;
import com.recipebookpro.data.repository.RecipeRepositoryImpl;
import com.recipebookpro.domain.usecase.AnalyzeRecipeCaloriesUseCase;

import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import com.recipebookpro.data.worker.MergeIngredientsWorker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlannerFragment extends Fragment implements DayCardAdapter.OnDayInteractionListener {

    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private MealPlan currentPlan;
    private MealPlanRepository planRepository;
    private AnalyzeRecipeCaloriesUseCase analyzeCaloriesUseCase;

    private RecyclerView rvDayCards;
    private RecyclerView rvShoppingListsInline;
    private ProgressBar progressPlanner;
    private View tvPlannerEmpty;
    private MaterialTextView tvEmptyShoppingInline;
    private MaterialTextView tvTotalCalories, tvPlanName;
    private View cardCalorieSummary;
    private com.google.android.material.chip.ChipGroup cgDuration;
    private DayCardAdapter dayCardAdapter;
    private ShoppingListAdapter shoppingListAdapter;
    private final List<ShoppingList> shoppingLists = new ArrayList<>();

    private final Map<String, List<Recipe>> resolvedRecipes = new HashMap<>();
    private ListenerRegistration currentPlanListener;

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
        planRepository = new MealPlanRepositoryImpl();
        RecipeRepository recipeRepository = new RecipeRepositoryImpl();
        analyzeCaloriesUseCase = new AnalyzeRecipeCaloriesUseCase(recipeRepository, new GroqAiNutritionService());

        rvDayCards = view.findViewById(R.id.rvDayCards);
        rvShoppingListsInline = view.findViewById(R.id.rvShoppingListsInline);
        progressPlanner = view.findViewById(R.id.progressPlanner);
        tvPlannerEmpty = view.findViewById(R.id.tvPlannerEmpty);
        tvEmptyShoppingInline = view.findViewById(R.id.tvEmptyShoppingInline);
        tvTotalCalories = view.findViewById(R.id.tvTotalCalories);
        tvPlanName = view.findViewById(R.id.tvPlanName);
        cardCalorieSummary = view.findViewById(R.id.cardCalorieSummary);
        cgDuration = view.findViewById(R.id.cgDuration);

        MaterialButton btnGenerate = view.findViewById(R.id.btnGenerateShoppingList);
        MaterialButton btnSavePlan = view.findViewById(R.id.btnSavePlan);
        MaterialButton btnLoadPlan = view.findViewById(R.id.btnLoadPlan);
        MaterialButton btnSharePlan = view.findViewById(R.id.btnSharePlan);

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

        MaterialToolbar toolbarPlanner = view.findViewById(R.id.toolbarPlanner);
        btnGenerate.setOnClickListener(v -> generateShoppingList());
        btnSavePlan.setOnClickListener(v -> showSavePlanDialog());
        btnLoadPlan.setOnClickListener(v -> showLoadPlanDialog());
        btnSharePlan.setOnClickListener(v -> shareCurrentPlan());

        toolbarPlanner.inflateMenu(R.menu.menu_planner);
        toolbarPlanner.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_new_plan) {
                confirmNewPlan();
                return true;
            }
            return false;
        });

        cgDuration.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!checkedIds.isEmpty()) {
                int duration = 7;
                int id = checkedIds.get(0);
                if (id == R.id.chip3Days) duration = 3;
                else if (id == R.id.chip7Days) duration = 7;
                else if (id == R.id.chip10Days) duration = 10;
                
                updateDuration(duration);
            }
        });

        loadCurrentOrDefaultPlan();
        loadShoppingLists();
    }

    private void showSavePlanDialog() {
        if (currentUser == null || currentPlan == null) return;

        android.widget.EditText et = new android.widget.EditText(requireContext());
        et.setText(currentPlan.getName());
        et.setHint(R.string.meal_plan_name);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.meal_plan_save)
                .setView(et)
                .setPositiveButton(R.string.save, (d, w) -> {
                    String name = et.getText().toString().trim();
                    if (!name.isEmpty()) {
                        currentPlan.setName(name);
                        savePlan();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * Gün/satır düzenlemelerini Firestore'a yazar (tarif ekle/kaldır). Tam ekran progress göstermez.
     */
    private void persistCurrentPlanAfterEdit() {
        if (currentPlan == null || currentUser == null) return;

        calculateTotalCalories();

        planRepository.saveMealPlan(currentPlan, new MealPlanRepository.OnMealPlanActionCompleteListener() {
            @Override
            public void onSuccess() {
                if (!isAdded() || currentPlan == null) return;
                if (currentPlanListener == null && currentPlan.getId() != null) {
                    attachPlanListener(currentPlan.getId());
                }
            }

            @Override
            public void onError(Exception e) {
                if (!isAdded() || getContext() == null) return;
                Toast.makeText(getContext(), R.string.meal_plan_sync_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void savePlan() {
        progressPlanner.setVisibility(View.VISIBLE);
        planRepository.saveMealPlan(currentPlan, new MealPlanRepository.OnMealPlanActionCompleteListener() {
            @Override
            public void onSuccess() {
                progressPlanner.setVisibility(View.GONE);
                Toast.makeText(getContext(), R.string.meal_plan_saved, Toast.LENGTH_SHORT).show();
                if (currentPlanListener == null && currentPlan.getId() != null) {
                    attachPlanListener(currentPlan.getId());
                }
            }

            @Override
            public void onError(Exception e) {
                progressPlanner.setVisibility(View.GONE);
                Toast.makeText(getContext(), R.string.save_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showLoadPlanDialog() {
        SavedPlansBottomSheet sheet = new SavedPlansBottomSheet();
        sheet.setOnPlanSelectedListener(plan -> {
            attachPlanListener(plan.getId());
        });
        sheet.setOnMealPlanDeletedListener(deletedId -> {
            if (deletedId != null && currentPlan != null && deletedId.equals(currentPlan.getId())) {
                if (currentPlanListener != null) {
                    currentPlanListener.remove();
                    currentPlanListener = null;
                }
                loadCurrentOrDefaultPlan();
            }
        });
        sheet.show(getChildFragmentManager(), "SavedPlans");
    }

    private void shareCurrentPlan() {
        // Implement sharing logic using Invitations (Phase 4)
        // For now, show the collaborators bottom sheet if we have an ID
        if (currentPlan == null || currentPlan.getId() == null) {
            Toast.makeText(getContext(), R.string.meal_plan_save_first, Toast.LENGTH_SHORT).show();
            return;
        }
        
        com.recipebookpro.presentation.ui.kitchen.CollaboratorsBottomSheet sheet = 
            com.recipebookpro.presentation.ui.kitchen.CollaboratorsBottomSheet.newInstance(
                currentPlan.getId(), "meal_plan", currentPlan.getName());
        sheet.show(getChildFragmentManager(), "Collaborators");
    }

    private void loadCurrentOrDefaultPlan() {
        if (currentUser == null) return;
        progressPlanner.setVisibility(View.VISIBLE);

        // Find the most recent plan where user is owner or collaborator
        db.collection("meal_plans")
                .where(com.google.firebase.firestore.Filter.or(
                        com.google.firebase.firestore.Filter.equalTo("userId", currentUser.getUid()),
                        com.google.firebase.firestore.Filter.arrayContains("collaboratorIds", currentUser.getUid())
                ))
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        String planId = queryDocumentSnapshots.getDocuments().get(0).getId();
                        attachPlanListener(planId);
                    } else {
                        createNewPlan(7);
                    }
                })
                .addOnFailureListener(e -> {
                    progressPlanner.setVisibility(View.GONE);
                    createNewPlan(7);
                });
    }

    private void attachPlanListener(String planId) {
        if (currentPlanListener != null) currentPlanListener.remove();

        currentPlanListener = db.collection("meal_plans").document(planId)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        progressPlanner.setVisibility(View.GONE);
                        return;
                    }
                    if (value == null || !value.exists()) {
                        progressPlanner.setVisibility(View.GONE);
                        if (currentPlan != null && planId.equals(currentPlan.getId())) {
                            if (currentPlanListener != null) {
                                currentPlanListener.remove();
                                currentPlanListener = null;
                            }
                            loadCurrentOrDefaultPlan();
                        }
                        return;
                    }

                    MealPlan updatedPlan = MealPlan.fromDocument(value);
                    
                    // Only full UI update if recipe IDs changed or duration changed
                    boolean needsFullUpdate = currentPlan == null 
                            || updatedPlan.getDuration() != currentPlan.getDuration()
                            || !updatedPlan.getAllRecipeIds().equals(currentPlan.getAllRecipeIds());

                    this.currentPlan = updatedPlan;
                    
                    if (needsFullUpdate) {
                        updateUIForPlan();
                    } else {
                        // Just update metadata if recipes are same
                        tvPlanName.setText(currentPlan.getName());
                    }
                    progressPlanner.setVisibility(View.GONE);
                });
    }

    private void confirmNewPlan() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.meal_plan_new_confirm_title)
                .setMessage(R.string.meal_plan_new_confirm_msg)
                .setPositiveButton(R.string.meal_plan_new_create, (d, w) -> {
                    if (currentPlanListener != null) {
                        currentPlanListener.remove();
                        currentPlanListener = null;
                    }
                    createNewPlan(7);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void createNewPlan(int duration) {
        if (currentUser == null) return;
        currentPlan = new MealPlan(currentUser.getUid(), "New Plan", duration);
        updateUIForPlan();
    }

    private void updateDuration(int duration) {
        if (currentPlan != null && currentPlan.getDuration() != duration) {
            currentPlan.setDuration(duration);
            // Re-initialize days map for the new duration if needed
            for (int i = 0; i < duration; i++) {
                String key = "day_" + i;
                if (!currentPlan.getDays().containsKey(key)) {
                    currentPlan.getDays().put(key, new ArrayList<>());
                }
            }
            updateUIForPlan();
        }
    }

    private void updateUIForPlan() {
        if (currentPlan == null) return;
        
        // Update Chips
        if (currentPlan.getDuration() == 3) cgDuration.check(R.id.chip3Days);
        else if (currentPlan.getDuration() == 7) cgDuration.check(R.id.chip7Days);
        else if (currentPlan.getDuration() == 10) cgDuration.check(R.id.chip10Days);

        dayCardAdapter.setDuration(currentPlan.getDuration());
        resolveAllRecipes();
    }

    private void resolveAllRecipes() {
        if (currentPlan == null) return;
        List<String> allIds = currentPlan.getAllRecipeIds();

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
            for (int i = 0; i < currentPlan.getDuration(); i++) {
                String dayKey = "day_" + i;
                List<String> dayIds = currentPlan.getDays().get(dayKey);
                List<Recipe> dayRecipes = new ArrayList<>();
                if (dayIds != null) {
                    for (String id : dayIds) {
                        Recipe r = recipeMap.get(id);
                        if (r != null) dayRecipes.add(r);
                    }
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
        updatePlannerEmptyState();
        calculateTotalCalories();
    }

    private void calculateTotalCalories() {
        PlannerCalorieSummary summary = PlannerCalorieSummary.from(resolvedRecipes);

        if (currentPlan != null) {
            currentPlan.setTotalCalories(summary.getTotalKnownCalories());
        }

        if (!summary.shouldShowBanner()) {
            cardCalorieSummary.setVisibility(View.GONE);
            return;
        }

        cardCalorieSummary.setVisibility(View.VISIBLE);
        PlannerCalorieSummary.Coverage coverage = summary.getCoverage();
        if (coverage == PlannerCalorieSummary.Coverage.ALL_KNOWN) {
            tvTotalCalories.setText(getString(R.string.meal_plan_total_calories, summary.getTotalKnownCalories()));
        } else if (coverage == PlannerCalorieSummary.Coverage.PARTIAL) {
            int unknownCount = summary.getUnknownRecipeCount();
            tvTotalCalories.setText(getResources().getQuantityString(
                    R.plurals.meal_plan_unknown_recipes_partial,
                    unknownCount,
                    summary.getTotalKnownCalories(),
                    unknownCount));
        } else {
            int unknownCount = summary.getUnknownRecipeCount();
            tvTotalCalories.setText(getResources().getQuantityString(
                    R.plurals.meal_plan_unknown_recipes_pending,
                    unknownCount,
                    unknownCount));
        }
    }

    @Override
    public void onAddRecipeClick(String dayKey) {
        RecipeSearchBottomSheet sheet = RecipeSearchBottomSheet.newInstance(dayKey);
        sheet.setOnRecipeSelectedListener((key, recipe) -> {
            if (currentPlan == null) return;

            List<String> dayIds = currentPlan.getDays().get(key);
            if (dayIds == null) {
                dayIds = new ArrayList<>();
                currentPlan.getDays().put(key, dayIds);
            }
            dayIds.add(recipe.getId());
            addRecipeToDayLocally(key, recipe);
            persistCurrentPlanAfterEdit();
        });
        sheet.show(getChildFragmentManager(), "RecipeSearch");
    }

    @Override
    public void onRecipeLongPress(String dayKey, Recipe recipe) {
        String dayLabel = getDayLabel(dayKey);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(recipe.getTitle())
                .setMessage(getString(R.string.planner_remove_recipe_confirm, dayLabel))
                .setPositiveButton(R.string.delete, (d, w) -> {
                    if (currentPlan == null) return;
                    List<String> dayIds = currentPlan.getDays().get(dayKey);
                    if (dayIds != null) {
                        dayIds.remove(recipe.getId());
                        removeRecipeFromDayLocally(dayKey, recipe.getId());
                        persistCurrentPlanAfterEdit();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public void onRecipeClick(Recipe recipe) {
        Intent intent = new Intent(getContext(), RecipeDetailActivity.class);
        intent.putExtra(RecipeDetailActivity.EXTRA_RECIPE, recipe);
        startActivity(intent);
    }

    private String getDayLabel(String dayKey) {
        try {
            int index = Integer.parseInt(dayKey.replace("day_", ""));
            // 0 -> 1, 1 -> 2 vb.
            return getString(R.string.day_n, (index + 1));
        } catch (Exception e) {
            return dayKey;
        }
    }

    private void generateShoppingList() {
        if (currentUser == null || currentPlan == null) return;

        List<String> allIds = currentPlan.getAllRecipeIdsWithDuplicates();
        if (allIds.isEmpty()) {
            Toast.makeText(getContext(), R.string.planner_no_recipe_in_menu, Toast.LENGTH_SHORT).show();
            return;
        }

        String listName = "system_planner_weekly_menu";
        if (currentPlan != null && currentPlan.getName() != null && !currentPlan.getName().isEmpty() && !currentPlan.getName().equals("New Plan")) {
            listName = currentPlan.getName() + " Shopping";
        }

        Data inputData = new Data.Builder()
                .putString(MergeIngredientsWorker.KEY_USER_ID, currentUser.getUid())
                .putStringArray(MergeIngredientsWorker.KEY_RECIPE_IDS, allIds.toArray(new String[0]))
                .putString(MergeIngredientsWorker.KEY_LIST_NAME, listName)
                .putBoolean(MergeIngredientsWorker.KEY_OVERWRITE_EXISTING, true)
                .putString(MergeIngredientsWorker.KEY_TARGET_LANGUAGE, LocaleHelper.getLanguage(requireContext()))
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(MergeIngredientsWorker.class)
                .setInputData(inputData)
                .build();

        WorkManager.getInstance(requireContext()).enqueue(request);
        Toast.makeText(getContext(), R.string.planner_generating_shopping_list, Toast.LENGTH_SHORT).show();

        WorkManager.getInstance(requireContext())
                .getWorkInfoByIdLiveData(request.getId())
                .observe(getViewLifecycleOwner(), workInfo -> {
                    if (workInfo == null) return;
                    if (workInfo.getState() == WorkInfo.State.SUCCEEDED
                            || workInfo.getState() == WorkInfo.State.FAILED
                            || workInfo.getState() == WorkInfo.State.CANCELLED) {
                        loadShoppingLists();
                    }
                });
    }

    private void addRecipeToDayLocally(String dayKey, Recipe recipe) {
        List<Recipe> dayRecipes = resolvedRecipes.get(dayKey);
        if (dayRecipes == null) {
            dayRecipes = new ArrayList<>();
            resolvedRecipes.put(dayKey, dayRecipes);
        }
        dayRecipes.add(recipe);
        dayCardAdapter.setDayRecipes(dayKey, dayRecipes);
        updatePlannerEmptyState();
        calculateTotalCalories();

        if (!recipe.hasCalorieEstimate()) {
            analyzeCaloriesUseCase.execute(recipe, new AnalyzeRecipeCaloriesUseCase.AnalyzeCallback() {
                @Override
                public void onSuccess(int calories) {
                    if (getActivity() == null || !isAdded()) return;
                    getActivity().runOnUiThread(() -> {
                        dayCardAdapter.notifyDataSetChanged();
                        calculateTotalCalories();
                    });
                }

                @Override
                public void onError(String error) {
                    android.util.Log.w("PlannerFragment", "Kalori analizi başarısız: " + error);
                }
            });
        }
    }

    private void removeRecipeFromDayLocally(String dayKey, String recipeId) {
        List<Recipe> dayRecipes = resolvedRecipes.get(dayKey);
        if (dayRecipes == null || dayRecipes.isEmpty()) return;
        for (int i = 0; i < dayRecipes.size(); i++) {
            Recipe r = dayRecipes.get(i);
            if (r != null && recipeId.equals(r.getId())) {
                dayRecipes.remove(i);
                break;
            }
        }
        dayCardAdapter.setDayRecipes(dayKey, dayRecipes);
        updatePlannerEmptyState();
        calculateTotalCalories();
    }

    private void updatePlannerEmptyState() {
        if (currentPlan == null) return;
        boolean allEmpty = true;
        for (int i = 0; i < currentPlan.getDuration(); i++) {
            String key = "day_" + i;
            List<Recipe> list = resolvedRecipes.get(key);
            if (list != null && !list.isEmpty()) {
                allEmpty = false;
                break;
            }
        }
        tvPlannerEmpty.setVisibility(allEmpty ? View.VISIBLE : View.GONE);
    }

    private Map<String, ShoppingList> ownerShoppingMap = new HashMap<>();
    private Map<String, ShoppingList> collabShoppingMap = new HashMap<>();
    private ListenerRegistration ownerShoppingListener;
    private ListenerRegistration collabShoppingListener;

    private void loadShoppingLists() {
        if (currentUser == null) return;
        
        if (ownerShoppingListener != null) ownerShoppingListener.remove();
        if (collabShoppingListener != null) collabShoppingListener.remove();
        
        ownerShoppingListener = db.collection("shopping_lists")
                .whereEqualTo("userId", currentUser.getUid())
                .addSnapshotListener((value, error) -> {
                    if (value != null) {
                        ownerShoppingMap.clear();
                        for (QueryDocumentSnapshot doc : value) {
                            ShoppingList sl = ShoppingList.fromDocument(doc);
                            if (sl.getItems() != null && !sl.getItems().isEmpty()) {
                                ownerShoppingMap.put(doc.getId(), sl);
                            }
                        }
                        mergeAndDisplayShoppingLists();
                    }
                });

        collabShoppingListener = db.collection("shopping_lists")
                .whereArrayContains("collaboratorIds", currentUser.getUid())
                .addSnapshotListener((value, error) -> {
                    if (value != null) {
                        collabShoppingMap.clear();
                        for (QueryDocumentSnapshot doc : value) {
                            ShoppingList sl = ShoppingList.fromDocument(doc);
                            if (sl.getItems() != null && !sl.getItems().isEmpty()) {
                                collabShoppingMap.put(doc.getId(), sl);
                            }
                        }
                        mergeAndDisplayShoppingLists();
                    }
                });
    }

    private void mergeAndDisplayShoppingLists() {
        shoppingLists.clear();
        Map<String, ShoppingList> all = new HashMap<>(ownerShoppingMap);
        all.putAll(collabShoppingMap);
        
        shoppingLists.addAll(all.values());
        shoppingLists.sort((l1, l2) -> Long.compare(l2.getCreatedAt(), l1.getCreatedAt()));
        
        shoppingListAdapter.notifyDataSetChanged();
        if (tvEmptyShoppingInline != null) {
            tvEmptyShoppingInline.setVisibility(shoppingLists.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        loadShoppingLists();
    }

    @Override
    public void onDestroyView() {
        if (ownerShoppingListener != null) {
            ownerShoppingListener.remove();
            ownerShoppingListener = null;
        }
        if (collabShoppingListener != null) {
            collabShoppingListener.remove();
            collabShoppingListener = null;
        }
        if (currentPlanListener != null) {
            currentPlanListener.remove();
            currentPlanListener = null;
        }
        super.onDestroyView();
    }

    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> parts = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            parts.add(new ArrayList<>(list.subList(i, Math.min(list.size(), i + size))));
        }
        return parts;
    }
}
