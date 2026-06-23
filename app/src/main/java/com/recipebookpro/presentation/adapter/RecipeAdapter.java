package com.recipebookpro.presentation.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import android.graphics.Color;
import android.widget.ImageView;
import com.google.android.material.chip.Chip;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.textview.MaterialTextView;
import com.recipebookpro.R;
import com.recipebookpro.domain.model.Recipe;
import com.recipebookpro.util.CategoryLocalization;

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
    private OnRecipeRemoveListener removeListener;

    public interface OnRecipeClickListener {
        void onRecipeClick(Recipe recipe);
    }

    public interface OnRecipeRemoveListener {
        void onRecipeRemove(Recipe recipe);
    }

    public RecipeAdapter(OnRecipeClickListener listener) {
        this.listener = listener;
    }

    public void setOnRecipeRemoveListener(OnRecipeRemoveListener removeListener) {
        this.removeListener = removeListener;
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
        private final View btnRemoveRecipe;

        RecipeViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvRecipeTitle);
            tvDescription = itemView.findViewById(R.id.tvRecipeDescription);
            tvDate = itemView.findViewById(R.id.tvRecipeDate);
            tvIngredientsPreview = itemView.findViewById(R.id.tvIngredientsPreview);
            chipCategory = itemView.findViewById(R.id.chipRecipeCategory);
            ivRecipeImage = itemView.findViewById(R.id.ivRecipeImage);
            btnRemoveRecipe = itemView.findViewById(R.id.btnRemoveRecipe);
        }

        void bind(Recipe recipe) {
            String currentLang = com.recipebookpro.presentation.ui.LocaleHelper.getLanguage(itemView.getContext());
            tvTitle.setText(recipe.getDisplayTitle(currentLang));

            // Determine data to load: URL or placeholder resource
            Object imageData = (recipe.getImageUrl() != null && !recipe.getImageUrl().isEmpty()) 
                    ? recipe.getImageUrl() 
                    : R.drawable.ic_cook;
            boolean isRealImage = imageData instanceof String;

            android.util.TypedValue typedValue = new android.util.TypedValue();
            android.content.Context context = itemView.getContext();
            context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimaryContainer, typedValue, true);
            int bgColor = typedValue.data;
            context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnPrimaryContainer, typedValue, true);
            int tintColor = typedValue.data;

            ivRecipeImage.setVisibility(View.VISIBLE);
            ImageRequest request = new ImageRequest.Builder(context)
                    .data(imageData)
                    .target(new coil.target.Target() {
                        @Override
                        public void onStart(@Nullable android.graphics.drawable.Drawable placeholder) {}

                        @Override
                        public void onSuccess(@NonNull android.graphics.drawable.Drawable result) {
                            if (isRealImage) {
                                ivRecipeImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
                                ivRecipeImage.setBackground(null);
                                ivRecipeImage.setImageTintList(null);
                                ivRecipeImage.setPadding(0, 0, 0, 0);
                                ivRecipeImage.setImageDrawable(result);
                            } else {
                                ivRecipeImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
                                ivRecipeImage.setBackgroundColor(bgColor);
                                ivRecipeImage.setPadding(0, 0, 0, 0);
                                
                                android.graphics.drawable.Drawable tinted = result.mutate();
                                androidx.core.graphics.drawable.DrawableCompat.setTint(tinted, tintColor);
                                ivRecipeImage.setImageDrawable(tinted);
                            }
                        }

                        @Override
                        public void onError(@Nullable android.graphics.drawable.Drawable error) {
                            ivRecipeImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
                            ivRecipeImage.setImageResource(R.drawable.ic_cook);
                            ivRecipeImage.setBackgroundColor(bgColor);
                            ivRecipeImage.setImageTintList(android.content.res.ColorStateList.valueOf(tintColor));
                        }
                    })
                    .crossfade(true)
                    .build();
            Coil.imageLoader(context).enqueue(request);

            if (recipe.getCategory() == null || recipe.getCategory().trim().isEmpty()) {
                chipCategory.setVisibility(View.GONE);
            } else {
                chipCategory.setVisibility(View.VISIBLE);
                chipCategory.setText(CategoryLocalization.getDisplayName(itemView.getContext(), recipe.getCategory()));
                applyCategoryColor(chipCategory, recipe.getCategory());
            }

            if (recipe.getCreatedAt() > 0) {
                SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
                tvDate.setText(sdf.format(new Date(recipe.getCreatedAt())));
            } else {
                tvDate.setText("");
            }

            if (removeListener != null) {
                btnRemoveRecipe.setVisibility(View.VISIBLE);
                btnRemoveRecipe.setOnClickListener(v -> removeListener.onRecipeRemove(recipe));
            } else {
                btnRemoveRecipe.setVisibility(View.GONE);
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onRecipeClick(recipe);
                }
            });

            itemView.setOnLongClickListener(v -> {
                if (removeListener != null) {
                    removeListener.onRecipeRemove(recipe);
                    return true;
                }
                return false;
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
            chip.setTextColor(getReadableTextColor(baseColor));
        }

        private int getReadableTextColor(int backgroundColor) {
            double whiteContrast = ColorUtils.calculateContrast(Color.WHITE, backgroundColor);
            double blackContrast = ColorUtils.calculateContrast(Color.BLACK, backgroundColor);
            return whiteContrast >= blackContrast ? Color.WHITE : Color.BLACK;
        }
    }
}
