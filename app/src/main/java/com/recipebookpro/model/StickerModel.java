package com.recipebookpro.model;

import java.io.Serializable;

public class StickerModel implements Serializable {
    private String imageUrl;
    private float x;
    private float y;
    private float rotation;
    private float scale;

    public StickerModel() {
        // Firebase için gerekli boş yapıcı metot
    }

    public StickerModel(String imageUrl, float x, float y, float rotation, float scale) {
        this.imageUrl = imageUrl;
        this.x = x;
        this.y = y;
        this.rotation = rotation;
        this.scale = scale;
    }

    // Getter ve Setter metotları
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public float getX() { return x; }
    public void setX(float x) { this.x = x; }

    public float getY() { return y; }
    public void setY(float y) { this.y = y; }

    public float getRotation() { return rotation; }
    public void setRotation(float rotation) { this.rotation = rotation; }

    public float getScale() { return scale; }
    public void setScale(float scale) { this.scale = scale; }
}
