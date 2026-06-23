package com.recipebookpro.domain.service;

import com.recipebookpro.domain.model.Recipe;

/**
 * Uzaktan besin / kalori tahmini (ör. LLM API). Uygulama katmanı somut implementasyonu verir.
 */
public interface AiNutritionService {

    interface ResultCallback {
        void onSuccess(String rawModelText);

        void onError(String message);
    }

    void analyzeMacrosFromIngredients(String ingredientsText, String languageCode, ResultCallback callback);

    void estimateTotalCaloriesForRecipe(Recipe recipe, ResultCallback callback);
}
