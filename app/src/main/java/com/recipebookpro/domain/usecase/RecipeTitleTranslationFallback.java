package com.recipebookpro.domain.usecase;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class RecipeTitleTranslationFallback {

    private static final String LANG_EN = "en";
    private static final String LANG_TR = "tr";

    private static final Locale TR_LOCALE = new Locale("tr", "TR");
    private static final Map<String, String> TR_TO_EN_PHRASES = new LinkedHashMap<>();
    private static final Map<String, String> EN_TO_TR_PHRASES = new LinkedHashMap<>();
    private static final Map<String, String> TR_TO_EN_WORDS = new LinkedHashMap<>();
    private static final Map<String, String> EN_TO_TR_WORDS = new LinkedHashMap<>();

    static {
        putPhrase("kuru fasulye", "Dry Beans");
        putPhrase("kuru fasulye yemeği", "Dry Beans");
        putPhrase("kuru fasulye yemegi", "Dry Beans");
        putPhrase("tarhana çorbası", "Tarhana Soup");
        putPhrase("tarhana corbasi", "Tarhana Soup");
        putPhrase("mercimek çorbası", "Lentil Soup");
        putPhrase("mercimek corbasi", "Lentil Soup");
        putPhrase("tavuk çorbası", "Chicken Soup");
        putPhrase("tavuk corbasi", "Chicken Soup");
        putPhrase("domates çorbası", "Tomato Soup");
        putPhrase("domates corbasi", "Tomato Soup");
        putPhrase("ezogelin çorbası", "Ezogelin Soup");
        putPhrase("yayla çorbası", "Yogurt Soup");
        putPhrase("yayla corbasi", "Yogurt Soup");
        putPhrase("pirinç pilavı", "Rice Pilaf");
        putPhrase("pirinc pilavi", "Rice Pilaf");
        putPhrase("bulgur pilavı", "Bulgur Pilaf");
        putPhrase("bulgur pilavi", "Bulgur Pilaf");
        putPhrase("tavuklu pilav", "Chicken Rice");
        putPhrase("çoban salata", "Shepherd Salad");
        putPhrase("çoban salatası", "Shepherd Salad");
        putPhrase("coban salatasi", "Shepherd Salad");
        putPhrase("patates salatası", "Potato Salad");
        putPhrase("patates salatasi", "Potato Salad");
        putPhrase("ton balıklı salata", "Tuna Salad");
        putPhrase("zeytinyağlı sarma", "Stuffed Vine Leaves With Olive Oil");
        putPhrase("zeytinyagli sarma", "Stuffed Vine Leaves With Olive Oil");
        putPhrase("yaprak sarma", "Stuffed Vine Leaves");
        putPhrase("biber dolması", "Stuffed Peppers");
        putPhrase("biber dolmasi", "Stuffed Peppers");
        putPhrase("kabak dolması", "Stuffed Zucchini");
        putPhrase("kabak dolmasi", "Stuffed Zucchini");
        putPhrase("içli köfte", "Stuffed Meatballs");
        putPhrase("icli kofte", "Stuffed Meatballs");
        putPhrase("mercimek köftesi", "Lentil Balls");
        putPhrase("mercimek koftesi", "Lentil Balls");
        putPhrase("anne köftesi", "Homemade Meatballs");
        putPhrase("anne koftesi", "Homemade Meatballs");
        putPhrase("ıslak kek", "Wet Cake");
        putPhrase("islak kek", "Wet Cake");
        putPhrase("sütlaç", "Rice Pudding");
        putPhrase("sutlac", "Rice Pudding");
        putPhrase("menemen", "Menemen");
        putPhrase("mücver", "Zucchini Fritters");

        putWord("kuru", "dry");
        putWord("fasulye", "beans");
        putWord("fasulyesi", "beans");
        putWord("mercimek", "lentil");
        putWord("nohut", "chickpea");
        putWord("bulgur", "bulgur");
        putWord("pirinç", "rice");
        putWord("tavuk", "chicken");
        putWord("tavuklu", "chicken");
        putWord("et", "meat");
        putWord("etli", "meat");
        putWord("kıymalı", "ground beef");
        putWord("patates", "potato");
        putWord("domates", "tomato");
        putWord("kabak", "zucchini");
        putWord("biber", "pepper");
        putWord("ıspanak", "spinach");
        putWord("peynir", "cheese");
        putWord("peynirli", "cheese");
        putWord("yoğurt", "yogurt");
        putWord("yoğurtlu", "yogurt");
        putWord("sarımsaklı", "garlic");
        putWord("zeytinyağlı", "with olive oil");
        putWord("çikolatalı", "chocolate");
        putWord("elmalı", "apple");
        putWord("limonlu", "lemon");
        putWord("havuçlu", "carrot");
        putWord("cevizli", "walnut");
        putWord("fındıklı", "hazelnut");
        putWord("mantarlı", "mushroom");
        putWord("sebzeli", "vegetable");
        putWord("sade", "plain");
        putWord("ev", "homemade");
        putWord("anne", "homemade");
        putWord("usulü", "style");
        putWord("çorba", "soup");
        putWord("çorbası", "soup");
        putWord("corba", "soup");
        putWord("corbasi", "soup");
        putWord("pilav", "pilaf");
        putWord("pilavı", "pilaf");
        putWord("pilavi", "pilaf");
        putWord("salata", "salad");
        putWord("salatası", "salad");
        putWord("salatasi", "salad");
        putWord("köfte", "meatballs");
        putWord("köftesi", "meatballs");
        putWord("kofte", "meatballs");
        putWord("koftesi", "meatballs");
        putWord("börek", "pastry");
        putWord("böreği", "pastry");
        putWord("borek", "pastry");
        putWord("boregi", "pastry");
        putWord("dolma", "stuffed");
        putWord("dolması", "stuffed");
        putWord("dolmasi", "stuffed");
        putWord("sarma", "rolls");
        putWord("sarması", "rolls");
        putWord("makarna", "pasta");
        putWord("makarnası", "pasta");
        putWord("kek", "cake");
        putWord("keki", "cake");
        putWord("kurabiye", "cookies");
        putWord("tatlı", "dessert");
        putWord("tatlısı", "dessert");
        putWord("tatli", "dessert");
        putWord("tatlisi", "dessert");
        putWord("yemeği", "dish");
        putWord("yemegi", "dish");
        putWord("yemek", "dish");
    }

    private RecipeTitleTranslationFallback() {
    }

    static String resolve(String originalTitle, String candidateTitle, String targetLang) {
        String original = clean(originalTitle);
        String candidate = clean(candidateTitle);
        String target = normalizeLanguage(targetLang);

        if (original.isEmpty()) {
            return candidate;
        }

        if (!candidate.isEmpty()
                && !candidate.equalsIgnoreCase(original)
                && appearsToMatchTarget(candidate, target)) {
            return candidate;
        }

        String fallback = fallbackTranslate(original, target);
        if (!fallback.equalsIgnoreCase(original)) {
            return fallback;
        }

        return candidate.isEmpty() ? original : candidate;
    }

    private static String fallbackTranslate(String title, String targetLang) {
        String lower = title.toLowerCase(TR_LOCALE);
        if (LANG_EN.equals(targetLang)) {
            String phrase = TR_TO_EN_PHRASES.get(lower);
            if (phrase != null) {
                return phrase;
            }
            return translateByWords(title, TR_TO_EN_WORDS, true);
        }
        if (LANG_TR.equals(targetLang)) {
            String phrase = EN_TO_TR_PHRASES.get(lower);
            if (phrase != null) {
                return phrase;
            }
            return translateByWords(title, EN_TO_TR_WORDS, false);
        }
        return title;
    }

    private static String translateByWords(String title, Map<String, String> dictionary, boolean titleCase) {
        String[] words = title.trim().split("\\s+");
        StringBuilder translated = new StringBuilder();
        boolean changed = false;
        for (String word : words) {
            String key = word.toLowerCase(TR_LOCALE);
            String value = dictionary.get(key);
            if (value == null) {
                value = word;
            } else {
                changed = true;
            }
            if (translated.length() > 0) {
                translated.append(' ');
            }
            translated.append(value);
        }
        if (!changed) {
            return title;
        }
        return titleCase ? toTitleCase(translated.toString()) : toSentenceCase(translated.toString());
    }

    private static boolean appearsToMatchTarget(String text, String targetLang) {
        if (LANG_EN.equals(targetLang)) {
            return !looksTurkish(text);
        }
        if (LANG_TR.equals(targetLang)) {
            return !looksEnglishRecipeTitle(text) || looksTurkish(text);
        }
        return true;
    }

    private static boolean looksTurkish(String text) {
        String lower = text.toLowerCase(TR_LOCALE);
        return lower.matches(".*[çğıöşü].*")
                || containsAny(lower, TR_TO_EN_WORDS)
                || TR_TO_EN_PHRASES.containsKey(lower);
    }

    private static boolean looksEnglishRecipeTitle(String text) {
        String lower = text.toLowerCase(Locale.US);
        return containsAny(lower, EN_TO_TR_WORDS) || EN_TO_TR_PHRASES.containsKey(lower);
    }

    private static boolean containsAny(String lowerText, Map<String, String> dictionary) {
        for (String key : dictionary.keySet()) {
            if (lowerText.matches(".*\\b" + java.util.regex.Pattern.quote(key) + "\\b.*")) {
                return true;
            }
        }
        return false;
    }

    private static void putPhrase(String tr, String en) {
        TR_TO_EN_PHRASES.put(tr, en);
        EN_TO_TR_PHRASES.put(en.toLowerCase(Locale.US), toSentenceCase(tr));
    }

    private static void putWord(String tr, String en) {
        TR_TO_EN_WORDS.put(tr, en);
        EN_TO_TR_WORDS.put(en.toLowerCase(Locale.US), tr);
    }

    private static String clean(String text) {
        return text == null ? "" : text.trim().replaceAll("\\s+", " ");
    }

    private static String normalizeLanguage(String language) {
        if (language == null) {
            return "";
        }
        String trimmed = language.trim().toLowerCase(Locale.US);
        return trimmed.length() >= 2 ? trimmed.substring(0, 2) : trimmed;
    }

    private static String toTitleCase(String text) {
        String[] parts = text.trim().split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(part.substring(0, 1).toUpperCase(Locale.US))
                    .append(part.substring(1).toLowerCase(Locale.US));
        }
        return builder.toString();
    }

    private static String toSentenceCase(String text) {
        String trimmed = text.trim().toLowerCase(TR_LOCALE);
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        return trimmed.substring(0, 1).toUpperCase(TR_LOCALE) + trimmed.substring(1);
    }
}
