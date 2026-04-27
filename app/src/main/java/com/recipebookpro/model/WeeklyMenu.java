package com.recipebookpro.model;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.Exclude;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WeeklyMenu implements Serializable {

    public static final String[] DAY_KEYS = {
        "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"
    };

    public static final String[] DAY_LABELS_TR = {
        "Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar"
    };

    private String userId;
    private long weekStartDate;
    private Map<String, List<String>> days;

    public WeeklyMenu() {
        days = new HashMap<>();
        for (String day : DAY_KEYS) {
            days.put(day, new ArrayList<>());
        }
    }

    public WeeklyMenu(String userId) {
        this.userId = userId;
        this.weekStartDate = System.currentTimeMillis();
        days = new HashMap<>();
        for (String day : DAY_KEYS) {
            days.put(day, new ArrayList<>());
        }
    }

    @SuppressWarnings("unchecked")
    public static WeeklyMenu fromDocument(DocumentSnapshot doc) {
        WeeklyMenu menu = new WeeklyMenu();
        menu.setUserId(doc.getString("userId"));

        Object weekStartVal = doc.get("weekStartDate");
        if (weekStartVal instanceof Number) {
            menu.setWeekStartDate(((Number) weekStartVal).longValue());
        }

        Object daysVal = doc.get("days");
        if (daysVal instanceof Map<?, ?>) {
            Map<?, ?> rawDays = (Map<?, ?>) daysVal;
            Map<String, List<String>> parsedDays = new HashMap<>();
            for (String dayKey : DAY_KEYS) {
                Object dayList = rawDays.get(dayKey);
                List<String> recipeIds = new ArrayList<>();
                if (dayList instanceof List<?>) {
                    for (Object item : (List<?>) dayList) {
                        if (item != null) {
                            recipeIds.add(String.valueOf(item));
                        }
                    }
                }
                parsedDays.put(dayKey, recipeIds);
            }
            menu.setDays(parsedDays);
        }

        return menu;
    }

    /**
     * Collect all unique recipe IDs across all days.
     */
    @Exclude
    public List<String> getAllRecipeIds() {
        List<String> all = new ArrayList<>();
        if (days != null) {
            for (List<String> dayRecipes : days.values()) {
                if (dayRecipes != null) {
                    for (String id : dayRecipes) {
                        if (!all.contains(id)) {
                            all.add(id);
                        }
                    }
                }
            }
        }
        return all;
    }

    @Exclude
    public List<String> getAllRecipeIdsWithDuplicates() {
        List<String> all = new ArrayList<>();
        if (days != null) {
            for (List<String> dayRecipes : days.values()) {
                if (dayRecipes != null) {
                    all.addAll(dayRecipes);
                }
            }
        }
        return all;
    }

    public List<String> getRecipeIdsForDay(String dayKey) {
        if (days != null && days.containsKey(dayKey)) {
            return days.get(dayKey);
        }
        return new ArrayList<>();
    }

    // ========================== Getters & Setters ==========================

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public long getWeekStartDate() { return weekStartDate; }
    public void setWeekStartDate(long weekStartDate) { this.weekStartDate = weekStartDate; }

    public Map<String, List<String>> getDays() { return days == null ? new HashMap<>() : days; }
    public void setDays(Map<String, List<String>> days) { this.days = days != null ? days : new HashMap<>(); }
}
