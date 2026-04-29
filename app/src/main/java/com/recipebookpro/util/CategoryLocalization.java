package com.recipebookpro.util;

import android.content.Context;

import com.recipebookpro.R;

public final class CategoryLocalization {

    private CategoryLocalization() {
    }

    public static String getDisplayName(Context context, String categoryValue) {
        if (categoryValue == null || categoryValue.trim().isEmpty()) {
            return context.getString(R.string.category_unknown);
        }

        String[] values = context.getResources().getStringArray(R.array.recipe_category_values);
        String[] labels = context.getResources().getStringArray(R.array.recipe_category_labels);
        int count = Math.min(values.length, labels.length);

        for (int i = 0; i < count; i++) {
            if (categoryValue.equalsIgnoreCase(values[i])) {
                return labels[i];
            }
        }

        return categoryValue;
    }
    public static String getCategoryValue(Context context, String displayName) {
        if (displayName == null || displayName.trim().isEmpty()) {
            return "";
        }

        String[] values = context.getResources().getStringArray(R.array.recipe_category_values);
        String[] labels = context.getResources().getStringArray(R.array.recipe_category_labels);
        int count = Math.min(values.length, labels.length);

        for (int i = 0; i < count; i++) {
            if (displayName.equalsIgnoreCase(labels[i])) {
                return values[i];
            }
        }

        return displayName;
    }
}
