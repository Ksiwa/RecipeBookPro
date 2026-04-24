package com.recipebookpro.ui.book;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.textview.MaterialTextView;
import com.recipebookpro.R;
import com.recipebookpro.model.Recipe;

public class RecipePageFragment extends Fragment {

    private static final String ARG_RECIPE = "recipe";
    private static final String ARG_PAGE_NO = "page_no";
    private static final String ARG_TOTAL = "total";

    public static RecipePageFragment newInstance(Recipe recipe, int pageNo, int totalPages) {
        RecipePageFragment fragment = new RecipePageFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_RECIPE, recipe);
        args.putInt(ARG_PAGE_NO, pageNo);
        args.putInt(ARG_TOTAL, totalPages);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_recipe_page, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = getArguments();
        if (args == null) {
            return;
        }

        Recipe recipe = (Recipe) args.getSerializable(ARG_RECIPE);
        if (recipe == null) {
            return;
        }

        MaterialTextView tvTitle = view.findViewById(R.id.tvPageTitle);
        MaterialTextView tvCategory = view.findViewById(R.id.tvPageCategory);
        MaterialTextView tvDescLabel = view.findViewById(R.id.tvPageDescLabel);
        MaterialTextView tvDesc = view.findViewById(R.id.tvPageDesc);
        MaterialTextView tvIngredients = view.findViewById(R.id.tvPageIngredients);
        MaterialTextView tvSteps = view.findViewById(R.id.tvPageSteps);
        MaterialTextView tvPageNum = view.findViewById(R.id.tvPageNum);

        tvTitle.setText(recipe.getTitle());
        tvCategory.setText(recipe.getCategory().isEmpty()
                ? getString(R.string.category_unknown)
                : recipe.getCategory());

        if (TextUtils.isEmpty(recipe.getDescription())) {
            tvDescLabel.setVisibility(View.GONE);
            tvDesc.setVisibility(View.GONE);
        } else {
            tvDesc.setText(recipe.getDescription());
        }

        tvIngredients.setText(recipe.getFormattedIngredients().isEmpty()
                ? getString(R.string.no_ingredients)
                : formatBullets(recipe.getFormattedIngredients()));
        tvSteps.setText(recipe.getSteps().isEmpty()
                ? getString(R.string.no_steps)
                : formatSteps(recipe.getSteps()));
        tvPageNum.setText(getString(R.string.page_number, args.getInt(ARG_PAGE_NO), args.getInt(ARG_TOTAL)));
    }

    private String formatBullets(String text) {
        StringBuilder builder = new StringBuilder();
        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            String cleaned = line.trim();
            if (cleaned.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append("• ").append(cleaned);
        }
        return builder.toString();
    }

    private String formatSteps(String text) {
        StringBuilder builder = new StringBuilder();
        String[] lines = text.split("\\r?\\n");
        int step = 1;
        for (String line : lines) {
            String cleaned = line.trim();
            if (cleaned.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append(step).append(". ")
                    .append(cleaned.replaceFirst("^[\\-•*\\d.)\\s]+", "").trim());
            step++;
        }
        return builder.length() == 0 ? text : builder.toString();
    }
}
