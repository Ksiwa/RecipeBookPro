package com.recipebookpro.presentation.ui.recipe;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.recipebookpro.R;
import com.recipebookpro.domain.model.Recipe;
import com.recipebookpro.presentation.ui.recipe.adapter.IngredientAdapter;

import java.util.ArrayList;

public class IngredientsTabFragment extends Fragment {

    private static final String ARG_RECIPE = "recipe";
    private static final String ARG_ALLERGENS = "allergens";

    private Recipe recipe;
    private ArrayList<String> userAllergens;
    private int currentServings;
    
    private TextView tvServingsCount;
    private IngredientAdapter adapter;

    public static IngredientsTabFragment newInstance(Recipe recipe, ArrayList<String> userAllergens) {
        IngredientsTabFragment fragment = new IngredientsTabFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_RECIPE, recipe);
        args.putStringArrayList(ARG_ALLERGENS, userAllergens);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            recipe = (Recipe) getArguments().getSerializable(ARG_RECIPE);
            userAllergens = getArguments().getStringArrayList(ARG_ALLERGENS);
            currentServings = recipe.getServings();
            if (currentServings <= 0) currentServings = 1;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_tab_ingredients, container, false);
        
        RecyclerView rvIngredients = view.findViewById(R.id.rvIngredients);
        tvServingsCount = view.findViewById(R.id.tvServingsCount);
        ImageButton btnMinus = view.findViewById(R.id.btnMinus);
        ImageButton btnPlus = view.findViewById(R.id.btnPlus);
        com.google.android.material.chip.Chip chipCalories = view.findViewById(R.id.chipCalories);

        if (recipe.getCalories() > 0) {
            chipCalories.setVisibility(View.VISIBLE);
            chipCalories.setText(recipe.getCalories() + " " + getString(R.string.kcal_unit));
        }
        
        rvIngredients.setLayoutManager(new LinearLayoutManager(getContext()));
        
        adapter = new IngredientAdapter(recipe.getIngredients(), new java.util.ArrayList<>());
        rvIngredients.setAdapter(adapter);
        
        com.recipebookpro.presentation.ui.recipe.RecipeDetailViewModel viewModel = 
            new ViewModelProvider(requireActivity()).get(com.recipebookpro.presentation.ui.recipe.RecipeDetailViewModel.class);
        viewModel.getRiskyMatchTerms().observe(getViewLifecycleOwner(), terms -> {
            if (adapter != null && terms != null) {
                adapter.setRiskyMatchTerms(terms);
            }
        });

        view.findViewById(R.id.chipNutrition).setOnClickListener(v -> {
            String ingredientsText = recipe.getFormattedIngredients();
            NutritionBottomSheet bottomSheet = NutritionBottomSheet.newInstance(ingredientsText);
            bottomSheet.show(getParentFragmentManager(), "NutritionBottomSheet");
        });
        
        view.findViewById(R.id.chipConverter).setOnClickListener(v -> {
            UnitConverterBottomSheet bottomSheet = new UnitConverterBottomSheet();
            bottomSheet.show(getParentFragmentManager(), "UnitConverterBottomSheet");
        });
        
        updateServingsUI();
        
        btnMinus.setOnClickListener(v -> {
            if (currentServings > 1) {
                currentServings--;
                updateServingsUI();
            }
        });
        
        btnPlus.setOnClickListener(v -> {
            currentServings++;
            updateServingsUI();
        });
        
        return view;
    }
    
    private void updateServingsUI() {
        tvServingsCount.setText(getString(R.string.servings_format, currentServings));
        double ratio = (double) currentServings / recipe.getServings();
        adapter.setScaleRatio(ratio);
    }

    public void refreshIngredientsHighlight() {
        // Obsolete, handled by ViewModel observer now
    }
}
