package com.recipebookpro.ui.recipe.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.recipebookpro.R;
import com.recipebookpro.model.Recipe;

import java.util.List;

public class IngredientAdapter extends RecyclerView.Adapter<IngredientAdapter.ViewHolder> {

    private final List<Recipe.Ingredient> ingredients;
    private final List<String> userAllergens;
    private double currentScaleRatio = 1.0;

    public IngredientAdapter(List<Recipe.Ingredient> ingredients, List<String> userAllergens) {
        this.ingredients = ingredients;
        this.userAllergens = userAllergens;
    }

    public void setScaleRatio(double ratio) {
        this.currentScaleRatio = ratio;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_ingredient_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Recipe.Ingredient ingredient = ingredients.get(position);
        
        // Use scaled display text if a numeric amount exists
        String amountUnit = "";
        if (ingredient.getNumericAmount() > 0) {
             amountUnit = ingredient.getScaledDisplayText(currentScaleRatio);
             // Since scaled display text includes the name, we just show that in the name field and hide amount if we want,
             // or we can separate them. ScaledDisplayText returns everything. Let's just put it in tvIngredientName.
             holder.tvIngredientName.setText(ingredient.getScaledDisplayText(currentScaleRatio));
             holder.tvIngredientAmountUnit.setVisibility(View.GONE);
        } else {
             holder.tvIngredientName.setText(ingredient.getName());
             String au = (ingredient.getAmount() + " " + ingredient.getUnit()).trim();
             if (!au.isEmpty()) {
                 holder.tvIngredientAmountUnit.setText(au);
                 holder.tvIngredientAmountUnit.setVisibility(View.VISIBLE);
             } else {
                 holder.tvIngredientAmountUnit.setVisibility(View.GONE);
             }
        }

        // Allergy check
        boolean hasAllergy = false;
        if (userAllergens != null && !userAllergens.isEmpty()) {
            String ingNameLower = ingredient.getName().toLowerCase();
            for (String allergen : userAllergens) {
                if (ingNameLower.contains(allergen.toLowerCase())) {
                    hasAllergy = true;
                    break;
                }
            }
        }
        
        holder.allergyIndicator.setVisibility(hasAllergy ? View.VISIBLE : View.GONE);
    }

    @Override
    public int getItemCount() {
        return ingredients == null ? 0 : ingredients.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvIngredientAmountUnit;
        TextView tvIngredientName;
        View allergyIndicator;

        ViewHolder(View itemView) {
            super(itemView);
            tvIngredientAmountUnit = itemView.findViewById(R.id.tvIngredientAmountUnit);
            tvIngredientName = itemView.findViewById(R.id.tvIngredientName);
            allergyIndicator = itemView.findViewById(R.id.allergyIndicator);
        }
    }
}
