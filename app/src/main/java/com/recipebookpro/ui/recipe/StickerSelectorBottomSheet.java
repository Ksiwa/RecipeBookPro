package com.recipebookpro.ui.recipe;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.ChipGroup;
import com.recipebookpro.R;

import java.util.ArrayList;
import java.util.List;

public class StickerSelectorBottomSheet extends BottomSheetDialogFragment {

    public interface OnStickerSelectedListener {
        void onStickerSelected(String imageUrl);
    }

    private OnStickerSelectedListener listener;

    public void setOnStickerSelectedListener(OnStickerSelectedListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_sticker_selector, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RecyclerView rv = view.findViewById(R.id.rvStickers);
        ChipGroup chipGroup = view.findViewById(R.id.chipGroupCategories);
        
        rv.setLayoutManager(new GridLayoutManager(getContext(), 3));

        updateStickerList(rv, "all");

        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            String category = "all";
            if (id == R.id.chipDessert) category = "dessert";
            else if (id == R.id.chipDrink) category = "drink";
            else if (id == R.id.chipFood) category = "food";
            else if (id == R.id.chipOrnamental) category = "ornamental";
            else if (id == R.id.chipTools) category = "tools_equipment";
            else if (id == R.id.chipVegetable) category = "vegetable";
            
            updateStickerList(rv, category);
        });
    }

    private void updateStickerList(RecyclerView rv, String category) {
        List<String> stickerUrls = generateStickerUrls(category);
        StickerAdapter adapter = new StickerAdapter(stickerUrls, url -> {
            if (listener != null) {
                listener.onStickerSelected(url);
            }
            dismiss();
        });
        rv.setAdapter(adapter);
    }

    private List<String> generateStickerUrls(String filter) {
        List<String> urls = new ArrayList<>();
        String baseUrl = "https://raw.githubusercontent.com/zeyynepp0/PhotoshopExtension_Images/main/";
        
        String[] categories = {"dessert", "drink", "food", "ornamental", "tools_equipment", "vegetable"};
        
        for (String cat : categories) {
            if (!filter.equals("all") && !filter.equals(cat)) continue;
            
            for (int id = 0; id <= 250; id++) {
                urls.add(baseUrl + cat + "/PhotoshopExtension_Image_" + id + ".png");
            }
        }
        return urls;
    }
}
