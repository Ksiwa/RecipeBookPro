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

        fetchAndShowStickers(rv, "all");

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
            
            fetchAndShowStickers(rv, category);
        });
    }

    private void fetchAndShowStickers(RecyclerView rv, String category) {
        new Thread(() -> {
            List<String> allUrls = new ArrayList<>();
            String[] categories = {"dessert", "drink", "food", "ornamental", "tools_equipment", "vegetable"};
            
            for (String cat : categories) {
                if (!category.equals("all") && !category.equals(cat)) continue;
                
                try {
                    java.net.URL url = new java.net.URL("https://api.github.com/repos/zeyynepp0/PhotoshopExtension_Images/contents/" + cat);
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Accept", "application/json");
                    
                    if (conn.getResponseCode() == 200) {
                        java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) sb.append(line);
                        
                        org.json.JSONArray arr = new org.json.JSONArray(sb.toString());
                        for (int i = 0; i < arr.length(); i++) {
                            org.json.JSONObject obj = arr.getJSONObject(i);
                            String name = obj.getString("name");
                            if (name.endsWith(".png")) {
                                allUrls.add("https://raw.githubusercontent.com/zeyynepp0/PhotoshopExtension_Images/main/" + cat + "/" + name);
                            }
                        }
                    }
                    conn.disconnect();
                } catch (Exception e) {
                    // Fallback for this category if API fails
                    for (int id = 0; id <= 250; id++) {
                        allUrls.add("https://raw.githubusercontent.com/zeyynepp0/PhotoshopExtension_Images/main/" + cat + "/PhotoshopExtension_Image_" + id + ".png");
                        allUrls.add("https://raw.githubusercontent.com/zeyynepp0/PhotoshopExtension_Images/main/" + cat + "/PhotoshopExtension_Image (" + id + ").png");
                    }
                }
            }
            
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    StickerAdapter adapter = new StickerAdapter(allUrls, imageUrl -> {
                        if (listener != null) {
                            listener.onStickerSelected(imageUrl);
                        }
                        dismiss();
                    });
                    rv.setAdapter(adapter);
                });
            }
        }).start();
    }

}
