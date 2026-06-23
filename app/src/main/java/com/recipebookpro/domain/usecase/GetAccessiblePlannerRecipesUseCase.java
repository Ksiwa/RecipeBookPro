package com.recipebookpro.domain.usecase;

import com.recipebookpro.domain.model.Recipe;
import com.recipebookpro.domain.repository.RecipeRepository;
import com.recipebookpro.domain.service.RecipeFamilyDeduplicator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GetAccessiblePlannerRecipesUseCase {

    private final RecipeRepository recipeRepository;

    public GetAccessiblePlannerRecipesUseCase(RecipeRepository recipeRepository) {
        this.recipeRepository = recipeRepository;
    }

    public void execute(String userId, RecipeRepository.OnRecipesLoadedListener listener) {
        recipeRepository.getAccessibleRecipesForPlanner(userId, new RecipeRepository.OnRecipesLoadedListener() {
            @Override
            public void onLoaded(List<Recipe> recipes) {
                List<Recipe> displayRecipes = RecipeFamilyDeduplicator.keepLatestRecipePerFamily(
                        recipes != null ? recipes : new ArrayList<>());
                Collections.sort(displayRecipes, (a, b) -> a.getTitle().compareToIgnoreCase(b.getTitle()));
                listener.onLoaded(displayRecipes);
            }

            @Override
            public void onError(Exception e) {
                listener.onError(e);
            }
        });
    }
}
