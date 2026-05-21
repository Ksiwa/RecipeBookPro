package com.recipebookpro.domain.usecase;

import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentifier;
import com.recipebookpro.domain.model.Recipe;
import com.recipebookpro.domain.model.Step;
import com.recipebookpro.domain.service.TranslationService;

import java.util.ArrayList;
import java.util.List;

public class TranslateRecipeUseCase {

    private static final String TAG = "TranslateUseCase";
    private static final String LANG_EN = "en";
    private static final String LANG_TR = "tr";
    private final TranslationService translationService;

    public TranslateRecipeUseCase(TranslationService translationService) {
        this.translationService = translationService;
    }

    public void execute(Recipe recipe, String targetLang, TranslationService.TranslationCallback callback) {
        if (recipe == null) {
            callback.onFailure(new IllegalArgumentException("Recipe is null"));
            return;
        }
        String normalizedTargetLang = normalizeSupportedLanguage(targetLang, LANG_EN);

        // 1. Build a robust text for detection
        StringBuilder sbDetection = new StringBuilder();
        sbDetection.append(recipe.getTitle()).append(" ").append(recipe.getDescription()).append(" ");
        if (recipe.getIngredients() != null) {
            for (Recipe.Ingredient ing : recipe.getIngredients()) {
                sbDetection.append(ing.getName()).append(" ").append(ing.getUnit()).append(" ");
            }
        }
        String text = sbDetection.toString().toLowerCase();
        
        callback.onDownloadProgress("Analyzing language...");

        LanguageIdentifier identifier = LanguageIdentification.getClient();
        identifier.identifyLanguage(text)
                .addOnSuccessListener(sourceLang -> {
                    String finalSource = sourceLang;
                    
                    // 2. Enhanced Keyword Balance Analysis
                    String enKeywords = "\\b(cup|water|milk|egg|piece|tablespoon|teaspoon|sugar|salt|oil|flour|butter|garlic|onion|pepper|salt|chicken|meat|beef|cook|fry|boil|bake)\\b";
                    String trKeywords = "\\b(su|süt|yumurta|adet|kaşık|şeker|tuz|yağ|un|tereyağı|sarımsak|soğan|biber|tavuk|et|pişir|kızart|kaynat|fırın|bardak|fincan)\\b";
                    
                    int enCount = countMatches(text, enKeywords);
                    int trCount = countMatches(text, trKeywords);
                    
                    if ("und".equals(sourceLang) || Math.abs(enCount - trCount) > 1) {
                        if (enCount > trCount) finalSource = LANG_EN;
                        else if (trCount > enCount) finalSource = LANG_TR;
                    }
                    
                    finalSource = normalizeSupportedLanguage(finalSource, normalizedTargetLang);
                    recipe.setOriginalLanguage(finalSource);
                    Log.d(TAG, "Detection result: " + finalSource + " (EN:" + enCount + ", TR:" + trCount + ")");
                    
                    if (finalSource.equalsIgnoreCase(normalizedTargetLang)) {
                        translateTitleOnlyIfNeeded(recipe, normalizedTargetLang, callback);
                    } else {
                        prepareAndThenTranslate(recipe, finalSource, normalizedTargetLang, callback);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Detection failed", e);
                    callback.onFailure(e);
                });
    }

    private int countMatches(String text, String regex) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(regex).matcher(text);
        int count = 0;
        while (m.find()) count++;
        return count;
    }

    private void translateTitleOnlyIfNeeded(Recipe recipe, String targetLang, TranslationService.TranslationCallback callback) {
        detectSingleFieldLanguage(recipe.getTitle(), recipe.getOriginalLanguage(), titleSource -> {
            String normalizedTitleSource = normalizeSupportedLanguage(titleSource, recipe.getOriginalLanguage());
            if (normalizedTitleSource.equalsIgnoreCase(targetLang)) {
                recipe.clearAllTranslations();
                callback.onSuccess("Match found");
                return;
            }

            callback.onDownloadProgress("Downloading translation models...");
            translationService.prepareModel(normalizedTitleSource, targetLang)
                    .addOnSuccessListener(unused -> {
                        callback.onDownloadProgress("Translating recipe title...");
                        translationService.translateSingleField(recipe.getTitle(), normalizedTitleSource, targetLang)
                                .addOnSuccessListener(translatedTitle -> {
                                    recipe.clearAllTranslations();
                                    recipe.setTranslatedTitle(resolveTranslatedTitle(recipe.getTitle(), translatedTitle, targetLang));
                                    recipe.setTranslationLanguage(targetLang);
                                    callback.onSuccess("Success");
                                })
                                .addOnFailureListener(callback::onFailure);
                    })
                    .addOnFailureListener(callback::onFailure);
        });
    }

    private void detectSingleFieldLanguage(String text, String fallback, LanguageCallback callback) {
        if (text == null || text.trim().isEmpty()) {
            callback.onResolved(normalizeSupportedLanguage(fallback, LANG_EN));
            return;
        }

        LanguageIdentification.getClient().identifyLanguage(text)
                .addOnSuccessListener(detected -> {
                    String lower = text.toLowerCase();
                    int enCount = countMatches(lower, "\\b(soup|salad|rice|meatball|pastry|chicken|beef|cake|cookie|bread)\\b");
                    int trCount = countMatches(lower, "\\b(çorba|çorbası|tarhana|pilav|pilavı|salata|salatası|köfte|börek|dolma|kurabiye|ekmek|kuru|fasulye|fasulyesi)\\b");
                    String resolved = detected == null ? "und" : detected;
                    if ("und".equals(resolved) || Math.abs(enCount - trCount) > 0) {
                        if (enCount > trCount) {
                            resolved = LANG_EN;
                        } else if (trCount > enCount) {
                            resolved = LANG_TR;
                        }
                    }
                    callback.onResolved(normalizeSupportedLanguage(resolved, fallback));
                })
                .addOnFailureListener(e -> callback.onResolved(normalizeSupportedLanguage(fallback, LANG_EN)));
    }

    private String normalizeSupportedLanguage(String language, String fallback) {
        String normalized = normalizeLanguage(language);
        if (LANG_EN.equals(normalized) || LANG_TR.equals(normalized)) {
            return normalized;
        }
        String normalizedFallback = normalizeLanguage(fallback);
        if (LANG_EN.equals(normalizedFallback) || LANG_TR.equals(normalizedFallback)) {
            return normalizedFallback;
        }
        return LANG_EN;
    }

    private String normalizeLanguage(String language) {
        if (language == null) {
            return "";
        }
        String trimmed = language.trim().toLowerCase();
        return trimmed.length() >= 2 ? trimmed.substring(0, 2) : trimmed;
    }

    private void prepareAndThenTranslate(Recipe recipe, String source, String target, TranslationService.TranslationCallback callback) {
        callback.onDownloadProgress("Downloading translation models...");
        
        translationService.prepareModel(source, target)
                .addOnSuccessListener(unused -> {
                    Log.d(TAG, "Models ready. Starting field translation...");
                    startFieldTranslation(recipe, source, target, callback);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Model preparation failed", e);
                    callback.onFailure(e);
                });
    }

    private void startFieldTranslation(Recipe recipe, String source, String target, TranslationService.TranslationCallback callback) {
        callback.onDownloadProgress("Translating recipe details...");

        detectSingleFieldLanguage(recipe.getTitle(), source, titleSource -> {
            String normalizedTitleSource = normalizeSupportedLanguage(titleSource, source);
            startFieldTranslationWithTitleSource(recipe, source, normalizedTitleSource, target, callback);
        });
    }

    private void startFieldTranslationWithTitleSource(Recipe recipe, String source, String titleSource, String target,
                                                      TranslationService.TranslationCallback callback) {
        List<Task<String>> tasks = new ArrayList<>();

        // Order is critical for applyResults mapping
        if (titleSource.equalsIgnoreCase(target)) {
            tasks.add(Tasks.forResult(recipe.getTitle()));
        } else {
            tasks.add(translationService.translateSingleField(recipe.getTitle(), titleSource, target));
        }
        tasks.add(translationService.translateSingleField(recipe.getDescription(), source, target));

        if (recipe.getIngredients() != null) {
            for (Recipe.Ingredient ing : recipe.getIngredients()) {
                tasks.add(translationService.translateSingleField(ing.getName(), source, target));
                if (ing.getUnit() != null && !ing.getUnit().isEmpty()) {
                    tasks.add(translationService.translateSingleField(ing.getUnit(), source, target));
                }
            }
        }

        if (recipe.getStepList() != null) {
            for (Step step : recipe.getStepList()) {
                tasks.add(translationService.translateSingleField(step.getDescription(), source, target));
            }
        }

        if (recipe.getAllergens() != null) {
            for (String allergen : recipe.getAllergens()) {
                tasks.add(translationService.translateSingleField(allergen, source, target));
            }
        }

        Tasks.whenAllComplete(tasks).addOnCompleteListener(allTasks -> {
            if (allTasks.isSuccessful()) {
                applyResults(recipe, tasks, target);
                callback.onSuccess("Success");
            } else {
                callback.onFailure(new Exception("Some fields failed to translate"));
            }
        });
    }

    private void applyResults(Recipe recipe, List<Task<String>> tasks, String targetLang) {
        int index = 0;
        recipe.setTranslatedTitle(resolveTranslatedTitle(recipe.getTitle(), getTaskResult(tasks.get(index++), recipe.getTitle(), targetLang), targetLang));
        recipe.setTranslationLanguage(targetLang);
        recipe.setTranslatedDescription(getTaskResult(tasks.get(index++), recipe.getDescription(), targetLang));

        if (recipe.getIngredients() != null) {
            for (Recipe.Ingredient ing : recipe.getIngredients()) {
                ing.setTranslatedName(getTaskResult(tasks.get(index++), ing.getName(), targetLang));
                if (ing.getUnit() != null && !ing.getUnit().isEmpty()) {
                    ing.setTranslatedUnit(getTaskResult(tasks.get(index++), ing.getUnit(), targetLang));
                } else {
                    ing.setTranslatedUnit("");
                }
            }
        }

        if (recipe.getStepList() != null) {
            for (Step step : recipe.getStepList()) {
                step.setTranslatedDescription(getTaskResult(tasks.get(index++), step.getDescription(), targetLang));
            }
        }

        // Rebuild steps text
        StringBuilder sb = new StringBuilder();
        if (recipe.getStepList() != null) {
            for (Step s : recipe.getStepList()) {
                sb.append(s.getDisplayDescription()).append("\n");
            }
        }
        recipe.setTranslatedInstructions(sb.toString().trim());

        List<String> translatedAllergens = new ArrayList<>();
        if (recipe.getAllergens() != null) {
            for (String allergen : recipe.getAllergens()) {
                translatedAllergens.add(getTaskResult(tasks.get(index++), allergen, targetLang));
            }
        }
        recipe.setTranslatedAllergens(translatedAllergens);
    }

    private String getTaskResult(Task<String> task, String originalText, String targetLang) {
        if (task.isSuccessful() && task.getResult() != null) {
            String result = task.getResult().trim();
            // Manual Patch: If AI returns the same short word, use a small internal dictionary
            if (result.equalsIgnoreCase(originalText.trim()) && originalText.length() < 5) {
                return applyManualPatch(originalText.trim().toLowerCase(), targetLang);
            }
            return result;
        }
        return originalText; // Fallback to original if failed
    }

    private String resolveTranslatedTitle(String originalTitle, String translatedTitle, String targetLang) {
        return RecipeTitleTranslationFallback.resolve(originalTitle, translatedTitle, targetLang);
    }

    private String applyTitlePatch(String title, String targetLang) {
        String input = title.trim();
        String lower = input.toLowerCase();
        if (LANG_EN.equals(targetLang)) {
            if (lower.equals("kuru fasulye") || lower.equals("kuru fasulye yemeği")) {
                return "Dry Beans";
            }
            if (lower.equals("tarhana çorbası") || lower.equals("tarhana çorba")) {
                return "Tarhana Soup";
            }
            if (lower.endsWith(" çorbası") || lower.endsWith(" çorba")) {
                return toTitleCase(input.replaceFirst("(?i)\\s+çorbası$", "").replaceFirst("(?i)\\s+çorba$", "") + " Soup");
            }
            if (lower.endsWith(" salatası") || lower.endsWith(" salata")) {
                return toTitleCase(input.replaceFirst("(?i)\\s+salatası$", "").replaceFirst("(?i)\\s+salata$", "") + " Salad");
            }
            if (lower.endsWith(" pilavı") || lower.endsWith(" pilav")) {
                return toTitleCase(input.replaceFirst("(?i)\\s+pilavı$", "").replaceFirst("(?i)\\s+pilav$", "") + " Rice");
            }
        } else if (LANG_TR.equals(targetLang)) {
            if (lower.equals("dry beans")) {
                return "Kuru fasulye";
            }
            if (lower.equals("tarhana soup")) {
                return "Tarhana çorbası";
            }
            if (lower.endsWith(" soup")) {
                return toSentenceCase(input.replaceFirst("(?i)\\s+soup$", "") + " çorbası");
            }
            if (lower.endsWith(" salad")) {
                return toSentenceCase(input.replaceFirst("(?i)\\s+salad$", "") + " salatası");
            }
            if (lower.endsWith(" rice")) {
                return toSentenceCase(input.replaceFirst("(?i)\\s+rice$", "") + " pilavı");
            }
        }
        return title;
    }

    private String toTitleCase(String text) {
        String[] parts = text.trim().split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (builder.length() > 0) builder.append(' ');
            builder.append(part.substring(0, 1).toUpperCase()).append(part.substring(1).toLowerCase());
        }
        return builder.toString();
    }

    private String toSentenceCase(String text) {
        String trimmed = text.trim().toLowerCase();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        return trimmed.substring(0, 1).toUpperCase() + trimmed.substring(1);
    }

    private String applyManualPatch(String text, String targetLang) {
        String input = text.trim().toLowerCase();
        if (targetLang.equals("en")) {
            if (input.equals("un")) return "flour";
            if (input.equals("su")) return "water";
            if (input.equals("et")) return "meat";
            if (input.equals("tuz")) return "salt";
            if (input.equals("süt")) return "milk";
            if (input.equals("yağ")) return "oil";
            if (input.equals("karis") || input.equals("karış")) return "span";
            if (input.equals("adet")) return "piece";
            if (input.equals("bardak")) return "glass";
            if (input.equals("kaşık")) return "spoon";
            if (input.equals("mısır") || input.equals("misir")) return "corn";
            if (input.equals("maydanoz")) return "parsley";
            if (input.equals("dereotu")) return "dill";
            if (input.equals("un")) return "flour";
        } else if (targetLang.equals("tr")) {
            if (input.equals("flour")) return "un";
            if (input.equals("water")) return "su";
            if (input.equals("meat")) return "et";
            if (input.equals("salt")) return "tuz";
            if (input.equals("milk")) return "süt";
            if (input.equals("oil")) return "yağ";
            if (input.equals("span") || input.equals("hand span")) return "karış";
            if (input.equals("piece")) return "adet";
            if (input.equals("glass") || input.equals("cup")) return "bardak";
            if (input.equals("spoon")) return "kaşık";
            if (input.equals("corn")) return "mısır";
            if (input.equals("parsley")) return "maydanoz";
            if (input.equals("dill")) return "dereotu";
        }
        return text;
    }

    private interface LanguageCallback {
        void onResolved(String languageCode);
    }
}
