package com.recipebookpro.presentation.ui.discover.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textview.MaterialTextView;
import com.recipebookpro.R;
import com.recipebookpro.domain.model.Recipe;
import com.recipebookpro.domain.model.User;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        void onAuthorClick(String userId);
        void onToggleFollowAuthor(String userId, boolean currentlyFollowing);
    }

    private final List<ScoredRecipe> items;
    private final OnDiscoverInteractionListener listener;
    private final Map<String, User> ownerMap = new HashMap<>();

    public DiscoverRecipeAdapter(List<ScoredRecipe> items, OnDiscoverInteractionListener listener) {
        this.items = items;
        this.listener = listener;
    }

    public void setOwnerMap(Map<String, User> owners) {
        ownerMap.clear();
        if (owners != null) {
            ownerMap.putAll(owners);
        }
        notifyDataSetChanged();
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
        String currentLang = com.recipebookpro.presentation.ui.LocaleHelper.getLanguage(holder.itemView.getContext());
        holder.tvTitle.setText(recipe.getDisplayTitle(currentLang));
        holder.tvMatch.setText(holder.itemView.getContext().getString(R.string.match_percent_display, scored.matchPercent));

        User owner = ownerMap.get(recipe.getUserId());
        String ownerName;
        if (owner != null) {
            ownerName = owner.getDisplayName();
            if (ownerName == null || ownerName.trim().isEmpty()) {
                ownerName = owner.getEmail();
            }
        } else {
            ownerName = holder.itemView.getContext().getString(R.string.recipe_owner_unknown);
        }
        holder.tvAuthor.setText(holder.itemView.getContext().getString(R.string.recipe_owner_label, ownerName));
        holder.tvLikes.setText(holder.itemView.getResources()
                .getQuantityString(R.plurals.likes_count, recipe.getLikes(), recipe.getLikes()));
        holder.tvAuthor.setOnClickListener(v -> {
            if (listener != null && recipe.getUserId() != null && !recipe.getUserId().isEmpty()) {
                listener.onAuthorClick(recipe.getUserId());
            }
        });

        boolean canFollow = recipe.getUserId() != null && !recipe.getUserId().isEmpty()
                && owner != null
                && !owner.getUid().isEmpty()
                && holder.currentUserId != null
                && !holder.currentUserId.equals(recipe.getUserId());
        if (canFollow) {
            boolean currentlyFollowing = owner.getFollowerIds().contains(holder.currentUserId);
            holder.btnFollowAuthor.setVisibility(View.VISIBLE);
            holder.btnFollowAuthor.setText(currentlyFollowing ? R.string.unfollow : R.string.follow);
            holder.btnFollowAuthor.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onToggleFollowAuthor(recipe.getUserId(), currentlyFollowing);
                }
            });
        } else {
            holder.btnFollowAuthor.setVisibility(View.GONE);
            holder.btnFollowAuthor.setOnClickListener(null);
        }

        // Determine data to load: URL or placeholder resource
        Object imageData = (recipe.getImageUrl() != null && !recipe.getImageUrl().isEmpty()) 
                ? recipe.getImageUrl() 
                : R.drawable.ic_cook;
        boolean isRealImage = imageData instanceof String;

        android.util.TypedValue typedValue = new android.util.TypedValue();
        android.content.Context context = holder.itemView.getContext();
        context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimaryContainer, typedValue, true);
        int bgColor = typedValue.data;
        context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnPrimaryContainer, typedValue, true);
        int tintColor = typedValue.data;

        ImageRequest request = new ImageRequest.Builder(context)
                .data(imageData)
                .target(new coil.target.Target() {
                    @Override
                    public void onStart(@Nullable android.graphics.drawable.Drawable placeholder) {}

                    @Override
                    public void onSuccess(@NonNull android.graphics.drawable.Drawable result) {
                        if (isRealImage) {
                            holder.ivImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
                            holder.ivImage.setBackground(null);
                            holder.ivImage.setImageTintList(null);
                            holder.ivImage.setPadding(0, 0, 0, 0);
                            holder.ivImage.setImageDrawable(result);
                        } else {
                            holder.ivImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
                            holder.ivImage.setBackgroundColor(bgColor);
                            holder.ivImage.setPadding(0, 0, 0, 0);
                            
                            android.graphics.drawable.Drawable tinted = result.mutate();
                            androidx.core.graphics.drawable.DrawableCompat.setTint(tinted, tintColor);
                            holder.ivImage.setImageDrawable(tinted);
                        }
                    }

                    @Override
                    public void onError(@Nullable android.graphics.drawable.Drawable error) {
                        // If real image fails, fallback to placeholder manually
                        holder.ivImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        holder.ivImage.setImageResource(R.drawable.ic_cook);
                        holder.ivImage.setBackgroundColor(bgColor);
                        holder.ivImage.setImageTintList(android.content.res.ColorStateList.valueOf(tintColor));
                    }
                })
                .crossfade(true)
                .build();
        Coil.imageLoader(context).enqueue(request);

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
        MaterialTextView tvTitle, tvAuthor, tvMatch, tvLikes;
        ChipGroup chipGroupMissing;
        MaterialButton btnAddToShopping, btnFollowAuthor;
        String currentUserId;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivImage = itemView.findViewById(R.id.ivDiscoverRecipeImage);
            tvTitle = itemView.findViewById(R.id.tvDiscoverRecipeTitle);
            tvAuthor = itemView.findViewById(R.id.tvDiscoverRecipeAuthor);
            tvLikes = itemView.findViewById(R.id.tvDiscoverRecipeLikes);
            tvMatch = itemView.findViewById(R.id.tvMatchPercentage);
            chipGroupMissing = itemView.findViewById(R.id.chipGroupMissing);
            btnAddToShopping = itemView.findViewById(R.id.btnAddToShopping);
            btnFollowAuthor = itemView.findViewById(R.id.btnFollowAuthor);
            com.google.firebase.auth.FirebaseUser currentUser =
                    com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            currentUserId = currentUser != null ? currentUser.getUid() : null;
        }
    }
}
