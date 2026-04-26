package com.recipebookpro.model;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.PropertyName;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Cookbook implements Serializable {

    @Exclude
    private String id;
    private String userId;
    private String name;
    private String description;
    private String coverImageUrl;
    private boolean isPublic;
    private List<String> tags;
    private List<String> collaboratorIds;
    private List<String> followerIds;
    private int followerCount;
    private List<String> recipeIds;
    private Map<String, List<StickerModel>> recipeStickers;
    private long createdAt;

    public Cookbook() {
        recipeIds = new ArrayList<>();
        tags = new ArrayList<>();
        collaboratorIds = new ArrayList<>();
        followerIds = new ArrayList<>();
        recipeStickers = new HashMap<>();
    }

    public Cookbook(String userId, String name) {
        this.userId = userId;
        this.name = name;
        this.recipeIds = new ArrayList<>();
        this.tags = new ArrayList<>();
        this.collaboratorIds = new ArrayList<>();
        this.followerIds = new ArrayList<>();
        this.createdAt = System.currentTimeMillis();
    }

    @SuppressWarnings("unchecked")
    public static Cookbook fromDocument(DocumentSnapshot doc) {
        Cookbook book = doc.toObject(Cookbook.class);
        if (book != null) {
            book.setId(doc.getId());
            if (!book.isPublic()) {
                Boolean legacyPublic = doc.getBoolean("public");
                if (legacyPublic != null && legacyPublic) {
                    book.setPublic(true);
                }
            }
            if (book.getRecipeIds() == null) {
                book.setRecipeIds(new ArrayList<>());
            }
            if (book.getTags() == null) {
                book.setTags(new ArrayList<>());
            }
            if (book.getCollaboratorIds() == null) {
                book.setCollaboratorIds(new ArrayList<>());
            }
            if (book.getFollowerIds() == null) {
                book.setFollowerIds(new ArrayList<>());
            }
            if (book.getRecipeStickers() == null) {
                book.setRecipeStickers(new HashMap<>());
            }
            
            // Manual parsing for nested generic map/list
            Object rawStickers = doc.get("recipeStickers");
            if (rawStickers instanceof Map<?, ?>) {
                Map<String, List<StickerModel>> parsedMap = new HashMap<>();
                Map<?, ?> rawMap = (Map<?, ?>) rawStickers;
                for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                    String recipeId = String.valueOf(entry.getKey());
                    parsedMap.put(recipeId, parseStickerList(entry.getValue()));
                }
                book.setRecipeStickers(parsedMap);
            }
        }
        return book;
    }

    private static List<StickerModel> parseStickerList(Object raw) {
        List<StickerModel> list = new ArrayList<>();
        if (raw instanceof List<?>) {
            for (Object item : (List<?>) raw) {
                if (item instanceof Map<?, ?>) {
                    Map<?, ?> map = (Map<?, ?>) item;
                    StickerModel sticker = new StickerModel();
                    sticker.setImageUrl(asString(map.get("imageUrl")));
                    sticker.setX(asFloat(map.get("x")));
                    sticker.setY(asFloat(map.get("y")));
                    sticker.setRotation(asFloat(map.get("rotation")));
                    sticker.setScale(asFloat(map.get("scale")));
                    list.add(sticker);
                }
            }
        }
        return list;
    }

    private static float asFloat(Object value) {
        if (value instanceof Number) return ((Number) value).floatValue();
        return 0f;
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
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

    public String getDescription() {
        return description == null ? "" : description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCoverImageUrl() {
        return coverImageUrl == null ? "" : coverImageUrl;
    }

    public void setCoverImageUrl(String coverImageUrl) {
        this.coverImageUrl = coverImageUrl;
    }

    @PropertyName("isPublic")
    public boolean isPublic() {
        return isPublic;
    }

    @PropertyName("isPublic")
    public void setPublic(boolean isPublic) {
        this.isPublic = isPublic;
    }

    public List<String> getTags() {
        return tags == null ? new ArrayList<>() : tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags != null ? tags : new ArrayList<>();
    }

    public List<String> getCollaboratorIds() {
        return collaboratorIds == null ? new ArrayList<>() : collaboratorIds;
    }

    public void setCollaboratorIds(List<String> collaboratorIds) {
        this.collaboratorIds = collaboratorIds != null ? collaboratorIds : new ArrayList<>();
    }

    public List<String> getFollowerIds() {
        return followerIds == null ? new ArrayList<>() : followerIds;
    }

    public void setFollowerIds(List<String> followerIds) {
        this.followerIds = followerIds != null ? followerIds : new ArrayList<>();
    }

    public int getFollowerCount() {
        return followerCount;
    }

    public void setFollowerCount(int followerCount) {
        this.followerCount = followerCount;
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

    public Map<String, List<StickerModel>> getRecipeStickers() {
        return recipeStickers == null ? new HashMap<>() : recipeStickers;
    }

    public void setRecipeStickers(Map<String, List<StickerModel>> recipeStickers) {
        this.recipeStickers = recipeStickers;
    }
}
