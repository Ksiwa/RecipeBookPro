package com.recipebookpro.model;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.Exclude;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class RecipeCollection implements Serializable {

    @Exclude
    private String id;
    private String userId;
    private String name;
    private String emoji;
    private List<String> recipeIds;
    private long createdAt;

    public RecipeCollection() {
        recipeIds = new ArrayList<>();
    }

    public RecipeCollection(String userId, String name, String emoji) {
        this.userId = userId;
        this.name = name;
        this.emoji = emoji;
        this.recipeIds = new ArrayList<>();
        this.createdAt = System.currentTimeMillis();
    }

    @SuppressWarnings("unchecked")
    public static RecipeCollection fromDocument(DocumentSnapshot doc) {
        RecipeCollection collection = new RecipeCollection();
        collection.setId(doc.getId());
        collection.setUserId(doc.getString("userId"));
        collection.setName(doc.getString("name"));
        collection.setEmoji(doc.getString("emoji"));

        Object createdAtVal = doc.get("createdAt");
        if (createdAtVal instanceof Number) {
            collection.setCreatedAt(((Number) createdAtVal).longValue());
        }

        Object rawIds = doc.get("recipeIds");
        if (rawIds instanceof List<?>) {
            List<String> ids = new ArrayList<>();
            for (Object item : (List<?>) rawIds) {
                if (item != null) {
                    ids.add(String.valueOf(item));
                }
            }
            collection.setRecipeIds(ids);
        }

        return collection;
    }

    // ========================== Getters & Setters ==========================

    @Exclude
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getName() { return name == null ? "" : name; }
    public void setName(String name) { this.name = name; }

    public String getEmoji() { return emoji == null ? "📁" : emoji; }
    public void setEmoji(String emoji) { this.emoji = emoji; }

    public List<String> getRecipeIds() { return recipeIds == null ? new ArrayList<>() : recipeIds; }
    public void setRecipeIds(List<String> recipeIds) { this.recipeIds = recipeIds != null ? recipeIds : new ArrayList<>(); }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
