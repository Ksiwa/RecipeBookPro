package com.recipebookpro.util;

import com.recipebookpro.domain.model.ShoppingList.ShoppingItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Alışveriş satırlarında ML Kit sonrası Türkçe/İngilizce düzeltmeler ve
 * aynı malzemenin farklı yazımlarını (yumurta / yumurtalar, adet / parça / sayısı) tek anahtarda birleştirmek.
 */
public final class ShoppingIngredientLocaleFix {

    private static final Locale TR = new Locale("tr", "TR");
    private static final String COUNT_KEY_TR = "__adet__";
    private static final String COUNT_KEY_EN = "__piece__";
    private static final String EMPTY_UNIT_KEY = "__empty__";

    private static final Pattern TR_COUNT_UNIT = Pattern.compile(
            "^(adet|tane|parça|parca|parçası|sayısı|sayisi|piece|pieces|count|number|nos?\\.?)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern EN_COUNT_UNIT = Pattern.compile(
            "^(piece|pieces|count|number|adet|pcs?\\.?)$",
            Pattern.CASE_INSENSITIVE);

    private ShoppingIngredientLocaleFix() {
    }

    public static String normalizeTargetLang(String lang) {
        if (lang == null) {
            return "en";
        }
        String l = lang.toLowerCase(Locale.ROOT);
        if (l.startsWith("tr")) {
            return "tr";
        }
        return "en";
    }

    /**
     * ML veya ham metin sonrası: masa kaşığı düzeltmesi, adet birimi, yumurta tekil adı vb.
     */
    public static void applyPostTranslationFixes(ShoppingItem item, String targetLang) {
        if (item == null) {
            return;
        }
        String tl = normalizeTargetLang(targetLang);
        if ("tr".equals(tl)) {
            applyTurkishTextPatches(item);
            normalizeTurkishCountUnitAndName(item);
        } else {
            applyEnglishEggPlural(item);
        }
    }

    /** ML Kit "cup" → yanlış iyelik ekli ölçü birimi. */
    private static final Pattern TR_WRONG_KUPASI = Pattern.compile(
            "\\b[Kk]upası\\b", Pattern.UNICODE_CASE);
    /** "fincan" için benzer yanlış çeviri. */
    private static final Pattern TR_WRONG_FINCANI = Pattern.compile(
            "\\b[Ff]incanı\\b", Pattern.UNICODE_CASE);
    /** Tarif yönergesi "yukarıdaki limon" → malzeme adı olarak kalmış. */
    private static final Pattern TR_LEMON_ABOVE = Pattern.compile(
            "\\b(?:yukarıdaki|üstteki|altta\\s+belirtilen|yukarıda\\s+belirtilen)"
                    + "(?:\\s+taze)?\\s+limon(?:lar)?\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    /** İngilizce "pinch" (tutam) bazen sıkıştırma fiiline çevriliyor. */
    private static final Pattern TR_PINCH_VERB_IN_UNIT = Pattern.compile(
            "\\b(?:sıkıştır(?:mak|ma|mış|ım)?|sıkış(?:tır)?)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    /** Birim alanında kalan İngilizce "squeeze" (tuz bağlamında pinch). */
    private static final Pattern EN_SQUEEZE_IN_UNIT = Pattern.compile("\\bsqueeze\\b", Pattern.CASE_INSENSITIVE);
    /** Ölçü birimi yanında kalan anlamsız un kısaltması (ör. AP / BM). */
    private static final Pattern TR_STANDALONE_FLOUR_ABBREV = Pattern.compile(
            "^(?:bm|b\\.\\s*m\\.?|ap|a\\.\\s*p\\.?)$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static void applyTurkishTextPatches(ShoppingItem item) {
        String name = item.getName() != null ? item.getName() : "";
        String unit = item.getUnit() != null ? item.getUnit() : "";
        name = replaceMasaKasigi(name);
        unit = replaceMasaKasigi(unit);
        name = patchTurkishWrongPossessiveMeasures(name);
        unit = patchTurkishWrongPossessiveMeasures(unit);
        name = patchTurkishLemonInstructionArtifacts(name);
        unit = patchTurkishPinchUnitForSalt(unit, name);
        name = patchTurkishAbbreviatedFlourName(name, unit);
        item.setName(name);
        item.setUnit(unit);
    }

    private static String patchTurkishWrongPossessiveMeasures(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        String t = TR_WRONG_KUPASI.matcher(s).replaceAll("kupa");
        return TR_WRONG_FINCANI.matcher(t).replaceAll("fincan");
    }

    private static String patchTurkishLemonInstructionArtifacts(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        return TR_LEMON_ABOVE.matcher(name).replaceAll("limon kabuğu rendesi");
    }

    private static String patchTurkishPinchUnitForSalt(String unit, String name) {
        if (unit == null || unit.isEmpty()) {
            return unit != null ? unit : "";
        }
        String nm = name != null ? name.toLowerCase(TR) : "";
        if (!nm.contains("tuz")) {
            return unit;
        }
        if (TR_PINCH_VERB_IN_UNIT.matcher(unit).find()
                || unit.toLowerCase(Locale.ROOT).contains("squeeze")) {
            String u = TR_PINCH_VERB_IN_UNIT.matcher(unit).replaceAll("tutam");
            u = EN_SQUEEZE_IN_UNIT.matcher(u).replaceAll("tutam");
            return u.replaceAll("\\s{2,}", " ").trim();
        }
        return unit;
    }

    private static String patchTurkishAbbreviatedFlourName(String name, String unit) {
        if (name == null) {
            return "";
        }
        String trimmed = name.trim();
        if (!TR_STANDALONE_FLOUR_ABBREV.matcher(trimmed).matches()) {
            return name;
        }
        String ul = unit != null ? unit.toLowerCase(TR) : "";
        boolean volumetric = ul.contains("bardak")
                || ul.contains("kupa")
                || ul.contains("fincan")
                || ul.contains("kaşık")
                || ul.contains("kasik");
        boolean weight = ul.contains("gram")
                || ul.contains("gr ")
                || ul.matches(".*\\bgr\\b.*")
                || ul.endsWith("gr");
        if (volumetric || weight) {
            return "Çok amaçlı un";
        }
        return "Un";
    }

    private static String replaceMasaKasigi(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        String lower = s.toLowerCase(TR);
        if (!lower.contains("masa") || !lower.contains("kaşığ")) {
            return s;
        }
        Matcher m = Pattern.compile("masa\\s*kaşığ[ıiIİ]?[a-zıiğüşöç]*", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                .matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement("yemek kaşığı"));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static void normalizeTurkishCountUnitAndName(ShoppingItem item) {
        String unit = item.getUnit() != null ? item.getUnit().trim() : "";
        String name = item.getName() != null ? item.getName().trim() : "";
        if (TR_COUNT_UNIT.matcher(unit).matches()) {
            item.setUnit("adet");
        } else if (unit.isEmpty() && FractionUtils.sumPlusSeparatedAmounts(item.getAmount()) > 0
                && !name.isEmpty() && name.toLowerCase(TR).contains("yumurta")) {
            item.setUnit("adet");
        }
        String n = item.getName();
        if (n != null) {
            item.setName(singularizeTurkishYumurta(n));
        }
    }

    private static String singularizeTurkishYumurta(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        String low = name.toLowerCase(TR);
        if (!low.contains("yumurta")) {
            return capitalizeTrSentence(name);
        }
        String replaced = Pattern.compile("\\byumurtalar\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                .matcher(name).replaceAll("yumurta");
        return capitalizeTrSentence(replaced);
    }

    private static String capitalizeTrSentence(String s) {
        String t = s.trim();
        if (t.isEmpty()) {
            return "";
        }
        String lower = t.toLowerCase(TR);
        return lower.substring(0, 1).toUpperCase(TR) + lower.substring(1);
    }

    private static void applyEnglishEggPlural(ShoppingItem item) {
        String name = item.getName();
        if (name == null) {
            return;
        }
        String low = name.toLowerCase(Locale.ROOT);
        if (low.contains("eggs")) {
            item.setName(capitalizeEnWord(name.replaceAll("(?i)\\beggs\\b", "egg")));
        }
    }

    private static String capitalizeEnWord(String s) {
        String t = s.trim();
        if (t.isEmpty()) {
            return "";
        }
        return t.substring(0, 1).toUpperCase(Locale.ROOT) + t.substring(1).toLowerCase(Locale.ROOT);
    }

    public static String canonicalMergeKey(ShoppingItem item, String targetLang) {
        if (item == null) {
            return "|";
        }
        ShoppingItem w = new ShoppingItem(item.getName(), item.getAmount(), item.getUnit());
        w.setHomeStatus(item.getHomeStatus());
        applyPostTranslationFixes(w, targetLang);
        return canonicalMergeKeyAfterFix(w, targetLang);
    }

    public static String canonicalMergeKeyAfterFix(ShoppingItem item, String targetLang) {
        String tl = normalizeTargetLang(targetLang);
        if ("tr".equals(tl)) {
            return normalizeTurkishNameKey(item.getName()) + "|"
                    + normalizeTurkishUnitKey(item.getName(), item.getUnit());
        }
        return normalizeEnglishNameKey(item.getName()) + "|"
                + normalizeEnglishUnitKey(item.getName(), item.getUnit());
    }

    private static String normalizeTurkishNameKey(String name) {
        if (name == null) {
            return "";
        }
        String s = name.toLowerCase(TR).trim().replaceAll("\\s+", " ");
        s = s.replaceAll("\\byumurtalar\\b", "yumurta");
        s = s.replaceAll("\\byumurta\\b", "yumurta");
        return s;
    }

    private static String normalizeTurkishUnitKey(String ingredientName, String unit) {
        String u = unit != null ? unit.trim() : "";
        String nm = ingredientName != null ? ingredientName.toLowerCase(TR) : "";
        if (u.isEmpty()) {
            if (nm.contains("yumurta")) {
                return COUNT_KEY_TR;
            }
            return EMPTY_UNIT_KEY;
        }
        String ul = u.toLowerCase(TR);
        if (TR_COUNT_UNIT.matcher(ul).matches() || "adet".equals(ul)) {
            return COUNT_KEY_TR;
        }
        return replaceMasaKasigi(ul).toLowerCase(TR).trim();
    }

    private static String normalizeEnglishNameKey(String name) {
        if (name == null) {
            return "";
        }
        return name.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ")
                .replaceAll("\\beggs\\b", "egg");
    }

    private static String normalizeEnglishUnitKey(String ingredientName, String unit) {
        String u = unit != null ? unit.trim() : "";
        String nm = ingredientName != null ? ingredientName.toLowerCase(Locale.ROOT) : "";
        if (u.isEmpty()) {
            if (nm.contains("egg")) {
                return COUNT_KEY_EN;
            }
            return EMPTY_UNIT_KEY;
        }
        String ul = u.toLowerCase(Locale.ROOT);
        if (EN_COUNT_UNIT.matcher(ul).matches()) {
            return COUNT_KEY_EN;
        }
        return ul;
    }

    /**
     * Aynı canonical anahtara düşen satırları birleştirir (ör. 3 adet + 3 adet yumurta → 6 adet yumurta).
     */
    public static List<ShoppingItem> consolidateDuplicateLines(List<ShoppingItem> items, String targetLang) {
        if (items == null || items.isEmpty()) {
            return new ArrayList<>();
        }
        String tl = normalizeTargetLang(targetLang);
        Map<String, SlotAgg> map = new LinkedHashMap<>();
        for (ShoppingItem src : items) {
            ShoppingItem w = new ShoppingItem(src.getName(), src.getAmount(), src.getUnit());
            w.setChecked(src.isChecked());
            w.setHomeStatus(src.getHomeStatus());
            w.setUserAdded(src.isUserAdded());
            applyPostTranslationFixes(w, tl);
            String key = canonicalMergeKeyAfterFix(w, tl);
            SlotAgg slot = map.get(key);
            if (slot == null) {
                slot = new SlotAgg();
                slot.displayName = displayNameForSlot(w.getName(), tl);
                slot.displayUnit = displayUnitForSlot(w.getUnit(), key, tl);
                slot.checked = w.isChecked();
                slot.homeStatus = w.getHomeStatus();
                slot.userAdded = src.isUserAdded();
                map.put(key, slot);
            } else {
                slot.checked = slot.checked || w.isChecked();
                slot.userAdded = slot.userAdded || src.isUserAdded();
            }
            double piece = FractionUtils.sumPlusSeparatedAmounts(w.getAmount());
            if (piece > 0) {
                slot.numericSum += piece;
            } else if (w.getAmount() != null && !w.getAmount().trim().isEmpty()) {
                slot.nonNumericAmounts.add(w.getAmount().trim());
            }
        }
        List<ShoppingItem> out = new ArrayList<>();
        for (SlotAgg slot : map.values()) {
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
            ShoppingItem it = new ShoppingItem(slot.displayName, amountStr, slot.displayUnit);
            it.setChecked(slot.checked);
            it.setHomeStatus(slot.homeStatus);
            it.setUserAdded(slot.userAdded);
            out.add(it);
        }
        return out;
    }

    private static String displayNameForSlot(String nameAfterFix, String targetLang) {
        if ("tr".equals(normalizeTargetLang(targetLang))) {
            return capitalizeTrSentence(nameAfterFix);
        }
        return capitalizeEnWord(nameAfterFix);
    }

    private static String displayUnitForSlot(String unitAfterFix, String canonicalKey, String targetLang) {
        String tl = normalizeTargetLang(targetLang);
        if ("tr".equals(tl)) {
            if (canonicalKey != null && canonicalKey.endsWith("|" + COUNT_KEY_TR)) {
                return "adet";
            }
            return unitAfterFix != null ? unitAfterFix : "";
        }
        if (canonicalKey != null && canonicalKey.endsWith("|" + COUNT_KEY_EN)) {
            return "piece";
        }
        return unitAfterFix != null ? unitAfterFix : "";
    }

    private static final class SlotAgg {
        String displayName;
        String displayUnit;
        double numericSum;
        final List<String> nonNumericAmounts = new ArrayList<>();
        boolean checked;
        String homeStatus;
        boolean userAdded;
    }
}
