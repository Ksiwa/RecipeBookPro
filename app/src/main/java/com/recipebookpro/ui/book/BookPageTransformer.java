package com.recipebookpro.ui.book;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.viewpager2.widget.ViewPager2;

public class BookPageTransformer implements ViewPager2.PageTransformer {

    private static final float MIN_SCALE = 0.92f;
    private static final float MIN_ALPHA = 0.72f;
    private static final float MAX_ROTATION = 9f;

    @Override
    public void transformPage(@NonNull View page, float position) {
        int pageWidth = page.getWidth();
        page.setCameraDistance(pageWidth * 12f);

        if (position < -1f) {
            page.setAlpha(0f);
            page.setRotationY(MAX_ROTATION);
            return;
        }

        if (position <= 0f) {
            float absPosition = Math.abs(position);
            float scaleFactor = MIN_SCALE + (1f - MIN_SCALE) * (1f - absPosition);
            float alpha = MIN_ALPHA + (1f - MIN_ALPHA) * (1f - absPosition);

            page.setPivotX(pageWidth);
            page.setPivotY(page.getHeight() * 0.5f);
            page.setRotationY(MAX_ROTATION * position);
            page.setTranslationX(pageWidth * -position * 0.12f);
            page.setTranslationZ(24f * (1f - absPosition));
            page.setScaleX(scaleFactor);
            page.setScaleY(0.98f + (0.02f * (1f - absPosition)));
            page.setAlpha(alpha);
            return;
        }

        if (position <= 1f) {
            float scaleFactor = MIN_SCALE + (1f - MIN_SCALE) * (1f - position);
            float alpha = MIN_ALPHA + (1f - MIN_ALPHA) * (1f - position);

            page.setPivotX(0f);
            page.setPivotY(page.getHeight() * 0.5f);
            page.setRotationY(-MAX_ROTATION * position);
            page.setTranslationX(pageWidth * -position * 0.18f);
            page.setTranslationZ(12f * (1f - position));
            page.setScaleX(scaleFactor);
            page.setScaleY(0.98f + (0.02f * (1f - position)));
            page.setAlpha(alpha);
            return;
        }

        page.setAlpha(0f);
        page.setRotationY(-MAX_ROTATION);
    }
}
