package com.recipebookpro.domain.usecase;

import com.recipebookpro.domain.model.Recipe;
import com.recipebookpro.domain.repository.HealthCheckRepository;

import java.util.List;
import java.util.Map;

public class CheckRecipeSafetyUseCase {

    private final HealthCheckRepository repository;

    public CheckRecipeSafetyUseCase(HealthCheckRepository repository) {
        this.repository = repository;
    }

    public void execute(Recipe recipe,
                        List<String> healthConditions,
                        List<String> customHealthConditions,
                        List<String> allergens,
                        Map<String, List<String>> healthTriggers,
                        String uiLangCode,
                        HealthCheckRepository.HealthCheckCallback callback) {
        repository.checkRecipeSafety(
                recipe,
                healthConditions,
                customHealthConditions,
                allergens,
                healthTriggers,
                uiLangCode,
                callback
        );
    }
}
