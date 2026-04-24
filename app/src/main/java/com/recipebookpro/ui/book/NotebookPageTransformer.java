package com.recipebookpro.ui.book;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.viewpager2.widget.ViewPager2;

public class NotebookPageTransformer implements ViewPager2.PageTransformer {

    @Override
    public void transformPage(@NonNull View page, float position) {
        page.setCameraDistance(30000);
        int pageWidth = page.getWidth();

        if (position < -1) { 
            page.setAlpha(0f);
        } else if (position <= 0) { // Sola doğru çevrilen mevcut sayfa
            page.setAlpha(1f);
            
            page.setTranslationX(pageWidth * -position);
            
            // Sol kenar (cilt) pivot
            page.setPivotX(0); 
            page.setPivotY(page.getHeight() / 2f);
            
            // 0 ile -180 derece arasında döndür
            page.setRotationY(180 * position); 
            
            // Kağıt kıvrılma illüzyonu için X ve Y ekseninde küçültme (Foreshortening)
            // Bu, yaprağın sadece düz bir tahta gibi dönmesini engeller, kıvrılarak büküldüğü hissini verir.
            float scaleFactor = 1f - Math.abs(position) * 0.1f;
            page.setScaleX(scaleFactor);
            page.setScaleY(1f - Math.abs(position) * 0.02f);
            
            page.setTranslationZ(1f); 
            
        } else if (position <= 1) { // Alttan gelen sonraki sayfa
            page.setAlpha(1f);
            
            page.setTranslationX(pageWidth * -position); 
            page.setRotationY(0); // Alttaki sayfa düz durur
            page.setScaleX(1f);
            page.setScaleY(1f);
            
            page.setTranslationZ(-1f);
        } else { 
            page.setAlpha(0f);
        }
    }
}
