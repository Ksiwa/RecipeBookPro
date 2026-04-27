package com.recipebookpro.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import android.widget.ImageView;
import com.google.android.material.chip.Chip;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.textview.MaterialTextView;
import com.recipebookpro.R;
import com.recipebookpro.model.Recipe;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import coil.Coil;
import coil.request.ImageRequest;

public class RecipeAdapter extends RecyclerView.Adapter<RecipeAdapter.RecipeViewHolder> {

    private final List<Recipe> recipeList = new ArrayList<>();
    private final OnRecipeClickListener listener;

    public interface OnRecipeClickListener {
        void onRecipeClick(Recipe recipe);
    }

    public RecipeAdapter(OnRecipeClickListener listener) {
        this.listener = listener;
    }

    public void setRecipeList(List<Recipe> list) {
        recipeList.clear();
        if (list != null) {
            Set<String> addedIds = new LinkedHashSet<>();
            for (Recipe recipe : list) {
                if (recipe == null) {
                    continue;
                }
                String recipeId = recipe.getId() != null ? recipe.getId() : "";
                if (!recipeId.isEmpty() && !addedIds.add(recipeId)) {
                    continue;
                }
                recipeList.add(recipe);
            }
        }
        notifyDataSetChanged();
    }

    public void clearRecipes() {
        recipeList.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RecipeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recipe, parent, false);
        return new RecipeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecipeViewHolder holder, int position) {
        holder.bind(recipeList.get(position));
    }

    @Override
    public int getItemCount() {
        return recipeList.size();
    }

    class RecipeViewHolder extends RecyclerView.ViewHolder {
        private final MaterialTextView tvTitle;
        private final MaterialTextView tvDescription;
        private final MaterialTextView tvDate;
        private final MaterialTextView tvIngredientsPreview;
        private final Chip chipCategory;
        private final ImageView ivRecipeImage;

        RecipeViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvRecipeTitle);
            tvDescription = itemView.findViewById(R.id.tvRecipeDescription);
            tvDate = itemView.findViewById(R.id.tvRecipeDate);
            tvIngredientsPreview = itemView.findViewById(R.id.tvIngredientsPreview);
            chipCategory = itemView.findViewById(R.id.chipRecipeCategory);
            ivRecipeImage = itemView.findViewById(R.id.ivRecipeImage);
        }

        void bind(Recipe recipe) {
            tvTitle.setText(recipe.getTitle());
            tvDescription.setText(recipe.getDescription());
            tvIngredientsPreview.setText(recipe.getFormattedIngredients());

            if (recipe.getImageUrl() != null && !recipe.getImageUrl().isEmpty()) {
                ivRecipeImage.setVisibility(View.VISIBLE);
                ImageRequest request = new ImageRequest.Builder(itemView.getContext())
                        .data(recipe.getImageUrl())
                        .target(ivRecipeImage)
                        .crossfade(true)
                        .placeholder(R.drawable.ic_nav_discover)
                        .error(R.drawable.ic_nav_discover)
                        .build();
                Coil.imageLoader(itemView.getContext()).enqueue(request);
            } else {
                ivRecipeImage.setVisibility(View.GONE);
            }

            if (recipe.getCategory() == null || recipe.getCategory().trim().isEmpty()) {
                chipCategory.setVisibility(View.GONE);
            } else {
                chipCategory.setVisibility(View.VISIBLE);
                chipCategory.setText(recipe.getCategory());
                applyCategoryColor(chipCategory, recipe.getCategory());
            }

            if (recipe.getCreatedAt() > 0) {
                SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
                tvDate.setText(sdf.format(new Date(recipe.getCreatedAt())));
            } else {
                tvDate.setText("");
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onRecipeClick(recipe);
                }
            });
        }

        private void applyCategoryColor(Chip chip, String category) {
            int baseColor;
            switch (category) {
                case "Tatli":
                    baseColor = ContextCompat.getColor(chip.getContext(), R.color.category_dessert);
                    break;
                case "Ana Yemek":
                    baseColor = ContextCompat.getColor(chip.getContext(), R.color.category_main_course);
                    break;
                case "Corba":
                    baseColor = ContextCompat.getColor(chip.getContext(), R.color.category_soup);
                    break;
                case "Kahvaltilik":
                    baseColor = ContextCompat.getColor(chip.getContext(), R.color.category_breakfast);
                    break;
                case "Atistirmalik":
                    baseColor = ContextCompat.getColor(chip.getContext(), R.color.category_snack);
                    break;
                case "Icecek":
                    baseColor = ContextCompat.getColor(chip.getContext(), R.color.category_drink);
                    break;
                case "Salata":
                    baseColor = ContextCompat.getColor(chip.getContext(), R.color.category_salad);
                    break;
                case "Hamur Isi":
                    baseColor = ContextCompat.getColor(chip.getContext(), R.color.category_pastry);
                    break;
                default:
                    baseColor = MaterialColors.getColor(chip, com.google.android.material.R.attr.colorSecondaryContainer);
                    break;
            }

            chip.setChipBackgroundColorResource(android.R.color.transparent);
            chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(baseColor));
            chip.setTextColor(MaterialColors.getColor(chip, com.google.android.material.R.attr.colorOnSecondaryContainer));
        }
    }
}
