package com.recipebookpro.domain.usecase;

import com.recipebookpro.domain.repository.RecipeRepository;

public class ToggleRecipeLikeUseCase {

    private final RecipeRepository recipeRepository;

    public ToggleRecipeLikeUseCase(RecipeRepository recipeRepository) {
        this.recipeRepository = recipeRepository;
    }

    public void execute(
            String userId,
            String recipeId,
            boolean shouldLike,
            RecipeRepository.OnRecipeActionCompleteListener listener
    ) {
        recipeRepository.toggleRecipeLike(userId, recipeId, shouldLike, listener);
    }
}
