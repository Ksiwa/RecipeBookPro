package com.recipebookpro.ui.book;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textview.MaterialTextView;
import com.recipebookpro.R;
import com.recipebookpro.model.Recipe;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class TocPageFragment extends Fragment {

    private static final String ARG_RECIPES = "recipes";

    public static TocPageFragment newInstance(List<Recipe> recipes) {
        TocPageFragment fragment = new TocPageFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_RECIPES, new ArrayList<>(recipes));
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_toc_page, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        List<Recipe> recipes = new ArrayList<>();
        Bundle args = getArguments();
        if (args != null) {
            Serializable serializable = args.getSerializable(ARG_RECIPES);
            if (serializable instanceof List) {
                //noinspection unchecked
                recipes = (List<Recipe>) serializable;
            }
        }

        MaterialTextView tvCount = view.findViewById(R.id.tvTocCount);
        RecyclerView rvTocPage = view.findViewById(R.id.rvTocPage);
        rvTocPage.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvTocPage.setNestedScrollingEnabled(false);
        tvCount.setText(getString(R.string.recipe_count_label, recipes.size()));

        TocItemAdapter adapter = new TocItemAdapter(recipes, position -> {
            if (getActivity() instanceof BookReaderActivity) {
                ((BookReaderActivity) getActivity()).goToRecipePage(position);
            }
        });
        rvTocPage.setAdapter(adapter);
    }
}
