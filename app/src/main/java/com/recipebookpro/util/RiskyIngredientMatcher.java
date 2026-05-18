package com.recipebookpro.util;

import com.recipebookpro.domain.model.Recipe;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Matches health-check "risky ingredient" labels to recipe rows across original / translated names.
 */
public final class RiskyIngredientMatcher {

    private RiskyIngredientMatcher() {
    }

    public static List<String> buildMatchTerms(Recipe recipe, List<String> riskyLabels) {
        Set<String> terms = new LinkedHashSet<>();
        if (riskyLabels != null) {
            for (String label : riskyLabels) {
                addLabelVariants(terms, label);
            }
        }
        if (recipe != null && recipe.getIngredients() != null && riskyLabels != null) {
            for (Recipe.Ingredient ing : recipe.getIngredients()) {
                for (String label : riskyLabels) {
                    if (ingredientMatchesLabel(ing, label, 1.0)) {
                        addNonEmpty(terms, ing.getName());
                        addNonEmpty(terms, ing.getDisplayName());
                    }
                }
            }
        }
        return new ArrayList<>(terms);
    }

    public static boolean termsOverlap(String term, String ingredientText) {
        if (term == null || ingredientText == null) {
            return false;
        }
        String t = term.toLowerCase(Locale.ROOT).trim();
        String hay = ingredientText.toLowerCase(Locale.ROOT);
        if (t.isEmpty() || hay.isEmpty()) {
            return false;
        }
        for (String part : splitLabelParts(term)) {
            if (matchesWordOrPhrase(hay, part)) {
                return true;
            }
            int termGroup = canonicalGroupIndex(part);
            if (termGroup >= 0 && canonicalGroupIndex(hay) == termGroup) {
                return true;
            }
        }
        return false;
    }

    public static boolean isRisky(Recipe.Ingredient ingredient, List<String> matchTerms, double scaleRatio) {
        if (ingredient == null || matchTerms == null || matchTerms.isEmpty()) {
            return false;
        }
        String combined = (ingredient.getName() + " "
                + ingredient.getDisplayName() + " "
                + ingredient.getDisplayText() + " "
                + ingredient.getScaledDisplayText(scaleRatio))
                .toLowerCase(Locale.ROOT);

        for (String term : matchTerms) {
            if (termsOverlap(term, combined)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWholeWordMatch(String text, String term) {
        String lowerText = text.toLowerCase(Locale.ROOT);
        String lowerTerm = term.toLowerCase(Locale.ROOT);
        int idx = lowerText.indexOf(lowerTerm);
        while (idx != -1) {
            boolean beforeOk = idx == 0 ||
                    !Character.isLetterOrDigit(lowerText.charAt(idx - 1));
            boolean afterOk = idx + lowerTerm.length() >= lowerText.length() ||
                    !Character.isLetterOrDigit(lowerText.charAt(idx + lowerTerm.length()));
            if (lowerTerm.equals("un")) {
                if (beforeOk) return true;
            } else {
                if (beforeOk && afterOk) return true;
            }
            idx = lowerText.indexOf(lowerTerm, idx + 1);
        }
        return false;
    }

    private static int canonicalGroupIndex(String text) {
        if (text == null) {
            return -1;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (int g = 0; g < CANONICAL_GROUPS.length; g++) {
            for (String word : CANONICAL_GROUPS[g]) {
                if (matchesWordOrPhrase(lower, word) || isWholeWordMatch(lower, word)) {
                    return g;
                }
            }
        }
        return -1;
    }

    private static final String[][] CANONICAL_GROUPS = {
            {"refined sugar", "rafine şeker", "sugar", "şeker", "seker", "toz şeker", "granulated sugar", "powdered sugar", "pudra", "bal", "honey", "reçel", "jam", "pekmez", "molasses", "şurup", "syrup", "meyve suyu", "fruit juice", "meyve konsantresi"},
            {"milk", "süt", "sut", "laktoz", "lactose", "dairy", "krema", "cream", "tam yağlı süt", "whole milk", "yarım yağlı süt", "skim milk"},
            {"flour", "un", "buğday", "wheat", "gluten", "irmik", "buğday unu", "wheat flour", "bazlama", "yufka", "erişte", "makarna", "börek", "poğaça", "simit", "pide", "lavaş", "galeta"},
            {"sodium", "sodyum", "tuz", "salt", "turşu", "pickle", "salamura", "soya sosu", "soy sauce", "zeytin", "olive", "konserve", "canned", "sucuk", "salam", "jambon"},
            {"egg", "yumurta"},
            {"saturated fat", "doymuş yağ", "sucuk", "salam", "sosis", "kavurma", "kaşar", "tam yağlı peynir", "tereyağı", "butter", "tereyagi", "margarin", "kuyruk yağı", "iç yağı", "bacon", "jambon", "cream", "krema"},
            {"limon", "lemon", "limon suyu", "lemon juice", "limon kabuğu", "lemon zest", "limon tuzu", "citric acid", "sitrik asit"},
    };

    private static boolean ingredientMatchesLabel(Recipe.Ingredient ingredient, String label, double scaleRatio) {
        if (ingredient == null || label == null || label.trim().isEmpty()) {
            return false;
        }
        String combined = (ingredient.getName() + " "
                + ingredient.getDisplayName() + " "
                + ingredient.getDisplayText() + " "
                + ingredient.getScaledDisplayText(scaleRatio))
                .toLowerCase(Locale.ROOT);
        return termsOverlap(label, combined);
    }

    private static void addLabelVariants(Set<String> terms, String label) {
        for (String part : splitLabelParts(label)) {
            addNonEmpty(terms, part);
            int group = canonicalGroupIndex(part);
            if (group >= 0) {
                for (String synonym : CANONICAL_GROUPS[group]) {
                    addNonEmpty(terms, synonym);
                }
            }
        }
    }

    private static void addNonEmpty(Set<String> terms, String value) {
        if (value != null && !value.trim().isEmpty()) {
            terms.add(value.trim());
        }
    }

    private static String[] splitLabelParts(String label) {
        return label.split("[/,]");
    }

    /**
     * Word-boundary match for short tokens (e.g. "un") to avoid false positives in longer words.
     */
    private static boolean matchesWordOrPhrase(String haystack, String needle) {
        String n = needle.toLowerCase(Locale.ROOT).trim();
        if (n.isEmpty() || haystack == null) {
            return false;
        }
        if (n.length() <= 3) {
            String regex = "(?i)(^|\\s|\\p{Punct})" + Pattern.quote(n) + "(\\p{L}{0,4})?(\\s|\\p{Punct}|$)";
            return Pattern.compile(regex).matcher(haystack).find();
        }
        return isWholeWordMatch(haystack, n);
    }
}
