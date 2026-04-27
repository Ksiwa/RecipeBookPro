package com.recipebookpro.ui.planner.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textview.MaterialTextView;
import com.recipebookpro.R;
import com.recipebookpro.model.Recipe;
import com.recipebookpro.model.WeeklyMenu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DayCardAdapter extends RecyclerView.Adapter<DayCardAdapter.DayViewHolder> {

    public interface OnDayInteractionListener {
        void onAddRecipeClick(String dayKey, int dayIndex);
        void onRecipeLongPress(String dayKey, Recipe recipe);
        void onRecipeClick(Recipe recipe);
    }

    private final Map<String, List<Recipe>> dayRecipesMap = new HashMap<>();
    private final OnDayInteractionListener listener;

    public DayCardAdapter(OnDayInteractionListener listener) {
        this.listener = listener;
        for (String key : WeeklyMenu.DAY_KEYS) {
            dayRecipesMap.put(key, new ArrayList<>());
        }
    }

    public void setDayRecipes(String dayKey, List<Recipe> recipes) {
        dayRecipesMap.put(dayKey, recipes != null ? recipes : new ArrayList<>());
        int index = dayIndex(dayKey);
        if (index >= 0) notifyItemChanged(index);
    }

    public void setAllDayRecipes(Map<String, List<Recipe>> allData) {
        for (String key : WeeklyMenu.DAY_KEYS) {
            dayRecipesMap.put(key, allData.containsKey(key) ? allData.get(key) : new ArrayList<>());
        }
        notifyDataSetChanged();
    }

    private int dayIndex(String dayKey) {
        for (int i = 0; i < WeeklyMenu.DAY_KEYS.length; i++) {
            if (WeeklyMenu.DAY_KEYS[i].equals(dayKey)) return i;
        }
        return -1;
    }

    @NonNull
    @Override
    public DayViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_day_card, parent, false);
        return new DayViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DayViewHolder holder, int position) {
        String dayKey = WeeklyMenu.DAY_KEYS[position];
        String dayLabel = WeeklyMenu.DAY_LABELS_TR[position];
        List<Recipe> recipes = dayRecipesMap.get(dayKey);
        if (recipes == null) recipes = new ArrayList<>();

        holder.tvDayName.setText(dayLabel);
        holder.tvDayEmpty.setVisibility(recipes.isEmpty() ? View.VISIBLE : View.GONE);
        holder.rvDayRecipes.setVisibility(recipes.isEmpty() ? View.GONE : View.VISIBLE);

        DayRecipeAdapter recipeAdapter = new DayRecipeAdapter(recipes, recipe -> {
            if (listener != null) listener.onRecipeClick(recipe);
        });
        holder.rvDayRecipes.setLayoutManager(new LinearLayoutManager(holder.itemView.getContext()));
        holder.rvDayRecipes.setAdapter(recipeAdapter);

        recipeAdapter.setOnLongClickListener(recipe -> {
            if (listener != null) listener.onRecipeLongPress(dayKey, recipe);
        });

        holder.btnAddToDay.setOnClickListener(v -> {
            if (listener != null) listener.onAddRecipeClick(dayKey, position);
        });
    }

    @Override
    public int getItemCount() {
        return WeeklyMenu.DAY_KEYS.length;
    }

    static class DayViewHolder extends RecyclerView.ViewHolder {
        MaterialTextView tvDayName, tvDayEmpty;
        RecyclerView rvDayRecipes;
        View btnAddToDay;

        DayViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDayName = itemView.findViewById(R.id.tvDayName);
            tvDayEmpty = itemView.findViewById(R.id.tvDayEmpty);
            rvDayRecipes = itemView.findViewById(R.id.rvDayRecipes);
            btnAddToDay = itemView.findViewById(R.id.btnAddToDay);
        }
    }
}
