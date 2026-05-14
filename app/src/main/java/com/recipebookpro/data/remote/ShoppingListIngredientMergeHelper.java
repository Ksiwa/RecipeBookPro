package com.recipebookpro.data.remote;

import android.content.Context;

import com.google.android.gms.tasks.Tasks;
import com.recipebookpro.domain.model.Recipe;
import com.recipebookpro.domain.model.ShoppingList.ShoppingItem;
import com.recipebookpro.util.FractionUtils;
import com.recipebookpro.util.RecipeLanguageDetector;
import com.recipebookpro.util.ShoppingIngredientLocaleFix;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * Planner / tarif ekranından alışveriş listesi: malzemeleri uygulama diline çevirip
 * aynı isim+birim satırlarında miktarları toplar (1 + 1 + 1 → 3).
 */
public final class ShoppingListIngredientMergeHelper {

    private ShoppingListIngredientMergeHelper() {
    }

    private static String normalizeTargetLang(String lang) {
        if (lang == null) {
            return "en";
        }
        String l = lang.toLowerCase(Locale.ROOT);
        if (l.startsWith("tr")) {
            return "tr";
        }
        return "en";
    }

    /** ML Kit için yalnızca tr/en; und ayrı kalır (çeviri yapılmaz). */
    private static String resolveMlSource(String detected) {
        if (detected == null || "und".equalsIgnoreCase(detected)) {
            return "und";
        }
        return normalizeTargetLang(detected);
    }

    private static String translateField(
            MLKitTranslationService svc,
            String sourceLang,
            String targetLang,
            String text
    ) throws Exception {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }
        String trimmed = text.trim();
        if (sourceLang.equalsIgnoreCase(targetLang) || "und".equalsIgnoreCase(sourceLang)) {
            return trimmed;
        }
        com.google.android.gms.tasks.Task<String> t = svc.translateSingleField(trimmed, sourceLang, targetLang);
        String out = Tasks.await(t);
        return out != null ? out.trim() : trimmed;
    }

    private static final class Slot {
        String name;
        String unit;
        double numericSum;
        final List<String> nonNumericAmounts = new ArrayList<>();
    }

    public static List<ShoppingItem> mergeRecipes(
            Context appContext,
            List<Recipe> recipes,
            String targetLangRaw
    ) throws Exception {
        String targetLang = normalizeTargetLang(targetLangRaw);
        MLKitTranslationService svc = new MLKitTranslationService(appContext.getApplicationContext());
        try {
            Map<String, Slot> map = new LinkedHashMap<>();
            Set<String> preparedPairs = new HashSet<>();

            for (Recipe recipe : recipes) {
                if (recipe == null || recipe.getIngredients() == null) {
                    continue;
                }
                String sourceLang = RecipeLanguageDetector.detectFromRecipe(recipe);
                String mlSource = resolveMlSource(sourceLang);
                if (!"und".equals(mlSource) && !mlSource.equals(targetLang)) {
                    String pair = mlSource + "->" + targetLang;
                    if (preparedPairs.add(pair)) {
                        Tasks.await(svc.prepareModel(mlSource, targetLang));
                    }
                }

                for (Recipe.Ingredient ing : recipe.getIngredients()) {
                    String rawName = ing.getName() != null ? ing.getName() : "";
                    String rawUnit = ing.getUnit() != null ? ing.getUnit() : "";
                    String tName = translateField(svc, mlSource, targetLang, rawName);
                    String tUnit = translateField(svc, mlSource, targetLang, rawUnit);
                    ShoppingItem line = new ShoppingItem(tName, "", tUnit);
                    ShoppingIngredientLocaleFix.applyPostTranslationFixes(line, targetLang);
                    String key = ShoppingIngredientLocaleFix.canonicalMergeKeyAfterFix(line, targetLang);
                    Slot slot = map.get(key);
                    if (slot == null) {
                        slot = new Slot();
                        slot.name = line.getName();
                        slot.unit = line.getUnit();
                        map.put(key, slot);
                    }
                    String rawAmt = ing.getAmount() != null ? ing.getAmount().trim() : "";
                    double piece = FractionUtils.sumPlusSeparatedAmounts(rawAmt);
                    if (piece > 0) {
                        slot.numericSum += piece;
                    } else if (!rawAmt.isEmpty()) {
                        slot.nonNumericAmounts.add(rawAmt);
                    }
                }
            }

            List<ShoppingItem> out = new ArrayList<>();
            for (Slot slot : map.values()) {
                String amountStr = "";
                if (slot.numericSum > 0) {
                    amountStr = FractionUtils.formatShoppingAmount(slot.numericSum);
                }
                if (!slot.nonNumericAmounts.isEmpty()) {
                    String joined = String.join(" + ", slot.nonNumericAmounts);
                    if (amountStr.isEmpty()) {
                        amountStr = joined;
                    } else {
                        amountStr = amountStr + " + " + joined;
                    }
                }
                ShoppingItem outIt = new ShoppingItem(slot.name, amountStr, slot.unit);
                outIt.setUserAdded(false);
                out.add(outIt);
            }
            out = ShoppingIngredientLocaleFix.consolidateDuplicateLines(out, targetLang);
            Collections.sort(out, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            return out;
        } finally {
            svc.close();
        }
    }
}
