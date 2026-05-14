package com.recipebookpro.util;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Alışveriş satırı metninde dil ipuçları: ASCII-only Türkçe malzeme adları (ör. Kabartma tozu)
 * ML Kit "und" veya yanlış "en" dönünce çeviri atlanmasın diye kullanılır.
 */
public final class ShoppingLineLanguageHints {

    private static final Locale TR = new Locale("tr", "TR");
    private static final Pattern TURKISH_MARKERS = Pattern.compile("[ğüşıöçĞÜŞÖÇİı]");

    private static final String[] TR_INGREDIENT_HINTS = {
            "kabartma", "vanilya", "çikolata", "tereyağ", "şeker", "toz şeker", "tozşeker",
            "yumurta", "sarımsak", "soğan", "biber", "maydanoz", "portakal", "paket",
            "fındık", "ceviz", "tarçın", "zerdeçal", "kimyon", "nane", "kekik", "pul biber",
            "salça", "domates", "patates", "havuç", "fasulye", "mercimek", "bulgur", "pirinç",
            "makarna", "peynir", "krema", "yoğurt", "ayran", "zeytin", "turşu", "sirke",
            "puding", "kakao", "rende", "kabuk", "tozu", "bardak", "fincan", "kaşık", "çay",
            "kahve", "tuz", "baharat", "mayası", "hamur", "kıyma", "tavuk", "balık", "süt",
            "limon", "yağ", "tarif", "lezzet", "mutfak", "güzel", "kolay", "anne", "ev",
            "eksik", "malzeme", "malzemeler", "ihtiyaç", "lazım", "lazim", "gerekli",
            "alınacak", "alinacak", "alısveriş", "alisveris", "liste", "ürün", "urun",
            "unutma", "not", "taze", "paketle", "daha", "biraz", "yardım"
    };

    private ShoppingLineLanguageHints() {
    }

    public static boolean containsTurkishScript(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        return TURKISH_MARKERS.matcher(s).find();
    }

    /**
     * Hedef dil İngilizce iken satırın Türkçe kaynaklı çeviri gerektirip gerektirmediği.
     */
    public static boolean looksTurkishForEnglishTarget(String combined) {
        if (combined == null || combined.trim().isEmpty()) {
            return false;
        }
        if (containsTurkishScript(combined)) {
            return true;
        }
        String low = combined.toLowerCase(TR);
        for (String hint : TR_INGREDIENT_HINTS) {
            if (low.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    /** Hedef dil Türkçe iken metnin İngilizce kaynaklı çeviri gerektirip gerektirmediği (defter açıklaması vb.). */
    public static boolean looksEnglishForTurkishTarget(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        if (containsTurkishScript(text)) {
            return false;
        }
        String low = text.toLowerCase(Locale.ROOT);
        return EN_PROSE_HINT.matcher(low).find();
    }

    private static final Pattern EN_PROSE_HINT = Pattern.compile(
            "\\b(the|and|or|with|for|from|of|to|in|a|an|my|your|our|"
                    + "delicious|tasty|yummy|easy|best|great|good|nice|"
                    + "recipe|recipes|food|cook|cooking|kitchen|book|home|homemade|"
                    + "breakfast|lunch|dinner|dessert|snack|sweet|spicy|fresh|"
                    +             "chicken|beef|fish|vegetarian|vegan|healthy|quick)\\b",
            Pattern.CASE_INSENSITIVE);
}
