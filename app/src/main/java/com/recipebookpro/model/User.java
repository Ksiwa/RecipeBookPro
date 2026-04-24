package com.recipebookpro.model;

import java.util.ArrayList;
import java.util.List;

public class User {
    private String uid;
    private String email;
    private String displayName;
    private long createdAt;

    // --- New fields ---
    private List<String> allergens;       // user allergens for warnings
    private String profileImageUrl;
    private String role;                  // "user" | "admin"

    public User() {
        // Firestore requires an empty constructor
        allergens = new ArrayList<>();
    }

    public User(String uid, String email, String displayName, long createdAt) {
        this.uid = uid;
        this.email = email;
        this.displayName = displayName;
        this.createdAt = createdAt;
        this.allergens = new ArrayList<>();
        this.role = "user";
    }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public List<String> getAllergens() {
        return allergens == null ? new ArrayList<>() : allergens;
    }
    public void setAllergens(List<String> allergens) {
        this.allergens = allergens != null ? allergens : new ArrayList<>();
    }

    public String getProfileImageUrl() {
        return profileImageUrl == null ? "" : profileImageUrl;
    }
    public void setProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public String getRole() {
        return role == null ? "user" : role;
    }
    public void setRole(String role) {
        this.role = role;
    }

    public boolean isAdmin() {
        return "admin".equalsIgnoreCase(role);
    }
}
