package com.recipebookpro.ui.book;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.viewpager2.widget.ViewPager2;

/**
 * Gelişmiş 3D Sayfa Çevirme (Book Flip) efekti.
 * Normal kaydırma yerine gerçek bir kitabın yaprakları gibi döner.
 */
public class CurlPageTransformer implements ViewPager2.PageTransformer {

    @Override
    public void transformPage(@NonNull View page, float position) {
        // Kamera uzaklığını artırarak perspektifi düzeltiyoruz
        page.setCameraDistance(20000);

        if (position < -1) { // [-Infinity, -1) Sayfa tamamen solda
            page.setAlpha(0f);
        } else if (position <= 0) { // [-1, 0] Mevcut sayfa (sola doğru gidiyor)
            page.setAlpha(1f);
            page.setPivotX(page.getWidth()); // Merkez sağ kenar
            page.setPivotY(page.getHeight() * 0.5f);
            page.setRotationY(-90 * Math.abs(position));
            
            // Sayfanın normal ViewPager kaymasını iptal edip olduğu yerde dönmesini sağlıyoruz
            page.setTranslationX(page.getWidth() * -position);
            
        } else if (position <= 1) { // (0, 1] Sonraki sayfa (sağdan geliyor)
            page.setAlpha(1f);
            page.setPivotX(0); // Merkez sol kenar
            page.setPivotY(page.getHeight() * 0.5f);
            page.setRotationY(90 * Math.abs(position));
            
            // Sayfanın normal ViewPager kaymasını iptal edip olduğu yerde dönmesini sağlıyoruz
            page.setTranslationX(page.getWidth() * -position);
            
        } else { // (1, +Infinity] Sayfa tamamen sağda
            page.setAlpha(0f);
        }
    }
}
