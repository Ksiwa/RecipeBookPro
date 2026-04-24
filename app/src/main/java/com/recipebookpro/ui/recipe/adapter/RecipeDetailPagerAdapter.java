package com.recipebookpro.ui.recipe.adapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.recipebookpro.model.Recipe;
import com.recipebookpro.ui.recipe.IngredientsTabFragment;
import com.recipebookpro.ui.recipe.NotesTabFragment;
import com.recipebookpro.ui.recipe.StepsTabFragment;

import java.util.ArrayList;

public class RecipeDetailPagerAdapter extends FragmentStateAdapter {

    private final Recipe recipe;
    private final ArrayList<String> userAllergens;

    public RecipeDetailPagerAdapter(@NonNull FragmentActivity fragmentActivity, Recipe recipe, ArrayList<String> userAllergens) {
        super(fragmentActivity);
        this.recipe = recipe;
        this.userAllergens = userAllergens;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:
                return IngredientsTabFragment.newInstance(recipe, userAllergens);
            case 1:
                return StepsTabFragment.newInstance(recipe);
            case 2:
                return NotesTabFragment.newInstance(recipe);
            default:
                return IngredientsTabFragment.newInstance(recipe, userAllergens);
        }
    }

    @Override
    public int getItemCount() {
        return 3;
    }
}
