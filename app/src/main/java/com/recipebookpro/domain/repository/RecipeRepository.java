package com.recipebookpro.domain.repository;

import com.recipebookpro.domain.model.Recipe;
import java.util.List;

public interface RecipeRepository {
    interface OnRecipesLoadedListener {
        void onLoaded(List<Recipe> recipes);
        void onError(Exception e);
    }

    interface OnRecipeActionCompleteListener {
        void onSuccess();
        void onError(Exception e);
    }

    void getRecipesByUserId(String userId, OnRecipesLoadedListener listener);
    void getAccessibleRecipesForPlanner(String userId, OnRecipesLoadedListener listener);
    void toggleRecipeLike(String userId, String recipeId, boolean shouldLike, OnRecipeActionCompleteListener listener);
    void updateRecipeCalories(String recipeId, int calories);
}
