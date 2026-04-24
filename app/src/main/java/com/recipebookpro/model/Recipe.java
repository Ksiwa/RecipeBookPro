package com.recipebookpro.model;

import com.google.firebase.firestore.DocumentSnapshot;
import com.recipebookpro.util.FractionUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Recipe implements Serializable {

    private String id;
    private String title;
    private String description;
    private String category;
    private List<Ingredient> ingredients;
    private String steps;                   // legacy plain-text steps
    private List<Step> stepList;             // structured step list
    private String userId;
    private long createdAt;

    // --- New fields ---
    private String imageUrl;                // Firebase Storage or URI
    private int servings;                   // original serving count
    private boolean isPublic;               // public/private visibility
    private int likes;                      // like counter
    private List<String> allergens;         // allergen tags
    private List<String> ingredientNames;   // flat name list for search

    public Recipe() {
        ingredients = new ArrayList<>();
        stepList = new ArrayList<>();
        allergens = new ArrayList<>();
        ingredientNames = new ArrayList<>();
    }

    public Recipe(String id,
                  String title,
                  String description,
                  String category,
                  List<Ingredient> ingredients,
                  String steps,
                  String userId,
                  long createdAt) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.category = category;
        this.ingredients = ingredients != null ? ingredients : new ArrayList<>();
        this.steps = steps;
        this.userId = userId;
        this.createdAt = createdAt;
        this.stepList = new ArrayList<>();
        this.allergens = new ArrayList<>();
        this.ingredientNames = new ArrayList<>();
    }

    // ========================== Firestore Parsing ==========================

    public static Recipe fromDocument(DocumentSnapshot document) {
        Recipe recipe = new Recipe();
        recipe.setId(document.getId());
        recipe.setTitle(asString(document.get("title")));
        recipe.setDescription(asString(document.get("description")));
        recipe.setCategory(asString(document.get("category")));
        recipe.setUserId(asString(document.get("userId")));
        recipe.setImageUrl(asString(document.get("imageUrl")));

        Object createdAtValue = document.get("createdAt");
        if (createdAtValue instanceof Number) {
            recipe.setCreatedAt(((Number) createdAtValue).longValue());
        }

        Object servingsVal = document.get("servings");
        if (servingsVal instanceof Number) {
            recipe.setServings(((Number) servingsVal).intValue());
        } else {
            recipe.setServings(1);
        }

        Object publicVal = document.get("isPublic");
        if (publicVal instanceof Boolean) {
            recipe.setPublic((Boolean) publicVal);
        }

        Object likesVal = document.get("likes");
        if (likesVal instanceof Number) {
            recipe.setLikes(((Number) likesVal).intValue());
        }

        // Parse allergens
        recipe.setAllergens(parseStringList(document.get("allergens")));

        // Parse ingredientNames
        recipe.setIngredientNames(parseStringList(document.get("ingredientNames")));

        // Parse ingredients
        Object rawIngredients = document.get("ingredients");
        recipe.setIngredients(parseIngredients(rawIngredients));

        // Parse steps: try structured list first, fall back to plain string
        Object rawStepList = document.get("stepList");
        if (rawStepList instanceof List<?> && !((List<?>) rawStepList).isEmpty()) {
            recipe.setStepList(parseStepList(rawStepList));
            recipe.setSteps(stepsToString(recipe.getStepList()));
        } else {
            Object rawSteps = document.get("steps");
            String stepsStr = asString(rawSteps);
            recipe.setSteps(stepsStr);
            recipe.setStepList(parseStepsFromString(stepsStr));
        }

        return recipe;
    }

    // ========================== Parse Helpers ==========================

    private static List<Ingredient> parseIngredients(Object rawIngredients) {
        List<Ingredient> list = new ArrayList<>();
        if (rawIngredients instanceof List<?>) {
            for (Object item : (List<?>) rawIngredients) {
                if (item instanceof Map<?, ?>) {
                    Map<?, ?> map = (Map<?, ?>) item;
                    String name = asString(map.get("name"));
                    String amount = asString(map.get("amount"));
                    String unit = asString(map.get("unit"));
                    if (!name.isEmpty()) {
                        Ingredient ing = new Ingredient(name, amount, unit);
                        // Parse numericAmount from the amount string
                        ing.setNumericAmount(FractionUtils.parseAmount(amount));
                        list.add(ing);
                    }
                }
            }
        } else if (rawIngredients instanceof String) {
            String[] lines = String.valueOf(rawIngredients).split("\\r?\\n");
            for (String line : lines) {
                String cleaned = line.trim();
                if (!cleaned.isEmpty()) {
                    list.add(new Ingredient(cleaned, "", ""));
                }
            }
        }
        return list;
    }

    private static List<Step> parseStepList(Object rawSteps) {
        List<Step> list = new ArrayList<>();
        if (rawSteps instanceof List<?>) {
            int order = 1;
            for (Object item : (List<?>) rawSteps) {
                if (item instanceof Map<?, ?>) {
                    Step step = Step.fromMap((Map<?, ?>) item);
                    if (step.getOrder() == 0) {
                        step.setOrder(order);
                    }
                    list.add(step);
                    order++;
                }
            }
        }
        return list;
    }

    /**
     * Backward compatibility: parse a plain text steps string into Step objects.
     */
    private static List<Step> parseStepsFromString(String stepsStr) {
        List<Step> list = new ArrayList<>();
        if (stepsStr == null || stepsStr.trim().isEmpty()) {
            return list;
        }
        String[] lines = stepsStr.split("\\r?\\n");
        int order = 1;
        for (String line : lines) {
            String cleaned = line.trim();
            if (cleaned.isEmpty()) continue;
            // Strip leading numbering like "1. " or "- "
            cleaned = cleaned.replaceFirst("^[\\-•*\\d.)\\s]+", "").trim();
            if (!cleaned.isEmpty()) {
                list.add(new Step(order, cleaned, 0, ""));
                order++;
            }
        }
        return list;
    }

    private static String stepsToString(List<Step> stepList) {
        if (stepList == null || stepList.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Step step : stepList) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(step.getOrder()).append(". ").append(step.getDescription());
        }
        return sb.toString();
    }

    private static List<String> parseStringList(Object raw) {
        List<String> list = new ArrayList<>();
        if (raw instanceof List<?>) {
            for (Object item : (List<?>) raw) {
                if (item != null) {
                    String val = String.valueOf(item).trim();
                    if (!val.isEmpty()) {
                        list.add(val);
                    }
                }
            }
        }
        return list;
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    // ========================== Utility Methods ==========================

    public String getFormattedIngredients() {
        if (ingredients == null || ingredients.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Ingredient ingredient : ingredients) {
            if (ingredient == null || ingredient.getName().trim().isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append(ingredient.getDisplayText());
        }
        return builder.toString();
    }

    /**
     * Build the flat ingredientNames list from the ingredients list.
     * Call this before saving to Firestore to enable search.
     */
    public void buildIngredientNames() {
        ingredientNames = new ArrayList<>();
        if (ingredients != null) {
            for (Ingredient ing : ingredients) {
                String name = ing.getName().trim().toLowerCase();
                if (!name.isEmpty() && !ingredientNames.contains(name)) {
                    ingredientNames.add(name);
                }
            }
        }
    }

    // ========================== Getters & Setters ==========================

    public String getId() {
        return id == null ? "" : id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title == null ? "" : title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description == null ? "" : description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category == null ? "" : category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public List<Ingredient> getIngredients() {
        return ingredients == null ? new ArrayList<>() : ingredients;
    }

    public void setIngredients(List<Ingredient> ingredients) {
        this.ingredients = ingredients != null ? ingredients : new ArrayList<>();
    }

    public String getSteps() {
        return steps == null ? "" : steps;
    }

    public void setSteps(String steps) {
        this.steps = steps;
    }

    public List<Step> getStepList() {
        return stepList == null ? new ArrayList<>() : stepList;
    }

    public void setStepList(List<Step> stepList) {
        this.stepList = stepList != null ? stepList : new ArrayList<>();
    }

    public String getUserId() {
        return userId == null ? "" : userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public String getImageUrl() {
        return imageUrl == null ? "" : imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public int getServings() {
        return servings <= 0 ? 1 : servings;
    }

    public void setServings(int servings) {
        this.servings = servings;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public void setPublic(boolean isPublic) {
        this.isPublic = isPublic;
    }

    public int getLikes() {
        return likes;
    }

    public void setLikes(int likes) {
        this.likes = likes;
    }

    public List<String> getAllergens() {
        return allergens == null ? new ArrayList<>() : allergens;
    }

    public void setAllergens(List<String> allergens) {
        this.allergens = allergens != null ? allergens : new ArrayList<>();
    }

    public List<String> getIngredientNames() {
        return ingredientNames == null ? new ArrayList<>() : ingredientNames;
    }

    public void setIngredientNames(List<String> ingredientNames) {
        this.ingredientNames = ingredientNames != null ? ingredientNames : new ArrayList<>();
    }

    // ========================== Ingredient Inner Class ==========================

    public static class Ingredient implements Serializable {
        private String name;
        private String amount;
        private String unit;
        private double numericAmount;  // for scaling calculations

        public Ingredient() {
        }

        public Ingredient(String name, String amount, String unit) {
            this.name = name;
            this.amount = amount;
            this.unit = unit;
            this.numericAmount = FractionUtils.parseAmount(amount);
        }

        public String getName() {
            return name == null ? "" : name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getAmount() {
            return amount == null ? "" : amount;
        }

        public void setAmount(String amount) {
            this.amount = amount;
        }

        public String getUnit() {
            return unit == null ? "" : unit;
        }

        public void setUnit(String unit) {
            this.unit = unit;
        }

        public double getNumericAmount() {
            return numericAmount;
        }

        public void setNumericAmount(double numericAmount) {
            this.numericAmount = numericAmount;
        }

        /**
         * Get the scaled display text for a given serving ratio.
         */
        public String getScaledDisplayText(double ratio) {
            StringBuilder builder = new StringBuilder();
            if (numericAmount > 0) {
                double scaled = numericAmount * ratio;
                builder.append(FractionUtils.toFractionString(scaled));
            } else if (!getAmount().trim().isEmpty()) {
                builder.append(getAmount().trim());
            }
            if (!getUnit().trim().isEmpty()) {
                if (builder.length() > 0) builder.append(" ");
                builder.append(getUnit().trim());
            }
            if (!getName().trim().isEmpty()) {
                if (builder.length() > 0) builder.append(" ");
                builder.append(getName().trim());
            }
            return builder.toString().trim();
        }

        public String getDisplayText() {
            StringBuilder builder = new StringBuilder();
            if (!getAmount().trim().isEmpty()) {
                builder.append(getAmount().trim());
            }
            if (!getUnit().trim().isEmpty()) {
                if (builder.length() > 0) builder.append(" ");
                builder.append(getUnit().trim());
            }
            if (!getName().trim().isEmpty()) {
                if (builder.length() > 0) builder.append(" ");
                builder.append(getName().trim());
            }
            return builder.toString().trim();
        }
    }
}
