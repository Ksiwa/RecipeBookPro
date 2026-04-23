package com.recipebookpro.model;

import com.google.firebase.firestore.DocumentSnapshot;

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
    private String steps;
    private String userId;
    private long createdAt;

    public Recipe() {
        ingredients = new ArrayList<>();
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
    }

    public static Recipe fromDocument(DocumentSnapshot document) {
        Recipe recipe = new Recipe();
        recipe.setId(document.getId());
        recipe.setTitle(asString(document.get("title")));
        recipe.setDescription(asString(document.get("description")));
        recipe.setCategory(asString(document.get("category")));
        recipe.setSteps(asString(document.get("steps")));
        recipe.setUserId(asString(document.get("userId")));

        Object createdAtValue = document.get("createdAt");
        if (createdAtValue instanceof Number) {
            recipe.setCreatedAt(((Number) createdAtValue).longValue());
        }

        Object rawIngredients = document.get("ingredients");
        recipe.setIngredients(parseIngredients(rawIngredients));
        return recipe;
    }

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
                        list.add(new Ingredient(name, amount, unit));
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

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

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

    public static class Ingredient implements Serializable {
        private String name;
        private String amount;
        private String unit;

        public Ingredient() {
        }

        public Ingredient(String name, String amount, String unit) {
            this.name = name;
            this.amount = amount;
            this.unit = unit;
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

        public String getDisplayText() {
            StringBuilder builder = new StringBuilder();
            if (!getAmount().trim().isEmpty()) {
                builder.append(getAmount().trim());
            }
            if (!getUnit().trim().isEmpty()) {
                if (builder.length() > 0) {
                    builder.append(" ");
                }
                builder.append(getUnit().trim());
            }
            if (!getName().trim().isEmpty()) {
                if (builder.length() > 0) {
                    builder.append(" ");
                }
                builder.append(getName().trim());
            }
            return builder.toString().trim();
        }
    }
}
