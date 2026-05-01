package com.recipebookpro.presentation.ui.recipe;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.recipebookpro.R;
import com.recipebookpro.domain.model.Recipe;
import com.recipebookpro.presentation.ui.recipe.adapter.StepAdapter;

public class StepsTabFragment extends Fragment {

    private static final String ARG_RECIPE = "recipe";
    private Recipe recipe;

    public static StepsTabFragment newInstance(Recipe recipe) {
        StepsTabFragment fragment = new StepsTabFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_RECIPE, recipe);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            recipe = (Recipe) getArguments().getSerializable(ARG_RECIPE);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_tab_steps, container, false);
        
        RecyclerView rvSteps = view.findViewById(R.id.rvSteps);
        
        rvSteps.setLayoutManager(new LinearLayoutManager(getContext()));
        rvSteps.setAdapter(new StepAdapter(recipe.getStepList()));
        
        return view;
    }
}
