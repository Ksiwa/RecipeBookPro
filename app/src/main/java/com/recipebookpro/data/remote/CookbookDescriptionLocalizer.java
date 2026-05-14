package com.recipebookpro.data.remote;

import android.content.Context;

import com.google.android.gms.tasks.Tasks;
import com.recipebookpro.util.RecipeLanguageDetector;
import com.recipebookpro.util.ShoppingLineLanguageHints;

import java.util.Locale;

/**
 * Defter açıklamasını uygulama diline (tr/en) göre ML Kit ile çevirir; kaynak dil tahmini alışveriş
 * çevirisi ile aynı kurallara yakın tutulur.
 */
public final class CookbookDescriptionLocalizer {

    private CookbookDescriptionLocalizer() {
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

    /**
     * Senkron: arka plan iş parçacığında veya Tasks.await ile kullanılabilir.
     *
     * @return çeviri gerekmez veya başarısızsa orijinal metin
     */
    public static String localizeSync(Context appContext, String description, String targetLangRaw)
            throws Exception {
        if (description == null) {
            return "";
        }
        String raw = description.trim();
        if (raw.isEmpty()) {
            return "";
        }
        String targetLang = normalizeTargetLang(targetLangRaw);
        String detected = RecipeLanguageDetector.detectLanguageSync(raw);
        String mlSource = resolveMlSource(detected);
        if ("en".equals(targetLang)
                && ("und".equals(mlSource) || "en".equals(mlSource))
                && ShoppingLineLanguageHints.looksTurkishForEnglishTarget(raw)) {
            mlSource = "tr";
        }
        if ("tr".equals(targetLang)
                && ("und".equals(mlSource) || "tr".equals(mlSource))
                && ShoppingLineLanguageHints.looksEnglishForTurkishTarget(raw)) {
            mlSource = "en";
        }
        if ("und".equals(mlSource) || mlSource.equals(targetLang)) {
            return raw;
        }
        MLKitTranslationService svc = new MLKitTranslationService(appContext.getApplicationContext());
        try {
            Tasks.await(svc.prepareModel(mlSource, targetLang));
            String out = Tasks.await(svc.translateSingleField(raw, mlSource, targetLang));
            return out != null ? out.trim() : raw;
        } finally {
            svc.close();
        }
    }
}
