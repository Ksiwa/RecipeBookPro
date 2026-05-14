package com.recipebookpro.util;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.recipebookpro.domain.model.Recipe;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tarif veya serbest metin için ML Kit + anahtar kelime ile tr/en kaynak dili tahmini
 * (TranslateRecipeUseCase ile uyumlu, senkron kullanım için Tasks.await).
 */
public final class RecipeLanguageDetector {

    private static final String EN_KEYWORDS =
            "\\b(cup|water|milk|egg|eggs|piece|tablespoon|teaspoon|sugar|salt|oil|flour|butter|garlic|onion|pepper|chicken|meat|beef|cook|fry|boil|bake|gram|pinch|cocoa)\\b";
    private static final String TR_KEYWORDS =
            "\\b(su|süt|yumurta|adet|kaşık|şeker|tuz|yağ|un|tereyağı|sarımsak|soğan|biber|tavuk|et|pişir|kızart|kaynat|fırın|bardak|fincan|rende|paket|üzeri)\\b";

    private RecipeLanguageDetector() {
    }

    public static String detectFromRecipe(Recipe recipe) {
        if (recipe == null) {
            return "und";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(recipe.getTitle()).append(' ');
        sb.append(recipe.getDescription()).append(' ');
        if (recipe.getIngredients() != null) {
            for (Recipe.Ingredient ing : recipe.getIngredients()) {
                sb.append(ing.getName()).append(' ');
                sb.append(ing.getUnit()).append(' ');
            }
        }
        return detectLanguageSync(sb.toString());
    }

    public static String detectLanguageSync(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "und";
        }
        String lower = text.toLowerCase();
        try {
            String sourceLang = Tasks.await(LanguageIdentification.getClient().identifyLanguage(text));
            String resolved = sourceLang != null ? sourceLang : "und";
            int enCount = countMatches(lower, EN_KEYWORDS);
            int trCount = countMatches(lower, TR_KEYWORDS);
            if ("und".equals(resolved) || Math.abs(enCount - trCount) > 1) {
                if (enCount > trCount) {
                    resolved = "en";
                } else if (trCount > enCount) {
                    resolved = "tr";
                }
            }
            return resolved;
        } catch (Exception e) {
            int enCount = countMatches(lower, EN_KEYWORDS);
            int trCount = countMatches(lower, TR_KEYWORDS);
            if (enCount > trCount) {
                return "en";
            }
            if (trCount > enCount) {
                return "tr";
            }
            return "und";
        }
    }

    private static int countMatches(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        int count = 0;
        while (m.find()) {
            count++;
        }
        return count;
    }
}
