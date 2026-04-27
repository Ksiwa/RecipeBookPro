package com.recipebookpro.ui.discover.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textview.MaterialTextView;
import com.recipebookpro.R;
import com.recipebookpro.model.Recipe;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import coil.Coil;
import coil.request.ImageRequest;

public class DiscoverRecipeAdapter extends RecyclerView.Adapter<DiscoverRecipeAdapter.ViewHolder> {

    public static class ScoredRecipe {
        public final Recipe recipe;
        public final int matchPercent;
        public final List<String> missingIngredients;

        public ScoredRecipe(Recipe recipe, int matchPercent, List<String> missingIngredients) {
            this.recipe = recipe;
            this.matchPercent = matchPercent;
            this.missingIngredients = missingIngredients;
        }
    }

    public interface OnDiscoverInteractionListener {
        void onRecipeClick(Recipe recipe);
        void onAddMissingToShopping(Recipe recipe, List<String> missing);
    }

    private final List<ScoredRecipe> items;
    private final OnDiscoverInteractionListener listener;

    public DiscoverRecipeAdapter(List<ScoredRecipe> items, OnDiscoverInteractionListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_discover_recipe, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ScoredRecipe scored = items.get(position);
        Recipe recipe = scored.recipe;

        holder.tvTitle.setText(recipe.getTitle());
        holder.tvMatch.setText("%" + scored.matchPercent + " Uyumlu");

        String desc = recipe.getDescription();
        holder.tvAuthor.setText(desc.isEmpty() ? recipe.getCategory() : desc);

        if (!recipe.getImageUrl().isEmpty()) {
            holder.ivImage.setPadding(0, 0, 0, 0);
            holder.ivImage.setBackgroundColor(0);
            ImageRequest request = new ImageRequest.Builder(holder.itemView.getContext())
                    .data(recipe.getImageUrl())
                    .target(holder.ivImage)
                    .build();
            Coil.imageLoader(holder.itemView.getContext()).enqueue(request);
        }

        holder.chipGroupMissing.removeAllViews();
        if (scored.missingIngredients != null && !scored.missingIngredients.isEmpty()) {
            for (String missing : scored.missingIngredients) {
                Chip chip = new Chip(holder.itemView.getContext());
                chip.setText(missing);
                chip.setChipBackgroundColorResource(android.R.color.transparent);
                chip.setTextColor(holder.itemView.getContext()
                        .getColor(com.google.android.material.R.color.m3_ref_palette_error40));
                chip.setChipStrokeColorResource(com.google.android.material.R.color.m3_ref_palette_error40);
                chip.setChipStrokeWidth(1f);
                chip.setClickable(false);
                holder.chipGroupMissing.addView(chip);
            }
            holder.btnAddToShopping.setVisibility(View.VISIBLE);
        } else {
            holder.btnAddToShopping.setVisibility(View.GONE);
        }

        holder.btnAddToShopping.setOnClickListener(v -> {
            if (listener != null) listener.onAddMissingToShopping(recipe, scored.missingIngredients);
        });

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onRecipeClick(recipe);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivImage;
        MaterialTextView tvTitle, tvAuthor, tvMatch;
        ChipGroup chipGroupMissing;
        MaterialButton btnAddToShopping;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivImage = itemView.findViewById(R.id.ivDiscoverRecipeImage);
            tvTitle = itemView.findViewById(R.id.tvDiscoverRecipeTitle);
            tvAuthor = itemView.findViewById(R.id.tvDiscoverRecipeAuthor);
            tvMatch = itemView.findViewById(R.id.tvMatchPercentage);
            chipGroupMissing = itemView.findViewById(R.id.chipGroupMissing);
            btnAddToShopping = itemView.findViewById(R.id.btnAddToShopping);
        }
    }
}
