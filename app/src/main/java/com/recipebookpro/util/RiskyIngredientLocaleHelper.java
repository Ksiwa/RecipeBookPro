package com.recipebookpro.util;

import android.content.Context;
import android.text.TextUtils;

import com.google.android.gms.tasks.Tasks;
import com.recipebookpro.data.remote.MLKitTranslationService;
import com.recipebookpro.presentation.ui.LocaleHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ensures risky-ingredient labels shown in the health banner match the app UI language.
 */
public final class RiskyIngredientLocaleHelper {

    public interface Callback {
        void onReady(List<String> uiLanguageLabels);
    }

    private static final Map<String, String> TR_TO_EN = new HashMap<>();
    private static final Map<String, String> EN_TO_TR = new HashMap<>();

    static {
        putPair("süt", "milk");
        putPair("un", "flour");
        putPair("buğday", "wheat");
        putPair("şeker", "sugar");
        putPair("bal", "honey");
        putPair("şurup", "syrup");
        putPair("tuz", "salt");
        putPair("tereyağı", "butter");
        putPair("krema", "cream");
        putPair("yumurta", "egg");
        putPair("peynir", "cheese");
        putPair("toz şeker", "granulated sugar");
        putPair("pudra şekeri", "powdered sugar");
        putPair("laktoz", "lactose");
        putPair("irmik", "semolina");
    }

    private RiskyIngredientLocaleHelper() {
    }

    private static void putPair(String tr, String en) {
        TR_TO_EN.put(tr.toLowerCase(Locale.ROOT), en);
        EN_TO_TR.put(en.toLowerCase(Locale.ROOT), tr);
    }

    public static void ensureLanguage(Context context, List<String> labels, String targetLang, Callback callback) {
        if (labels == null || labels.isEmpty()) {
            callback.onReady(new ArrayList<>());
            return;
        }

        String target = ShoppingIngredientLocaleFix.normalizeTargetLang(targetLang);
        String joined = TextUtils.join(" ", labels).toLowerCase(Locale.ROOT);

        com.google.mlkit.nl.languageid.LanguageIdentifier identifier =
                com.google.mlkit.nl.languageid.LanguageIdentification.getClient();
        identifier.identifyLanguage(joined).addOnSuccessListener(sourceLang -> {
            String source = "und".equals(sourceLang) ? guessSource(target, labels) : sourceLang;
            if (source.equalsIgnoreCase(target)) {
                callback.onReady(new ArrayList<>(labels));
                return;
            }
            translateWithMlKit(context, labels, source, target, callback);
        }).addOnFailureListener(e ->
                callback.onReady(applyDictionaryFallback(labels, target)));
    }

    public static void ensureUiLanguage(Context context, List<String> labels, Callback callback) {
        ensureLanguage(context, labels, LocaleHelper.getLanguage(context), callback);
    }

    private static String guessSource(String targetLang, List<String> labels) {
        for (String label : labels) {
            for (String part : label.split("[/,]")) {
                String p = part.trim().toLowerCase(Locale.ROOT);
                if ("en".equals(targetLang) && TR_TO_EN.containsKey(p)) {
                    return "tr";
                }
                if ("tr".equals(targetLang) && EN_TO_TR.containsKey(p)) {
                    return "en";
                }
            }
        }
        return "tr".equals(targetLang) ? "en" : "tr";
    }

    private static void translateWithMlKit(Context context, List<String> labels,
                                           String sourceLang, String targetLang, Callback callback) {
        MLKitTranslationService translationService = new MLKitTranslationService(context);
        translationService.prepareModel(sourceLang, targetLang)
                .addOnSuccessListener(unused -> {
                    List<com.google.android.gms.tasks.Task<String>> tasks = new ArrayList<>();
                    for (String item : labels) {
                        tasks.add(translationService.translateSingleField(item, sourceLang, targetLang));
                    }
                    Tasks.whenAllComplete(tasks).addOnCompleteListener(allTasks -> {
                        List<String> translated = new ArrayList<>();
                        for (int i = 0; i < tasks.size(); i++) {
                            com.google.android.gms.tasks.Task<String> task = tasks.get(i);
                            if (task.isSuccessful() && task.getResult() != null && !task.getResult().trim().isEmpty()) {
                                translated.add(task.getResult().trim());
                            } else {
                                translated.add(dictionaryTranslate(labels.get(i), targetLang));
                            }
                        }
                        translationService.close();
                        callback.onReady(translated);
                    });
                })
                .addOnFailureListener(e -> {
                    translationService.close();
                    callback.onReady(applyDictionaryFallback(labels, targetLang));
                });
    }

    private static List<String> applyDictionaryFallback(List<String> labels, String targetLang) {
        List<String> out = new ArrayList<>();
        for (String label : labels) {
            out.add(dictionaryTranslate(label, targetLang));
        }
        return out;
    }

    private static String dictionaryTranslate(String label, String targetLang) {
        if (label == null) {
            return "";
        }
        String[] parts = label.split("[/,]");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            String lower = part.toLowerCase(Locale.ROOT);
            String mapped = part;
            if ("en".equals(targetLang)) {
                mapped = TR_TO_EN.getOrDefault(lower, part);
            } else if ("tr".equals(targetLang)) {
                mapped = EN_TO_TR.getOrDefault(lower, part);
            }
            if (i > 0) {
                builder.append(label.contains("/") ? "/" : ", ");
            }
            builder.append(mapped);
        }
        return builder.toString().trim();
    }
}
