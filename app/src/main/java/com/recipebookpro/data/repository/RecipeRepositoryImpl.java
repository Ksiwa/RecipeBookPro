package com.recipebookpro.data.repository;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.recipebookpro.domain.model.Cookbook;
import com.recipebookpro.domain.model.Recipe;
import com.recipebookpro.domain.model.RecipeCollection;
import com.recipebookpro.domain.repository.RecipeRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RecipeRepositoryImpl implements RecipeRepository {

    private final FirebaseFirestore db;

    public RecipeRepositoryImpl() {
        this.db = FirebaseFirestore.getInstance();
    }

    @Override
    public void getRecipesByUserId(String userId, OnRecipesLoadedListener listener) {
        db.collection("recipes")
            .whereEqualTo("userId", userId)
            .addSnapshotListener((snapshots, error) -> {
                if (error != null || snapshots == null) {
                    listener.onError(error != null ? error : new Exception("Unknown error"));
                    return;
                }

                List<Recipe> allRecipes = new ArrayList<>();
                Set<String> addedIds = new HashSet<>();
                for (QueryDocumentSnapshot doc : snapshots) {
                    if (addedIds.add(doc.getId())) {
                        allRecipes.add(Recipe.fromDocument(doc));
                    }
                }
                
                Collections.sort(allRecipes, (r1, r2) -> Long.compare(r2.getCreatedAt(), r1.getCreatedAt()));
                listener.onLoaded(allRecipes);
            });
    }

    @Override
    public void getAccessibleRecipesForPlanner(String userId, OnRecipesLoadedListener listener) {
        if (userId == null || userId.trim().isEmpty()) {
            listener.onLoaded(new ArrayList<>());
            return;
        }

        Map<String, Recipe> mergedById = new LinkedHashMap<>();
        Set<String> idsFromContainers = new HashSet<>();
        final int[] pendingSources = {4};
        final boolean[] completed = {false};
        final Exception[] firstError = {null};

        Runnable sourceDone = () -> {
            pendingSources[0]--;
            if (pendingSources[0] == 0 && !completed[0]) {
                fetchMissingAccessibleRecipes(mergedById, idsFromContainers, listener, firstError[0]);
                completed[0] = true;
            }
        };

        db.collection("recipes")
                .whereEqualTo("userId", userId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        for (QueryDocumentSnapshot doc : task.getResult()) {
                            Recipe recipe = Recipe.fromDocument(doc);
                            mergedById.put(recipe.getId(), recipe);
                        }
                    } else if (firstError[0] == null) {
                        firstError[0] = task.getException();
                    }
                    sourceDone.run();
                });

        db.collection("cookbooks")
                .whereEqualTo("userId", userId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        collectRecipeIdsFromCookbookQuery(task.getResult(), idsFromContainers);
                    } else if (firstError[0] == null) {
                        firstError[0] = task.getException();
                    }
                    sourceDone.run();
                });

        db.collection("cookbooks")
                .whereArrayContains("collaboratorIds", userId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        collectRecipeIdsFromCookbookQuery(task.getResult(), idsFromContainers);
                    } else if (firstError[0] == null) {
                        firstError[0] = task.getException();
                    }
                    sourceDone.run();
                });

        db.collection("collections")
                .whereEqualTo("userId", userId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        collectRecipeIdsFromCollectionQuery(task.getResult(), idsFromContainers);
                    } else if (firstError[0] == null) {
                        firstError[0] = task.getException();
                    }
                    sourceDone.run();
                });
    }

    private void fetchMissingAccessibleRecipes(
            Map<String, Recipe> mergedById,
            Set<String> idsFromContainers,
            OnRecipesLoadedListener listener,
            Exception firstError
    ) {
        List<String> missingIds = new ArrayList<>();
        for (String recipeId : idsFromContainers) {
            if (recipeId != null && !recipeId.isEmpty() && !mergedById.containsKey(recipeId)) {
                missingIds.add(recipeId);
            }
        }

        if (missingIds.isEmpty()) {
            publishAccessibleRecipes(mergedById, listener, firstError);
            return;
        }

        List<List<String>> chunks = partition(missingIds, 10);
        final int[] pendingChunks = {chunks.size()};

        for (List<String> chunk : chunks) {
            db.collection("recipes")
                    .whereIn(FieldPath.documentId(), chunk)
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && task.getResult() != null) {
                            for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                                Recipe recipe = Recipe.fromDocument(doc);
                                mergedById.put(recipe.getId(), recipe);
                            }
                        }
                        pendingChunks[0]--;
                        if (pendingChunks[0] == 0) {
                            publishAccessibleRecipes(mergedById, listener, firstError);
                        }
                    });
        }
    }

    private void publishAccessibleRecipes(
            Map<String, Recipe> mergedById,
            OnRecipesLoadedListener listener,
            Exception firstError
    ) {
        List<Recipe> recipes = new ArrayList<>(mergedById.values());
        if (!recipes.isEmpty() || firstError == null) {
            listener.onLoaded(recipes);
        } else {
            listener.onError(firstError);
        }
    }

    private void collectRecipeIdsFromCookbookQuery(QuerySnapshot snapshot, Set<String> outIds) {
        if (snapshot == null) return;
        for (QueryDocumentSnapshot doc : snapshot) {
            Cookbook book = Cookbook.fromDocument(doc);
            for (String recipeId : book.getRecipeIds()) {
                if (recipeId != null && !recipeId.isEmpty()) {
                    outIds.add(recipeId);
                }
            }
        }
    }

    private void collectRecipeIdsFromCollectionQuery(QuerySnapshot snapshot, Set<String> outIds) {
        if (snapshot == null) return;
        for (QueryDocumentSnapshot doc : snapshot) {
            RecipeCollection collection = RecipeCollection.fromDocument(doc);
            for (String recipeId : collection.getRecipeIds()) {
                if (recipeId != null && !recipeId.isEmpty()) {
                    outIds.add(recipeId);
                }
            }
        }
    }

    @Override
    public void toggleRecipeLike(
            String userId,
            String recipeId,
            boolean shouldLike,
            OnRecipeActionCompleteListener listener
    ) {
        if (userId == null || userId.trim().isEmpty() || recipeId == null || recipeId.trim().isEmpty()) {
            listener.onError(new IllegalArgumentException("Missing user or recipe id"));
            return;
        }

        DocumentReference userRef = db.collection("users").document(userId);
        DocumentReference recipeRef = db.collection("recipes").document(recipeId);

        db.runTransaction(transaction -> {
            DocumentSnapshot userDoc = transaction.get(userRef);
            DocumentSnapshot recipeDoc = transaction.get(recipeRef);
            if (!recipeDoc.exists()) {
                throw new IllegalStateException("Recipe not found");
            }

            List<String> likedIds = (List<String>) userDoc.get("likedRecipeIds");
            boolean alreadyLiked = likedIds != null && likedIds.contains(recipeId);
            if (shouldLike == alreadyLiked) {
                return null;
            }

            transaction.update(userRef, "likedRecipeIds",
                    shouldLike ? FieldValue.arrayUnion(recipeId) : FieldValue.arrayRemove(recipeId));

            long currentLikes = recipeDoc.getLong("likes") != null ? recipeDoc.getLong("likes") : 0L;
            long delta = shouldLike ? 1L : -1L;
            if (!shouldLike && currentLikes <= 0L) {
                transaction.update(recipeRef, "likes", 0L);
            } else {
                transaction.update(recipeRef, "likes", FieldValue.increment(delta));
            }
            return null;
        }).addOnSuccessListener(unused -> listener.onSuccess())
                .addOnFailureListener(listener::onError);
    }

    @Override
    public void updateRecipeCalories(String recipeId, int calories) {
        db.collection("recipes").document(recipeId).update("calories", calories);
    }

    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> parts = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            parts.add(new ArrayList<>(list.subList(i, Math.min(list.size(), i + size))));
        }
        return parts;
    }
}
