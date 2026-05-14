package com.recipebookpro.data.worker;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.recipebookpro.data.remote.ShoppingListIngredientMergeHelper;
import com.recipebookpro.domain.model.Recipe;
import com.recipebookpro.domain.model.ShoppingList;
import com.recipebookpro.domain.model.ShoppingList.ShoppingItem;
import com.recipebookpro.util.ShoppingIngredientLocaleFix;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class MergeIngredientsWorker extends Worker {

    public static final String KEY_USER_ID = "user_id";
    public static final String KEY_RECIPE_IDS = "recipe_ids";
    public static final String KEY_LIST_NAME = "list_name";
    public static final String KEY_OVERWRITE_EXISTING = "overwrite_existing";
    public static final String KEY_TARGET_LANGUAGE = "target_language";

    public MergeIngredientsWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Data inputData = getInputData();
        String userId = inputData.getString(KEY_USER_ID);
        String[] recipeIds = inputData.getStringArray(KEY_RECIPE_IDS);
        String listName = inputData.getString(KEY_LIST_NAME);
        boolean overwriteExisting = inputData.getBoolean(KEY_OVERWRITE_EXISTING, false);
        String targetLang = inputData.getString(KEY_TARGET_LANGUAGE);
        if (targetLang == null || targetLang.isEmpty()) {
            targetLang = Locale.getDefault().getLanguage();
        }

        if (userId == null || recipeIds == null || listName == null) {
            return Result.failure();
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Map<String, ShoppingItem> mergedItems = new LinkedHashMap<>();

        try {
            List<Recipe> recipes = new ArrayList<>();
            for (String recipeId : recipeIds) {
                DocumentSnapshot doc = Tasks.await(db.collection("recipes").document(recipeId).get());
                if (doc.exists()) {
                    recipes.add(Recipe.fromDocument(doc));
                }
            }

            List<ShoppingItem> built;
            try {
                built = ShoppingListIngredientMergeHelper.mergeRecipes(getApplicationContext(), recipes, targetLang);
            } catch (Exception mergeEx) {
                mergeEx.printStackTrace();
                built = legacyMerge(recipes);
            }

            for (ShoppingItem it : built) {
                String key = shopMergeKey(it, targetLang);
                mergedItems.put(key, it);
            }

            QuerySnapshot existing = Tasks.await(
                    db.collection("shopping_lists")
                            .whereEqualTo("userId", userId)
                            .whereEqualTo("name", listName)
                            .limit(1)
                            .get());

            if (!existing.isEmpty()) {
                DocumentSnapshot existingDoc = existing.getDocuments().get(0);

                if (mergedItems.isEmpty()) {
                    Tasks.await(db.collection("shopping_lists").document(existingDoc.getId()).delete());
                } else {
                    ShoppingList existingList = ShoppingList.fromDocument(existingDoc);
                    if (!overwriteExisting) {
                        for (ShoppingItem item : existingList.getItems()) {
                            String key = shopMergeKey(item, targetLang);
                            if (!mergedItems.containsKey(key)) {
                                mergedItems.put(key, item);
                            }
                        }
                    }
                    List<ShoppingItem> finalItems = ShoppingIngredientLocaleFix.consolidateDuplicateLines(
                            new ArrayList<>(mergedItems.values()), targetLang);
                    existingList.setItems(finalItems);
                    Tasks.await(db.collection("shopping_lists")
                            .document(existingDoc.getId()).set(existingList));
                }
            } else {
                if (!mergedItems.isEmpty()) {
                    ShoppingList newList = new ShoppingList(userId, listName);
                    List<ShoppingItem> finalItems = ShoppingIngredientLocaleFix.consolidateDuplicateLines(
                            new ArrayList<>(mergedItems.values()), targetLang);
                    newList.setItems(finalItems);
                    DocumentReference newListRef = db.collection("shopping_lists").document();
                    newList.setId(newListRef.getId());
                    Tasks.await(newListRef.set(newList));
                }
            }

            return Result.success();

        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
            return Result.failure();
        }
    }

    private static String shopMergeKey(ShoppingItem it, String targetLang) {
        ShoppingItem copy = new ShoppingItem(it.getName(), it.getAmount(), it.getUnit());
        ShoppingIngredientLocaleFix.applyPostTranslationFixes(copy, targetLang);
        return ShoppingIngredientLocaleFix.canonicalMergeKeyAfterFix(copy, targetLang);
    }

    private static List<ShoppingItem> legacyMerge(List<Recipe> recipes) {
        Map<String, ShoppingItem> map = new LinkedHashMap<>();
        for (Recipe recipe : recipes) {
            if (recipe.getIngredients() == null) {
                continue;
            }
            for (Recipe.Ingredient ing : recipe.getIngredients()) {
                String key = ing.getName().toLowerCase(Locale.ROOT).trim();
                if (map.containsKey(key)) {
                    ShoppingItem existing = map.get(key);
                    existing.setAmount(existing.getAmount() + " + " + ing.getAmount());
                } else {
                    map.put(key, new ShoppingItem(ing.getName(), ing.getAmount(), ing.getUnit()));
                }
            }
        }
        return new ArrayList<>(map.values());
    }
}
