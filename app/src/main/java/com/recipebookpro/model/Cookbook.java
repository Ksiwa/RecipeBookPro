package com.recipebookpro.model;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.Exclude;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Cookbook implements Serializable {

    @Exclude
    private String id;
    private String userId;
    private String name;
    private List<String> recipeIds;
    private long createdAt;

    public Cookbook() {
        recipeIds = new ArrayList<>();
    }

    public Cookbook(String userId, String name) {
        this.userId = userId;
        this.name = name;
        this.recipeIds = new ArrayList<>();
        this.createdAt = System.currentTimeMillis();
    }

    public static Cookbook fromDocument(DocumentSnapshot doc) {
        Cookbook book = doc.toObject(Cookbook.class);
        if (book != null) {
            book.setId(doc.getId());
            if (book.getRecipeIds() == null) {
                book.setRecipeIds(new ArrayList<>());
            }
        }
        return book;
    }

    @Exclude
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getRecipeIds() {
        return recipeIds;
    }

    public void setRecipeIds(List<String> recipeIds) {
        this.recipeIds = recipeIds;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
