package com.recipebookpro.domain.repository;

import com.recipebookpro.domain.model.Recipe;

import java.util.List;
import java.util.Map;

public interface HealthCheckRepository {
    interface HealthCheckCallback {
        void onResult(String recipeId, boolean isSafe, String rationale, List<String> riskyIngredients);
        void onError(String recipeId, String errorMessage);
    }

    void checkRecipeSafety(Recipe recipe,
                           List<String> healthConditions,
                           List<String> customHealthConditions,
                           List<String> allergens,
                           Map<String, List<String>> healthTriggers,
                           String uiLangCode,
                           HealthCheckCallback callback);
}
