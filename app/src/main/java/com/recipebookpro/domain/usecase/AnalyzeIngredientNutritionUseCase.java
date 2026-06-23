package com.recipebookpro.domain.usecase;

import com.recipebookpro.domain.service.AiNutritionService;

public class AnalyzeIngredientNutritionUseCase {

    private final AiNutritionService aiNutritionService;

    public AnalyzeIngredientNutritionUseCase(AiNutritionService aiNutritionService) {
        this.aiNutritionService = aiNutritionService;
    }

    public interface Callback {
        void onSuccess(String nutritionText);

        void onError(String message);
    }

    public void execute(String ingredientsText, String languageCode, Callback callback) {
        if (ingredientsText == null || ingredientsText.trim().isEmpty()) {
            callback.onError("No ingredients to analyze");
            return;
        }
        aiNutritionService.analyzeMacrosFromIngredients(ingredientsText.trim(), normalizeLanguage(languageCode), new AiNutritionService.ResultCallback() {
            @Override
            public void onSuccess(String rawModelText) {
                callback.onSuccess(rawModelText);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    private String normalizeLanguage(String languageCode) {
        return "tr".equals(languageCode) ? "tr" : "en";
    }
}
