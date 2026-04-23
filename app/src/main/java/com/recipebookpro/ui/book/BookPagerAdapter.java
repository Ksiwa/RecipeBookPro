package com.recipebookpro.ui.book;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.recipebookpro.model.Recipe;

import java.util.List;

public class BookPagerAdapter extends FragmentStateAdapter {

    private final List<Recipe> recipes;
    private final String userIdentity;

    public BookPagerAdapter(@NonNull FragmentActivity activity,
                            List<Recipe> recipes,
                            String userIdentity) {
        super(activity);
        this.recipes = recipes;
        this.userIdentity = userIdentity;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position == 0) {
            return CoverPageFragment.newInstance(userIdentity, recipes.size());
        }
        if (position == 1) {
            return TocPageFragment.newInstance(recipes);
        }
        return RecipePageFragment.newInstance(
                recipes.get(position - 2),
                position + 1,
                recipes.size() + 2
        );
    }

    @Override
    public int getItemCount() {
        return recipes.size() + 2;
    }
}
