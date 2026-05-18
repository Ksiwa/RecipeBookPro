package com.recipebookpro.domain.model;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BuiltInHealthTriggers {
    private static final Map<String, List<String>> TRIGGERS = new HashMap<>();

    static {
        TRIGGERS.put("diabetes", Arrays.asList(
            "şeker", "bal", "pekmez", "reçel", "şurup", "meyve suyu", "nişasta",
            "beyaz un", "pirinç", "patates", "mısır", "sugar", "honey", "syrup",
            "jam", "starch", "white flour", "rice", "potato", "corn"
        ));
        TRIGGERS.put("diyabet", TRIGGERS.get("diabetes"));

        TRIGGERS.put("celiac", Arrays.asList(
            "un", "buğday", "gluten", "arpa", "çavdar", "irmik", "bulgur",
            "wheat", "flour", "gluten", "barley", "rye", "semolina",
            "bazlama", "yufka", "erişte", "makarna", "börek", "poğaça", "simit", "pide", "lavaş", "galeta"
        ));
        TRIGGERS.put("çölyak", TRIGGERS.get("celiac"));

        TRIGGERS.put("hypertension", Arrays.asList(
            "tuz", "sodyum", "salamura", "turşu", "soya sosu", "konserve",
            "salt", "sodium", "soy sauce", "canned", "pickle"
        ));
        TRIGGERS.put("hipertansiyon", TRIGGERS.get("hypertension"));

        TRIGGERS.put("cardiovascular", Arrays.asList(
            "tereyağı", "krema", "tam yağlı", "kızartma", "margarin",
            "butter", "cream", "full fat", "fried", "margarine"
        ));
        TRIGGERS.put("kalp", TRIGGERS.get("cardiovascular"));

        TRIGGERS.put("kidney_disease", Arrays.asList(
            "tuz", "potasyum", "fosfor", "protein", "kırmızı et",
            "salt", "potassium", "phosphorus", "protein", "red meat"
        ));
        TRIGGERS.put("kidney", TRIGGERS.get("kidney_disease"));
        TRIGGERS.put("böbrek", TRIGGERS.get("kidney_disease"));

        TRIGGERS.put("ibs", Arrays.asList(
            "soğan", "sarımsak", "laktoz", "fruktoz", "gluten",
            "onion", "garlic", "lactose", "fructose", "gluten"
        ));
        TRIGGERS.put("irritabl", TRIGGERS.get("ibs"));
    }

    public static List<String> getTriggersFor(String condition) {
        if (condition == null) return null;
        String key = condition.toLowerCase(java.util.Locale.ROOT).trim();
        return TRIGGERS.get(key);
    }
}
