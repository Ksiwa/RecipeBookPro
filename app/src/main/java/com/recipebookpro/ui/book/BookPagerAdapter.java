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
    private final String cookbookId;
    private final java.util.Map<String, java.util.List<String>> recipeToCookbookMap;

    public BookPagerAdapter(@NonNull FragmentActivity activity,
                            List<Recipe> recipes,
                            String userIdentity,
                            String cookbookId,
                            java.util.Map<String, java.util.List<String>> recipeToCookbookMap) {
        super(activity);
        this.recipes = recipes;
        this.userIdentity = userIdentity;
        this.cookbookId = cookbookId;
        this.recipeToCookbookMap = recipeToCookbookMap;
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
        Recipe recipe = recipes.get(position - 2);
        String contextCookbookId = cookbookId;
        java.util.ArrayList<String> allBookIds = null;
        
        if (contextCookbookId == null && recipeToCookbookMap != null) {
            java.util.List<String> books = recipeToCookbookMap.get(recipe.getId());
            if (books != null && !books.isEmpty()) {
                contextCookbookId = books.get(0); // Default to first
                allBookIds = new java.util.ArrayList<>(books);
            }
        }
        
        return RecipePageFragment.newInstance(
                recipe,
                contextCookbookId,
                allBookIds,
                position + 1,
                recipes.size() + 2
        );
    }

    @Override
    public int getItemCount() {
        return recipes.size() + 2;
    }
}
