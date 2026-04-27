package com.recipebookpro.ui.planner.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textview.MaterialTextView;
import com.recipebookpro.R;
import com.recipebookpro.model.Recipe;

import java.util.List;

public class DayRecipeAdapter extends RecyclerView.Adapter<DayRecipeAdapter.ViewHolder> {

    private final List<Recipe> recipes;
    private final OnRecipeClickListener clickListener;
    private OnRecipeLongClickListener longClickListener;

    public interface OnRecipeClickListener {
        void onRecipeClick(Recipe recipe);
    }

    public interface OnRecipeLongClickListener {
        void onRecipeLongClick(Recipe recipe);
    }

    public DayRecipeAdapter(List<Recipe> recipes, OnRecipeClickListener listener) {
        this.recipes = recipes;
        this.clickListener = listener;
    }

    public void setOnLongClickListener(OnRecipeLongClickListener listener) {
        this.longClickListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_day_recipe, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Recipe recipe = recipes.get(position);
        holder.tvRecipeName.setText(recipe.getTitle());

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onRecipeClick(recipe);
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onRecipeLongClick(recipe);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return recipes.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        MaterialTextView tvRecipeName;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvRecipeName = itemView.findViewById(R.id.tvDayRecipeName);
        }
    }
}
