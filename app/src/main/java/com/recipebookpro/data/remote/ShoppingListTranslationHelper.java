package com.recipebookpro.data.remote;

import android.content.Context;

import com.google.android.gms.tasks.Tasks;
import com.recipebookpro.domain.model.ShoppingList.ShoppingItem;
import com.recipebookpro.util.FractionUtils;
import com.recipebookpro.util.RecipeLanguageDetector;
import com.recipebookpro.util.ShoppingIngredientLocaleFix;
import com.recipebookpro.util.ShoppingLineLanguageHints;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;

/**
 * Alışveriş listesi satırlarını uygulama diline göre çevirir ve miktarlardaki + birleşimlerini toplar.
 */
public final class ShoppingListTranslationHelper {

    private ShoppingListTranslationHelper() {
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

    private static String resolveMlSource(String detected) {
        if (detected == null || "und".equalsIgnoreCase(detected)) {
            return "und";
        }
        return normalizeTargetLang(detected);
    }

    private static String combineForDetect(ShoppingItem it) {
        StringBuilder sb = new StringBuilder();
        if (it.getAmount() != null) {
            sb.append(it.getAmount()).append(' ');
        }
        if (it.getUnit() != null) {
            sb.append(it.getUnit()).append(' ');
        }
        if (it.getName() != null) {
            sb.append(it.getName());
        }
        return sb.toString();
    }

    /**
     * Senkron: Worker veya arka plan iş parçacığı içinde Tasks.await kullanılabilir.
     *
     * @return herhangi bir alan değiştiyse true
     */
    public static boolean localizeItemsSync(Context appContext, List<ShoppingItem> items, String targetLangRaw)
            throws Exception {
        if (items == null || items.isEmpty()) {
            return false;
        }
        String targetLang = normalizeTargetLang(targetLangRaw);
        MLKitTranslationService svc = new MLKitTranslationService(appContext.getApplicationContext());
        List<ShoppingItem> snapshotBefore = copyItems(items);
        boolean changed = false;
        try {
            Set<String> preparedPairs = new HashSet<>();
            for (ShoppingItem it : items) {
                String combined = combineForDetect(it);
                String detected = RecipeLanguageDetector.detectLanguageSync(combined);
                String mlSource = resolveMlSource(detected);
                if ("en".equals(targetLang)
                        && ("und".equals(mlSource) || "en".equals(mlSource))
                        && ShoppingLineLanguageHints.looksTurkishForEnglishTarget(combined)) {
                    mlSource = "tr";
                }
                if ("tr".equals(targetLang)
                        && ("und".equals(mlSource) || "tr".equals(mlSource))
                        && ShoppingLineLanguageHints.looksEnglishForTurkishTarget(combined)) {
                    mlSource = "en";
                }
                if (!"und".equals(mlSource) && !mlSource.equals(targetLang)) {
                    String pair = mlSource + "->" + targetLang;
                    if (preparedPairs.add(pair)) {
                        Tasks.await(svc.prepareModel(mlSource, targetLang));
                    }
                }
                String name = it.getName() != null ? it.getName() : "";
                String unit = it.getUnit() != null ? it.getUnit() : "";
                String amt = it.getAmount() != null ? it.getAmount() : "";

                String newName = name;
                String newUnit = unit;
                if (!"und".equals(mlSource) && !mlSource.equals(targetLang)) {
                    if (!name.isEmpty()) {
                        newName = Tasks.await(svc.translateSingleField(name, mlSource, targetLang)).trim();
                    }
                    if (!unit.isEmpty()) {
                        newUnit = Tasks.await(svc.translateSingleField(unit, mlSource, targetLang)).trim();
                    }
                }
                double sum = FractionUtils.sumPlusSeparatedAmounts(amt);
                String newAmt = amt;
                if (sum > 0) {
                    newAmt = FractionUtils.formatShoppingAmount(sum);
                }
                if (!name.equals(newName) || !unit.equals(newUnit) || !amt.equals(newAmt)) {
                    changed = true;
                }
                it.setName(newName);
                it.setUnit(newUnit);
                it.setAmount(newAmt);
                ShoppingIngredientLocaleFix.applyPostTranslationFixes(it, targetLang);
                String n2 = it.getName();
                String u2 = it.getUnit();
                String a2 = it.getAmount();
                if (!newName.equals(n2) || !newUnit.equals(u2) || !newAmt.equals(a2)) {
                    changed = true;
                }
            }
            List<ShoppingItem> afterPass = copyItems(items);
            List<ShoppingItem> merged = ShoppingIngredientLocaleFix.consolidateDuplicateLines(copyItems(items), targetLang);
            if (!sortedFingerprint(afterPass).equals(sortedFingerprint(merged))) {
                changed = true;
            }
            items.clear();
            items.addAll(merged);
            if (!sortedFingerprint(snapshotBefore).equals(sortedFingerprint(items))) {
                changed = true;
            }
            return changed;
        } finally {
            svc.close();
        }
    }

    public static List<ShoppingItem> copyItems(List<ShoppingItem> src) {
        List<ShoppingItem> out = new ArrayList<>();
        if (src == null) {
            return out;
        }
        for (ShoppingItem s : src) {
            ShoppingItem c = new ShoppingItem(s.getName(), s.getAmount(), s.getUnit());
            c.setChecked(s.isChecked());
            c.setHomeStatus(s.getHomeStatus());
            c.setUserAdded(s.isUserAdded());
            out.add(c);
        }
        return out;
    }

    private static String sortedFingerprint(List<ShoppingItem> list) {
        List<String> lines = new ArrayList<>();
        for (ShoppingItem i : list) {
            lines.add(i.getDisplayText());
        }
        Collections.sort(lines);
        return String.join("\n", lines);
    }
}
