package com.recipebookpro.model;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.Exclude;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ShoppingList implements Serializable {

    @Exclude
    private String id;
    private String userId;
    private String name;
    private long createdAt;
    private List<ShoppingItem> items;

    public ShoppingList() {
        items = new ArrayList<>();
    }

    public ShoppingList(String userId, String name) {
        this.userId = userId;
        this.name = name;
        this.createdAt = System.currentTimeMillis();
        this.items = new ArrayList<>();
    }

    public static ShoppingList fromDocument(DocumentSnapshot doc) {
        ShoppingList list = new ShoppingList();
        list.setId(doc.getId());
        list.setUserId(doc.getString("userId"));
        list.setName(doc.getString("name"));

        Object createdAtVal = doc.get("createdAt");
        if (createdAtVal instanceof Number) {
            list.setCreatedAt(((Number) createdAtVal).longValue());
        }

        Object rawItems = doc.get("items");
        if (rawItems instanceof List<?>) {
            List<ShoppingItem> parsed = new ArrayList<>();
            for (Object item : (List<?>) rawItems) {
                if (item instanceof Map<?, ?>) {
                    parsed.add(ShoppingItem.fromMap((Map<?, ?>) item));
                }
            }
            list.setItems(parsed);
        }

        return list;
    }

    // ========================== Getters & Setters ==========================

    @Exclude
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getName() { return name == null ? "" : name; }
    public void setName(String name) { this.name = name; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public List<ShoppingItem> getItems() { return items == null ? new ArrayList<>() : items; }
    public void setItems(List<ShoppingItem> items) { this.items = items != null ? items : new ArrayList<>(); }

    // ========================== Inner Class ==========================

    public static class ShoppingItem implements Serializable {

        public static final String STATUS_NEED_TO_BUY = "NEED_TO_BUY";
        public static final String STATUS_HAVE_IT = "HAVE_IT";

        private String name;
        private String amount;
        private String unit;
        private boolean checked;
        private String homeStatus;

        public ShoppingItem() {
            this.homeStatus = STATUS_NEED_TO_BUY;
        }

        public ShoppingItem(String name, String amount, String unit) {
            this.name = name;
            this.amount = amount;
            this.unit = unit;
            this.checked = false;
            this.homeStatus = STATUS_NEED_TO_BUY;
        }

        public static ShoppingItem fromMap(Map<?, ?> map) {
            ShoppingItem item = new ShoppingItem();
            Object nameVal = map.get("name");
            item.setName(nameVal != null ? String.valueOf(nameVal).trim() : "");

            Object amountVal = map.get("amount");
            item.setAmount(amountVal != null ? String.valueOf(amountVal).trim() : "");

            Object unitVal = map.get("unit");
            item.setUnit(unitVal != null ? String.valueOf(unitVal).trim() : "");

            Object checkedVal = map.get("checked");
            if (checkedVal instanceof Boolean) {
                item.setChecked((Boolean) checkedVal);
            }

            Object statusVal = map.get("homeStatus");
            item.setHomeStatus(statusVal != null ? String.valueOf(statusVal) : STATUS_NEED_TO_BUY);

            return item;
        }

        public String getName() { return name == null ? "" : name; }
        public void setName(String name) { this.name = name; }

        public String getAmount() { return amount == null ? "" : amount; }
        public void setAmount(String amount) { this.amount = amount; }

        public String getUnit() { return unit == null ? "" : unit; }
        public void setUnit(String unit) { this.unit = unit; }

        public boolean isChecked() { return checked; }
        public void setChecked(boolean checked) { this.checked = checked; }

        public String getHomeStatus() { return homeStatus == null ? STATUS_NEED_TO_BUY : homeStatus; }
        public void setHomeStatus(String homeStatus) { this.homeStatus = homeStatus; }

        public String getDisplayText() {
            StringBuilder sb = new StringBuilder();
            if (!getAmount().isEmpty()) sb.append(getAmount());
            if (!getUnit().isEmpty()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(getUnit());
            }
            if (!getName().isEmpty()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(getName());
            }
            return sb.toString().trim();
        }
    }
}
