package com.recipebookpro.worker;

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
import com.recipebookpro.model.Recipe;
import com.recipebookpro.model.ShoppingList;
import com.recipebookpro.model.ShoppingList.ShoppingItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class MergeIngredientsWorker extends Worker {

    public static final String KEY_USER_ID = "user_id";
    public static final String KEY_RECIPE_IDS = "recipe_ids"; // Array of strings
    public static final String KEY_LIST_NAME = "list_name";

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

        if (userId == null || recipeIds == null || recipeIds.length == 0 || listName == null) {
            return Result.failure();
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Map<String, ShoppingItem> mergedItems = new HashMap<>();

        try {
            for (String recipeId : recipeIds) {
                DocumentSnapshot doc = Tasks.await(db.collection("recipes").document(recipeId).get());
                if (doc.exists()) {
                    Recipe recipe = Recipe.fromDocument(doc);
                    if (recipe.getIngredients() != null) {
                        for (Recipe.Ingredient ing : recipe.getIngredients()) {
                            String key = ing.getName().toLowerCase().trim();
                            if (mergedItems.containsKey(key)) {
                                // Basic appending logic, since parsing amounts and units across 
                                // different strings accurately is complex without NLP
                                ShoppingItem existing = mergedItems.get(key);
                                existing.setAmount(existing.getAmount() + " + " + ing.getAmount());
                            } else {
                                mergedItems.put(key, new ShoppingItem(ing.getName(), ing.getAmount(), ing.getUnit()));
                            }
                        }
                    }
                }
            }

            QuerySnapshot existing = Tasks.await(
                    db.collection("shopping_lists")
                            .whereEqualTo("userId", userId)
                            .whereEqualTo("name", listName)
                            .limit(1)
                            .get());

            if (!existing.isEmpty()) {
                DocumentSnapshot existingDoc = existing.getDocuments().get(0);
                ShoppingList existingList = ShoppingList.fromDocument(existingDoc);
                for (ShoppingItem item : existingList.getItems()) {
                    String key = item.getName().toLowerCase().trim();
                    if (!mergedItems.containsKey(key)) {
                        mergedItems.put(key, item);
                    }
                }
                existingList.setItems(new ArrayList<>(mergedItems.values()));
                Tasks.await(db.collection("shopping_lists")
                        .document(existingDoc.getId()).set(existingList));
            } else {
                ShoppingList newList = new ShoppingList(userId, listName);
                newList.setItems(new ArrayList<>(mergedItems.values()));
                DocumentReference newListRef = db.collection("shopping_lists").document();
                newList.setId(newListRef.getId());
                Tasks.await(newListRef.set(newList));
            }

            return Result.success();

        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
            return Result.failure();
        }
    }
}
