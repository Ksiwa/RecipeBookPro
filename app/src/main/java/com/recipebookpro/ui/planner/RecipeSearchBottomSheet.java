package com.recipebookpro.ui.planner;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.recipebookpro.R;
import com.recipebookpro.model.Recipe;
import com.recipebookpro.ui.planner.adapter.DayRecipeAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RecipeSearchBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_DAY_KEY = "day_key";

    public interface OnRecipeSelectedListener {
        void onRecipeSelected(String dayKey, Recipe recipe);
    }

    private String dayKey;
    private OnRecipeSelectedListener listener;
    private final List<Recipe> allRecipes = new ArrayList<>();
    private final List<Recipe> filteredRecipes = new ArrayList<>();
    private DayRecipeAdapter adapter;

    public static RecipeSearchBottomSheet newInstance(String dayKey) {
        RecipeSearchBottomSheet sheet = new RecipeSearchBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_DAY_KEY, dayKey);
        sheet.setArguments(args);
        return sheet;
    }

    public void setOnRecipeSelectedListener(OnRecipeSelectedListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            dayKey = getArguments().getString(ARG_DAY_KEY);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_recipe_search, container, false);

        TextInputEditText etSearch = view.findViewById(R.id.etRecipeSearch);
        RecyclerView rvResults = view.findViewById(R.id.rvRecipeSearch);
        ProgressBar progress = view.findViewById(R.id.progressRecipeSearch);

        adapter = new DayRecipeAdapter(filteredRecipes, recipe -> {
            if (listener != null) {
                listener.onRecipeSelected(dayKey, recipe);
            }
            dismiss();
        });
        rvResults.setLayoutManager(new LinearLayoutManager(getContext()));
        rvResults.setAdapter(adapter);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterRecipes(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        loadUserRecipes(progress);

        return view;
    }

    private void loadUserRecipes(ProgressBar progress) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        progress.setVisibility(View.VISIBLE);
        FirebaseFirestore.getInstance().collection("recipes")
                .whereEqualTo("userId", user.getUid())
                .get()
                .addOnSuccessListener(snapshots -> {
                    progress.setVisibility(View.GONE);
                    allRecipes.clear();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        allRecipes.add(Recipe.fromDocument(doc));
                    }
                    Collections.sort(allRecipes, (a, b) -> a.getTitle().compareToIgnoreCase(b.getTitle()));
                    filterRecipes("");
                })
                .addOnFailureListener(e -> progress.setVisibility(View.GONE));
    }

    private void filterRecipes(String query) {
        filteredRecipes.clear();
        String q = query.toLowerCase().trim();
        for (Recipe r : allRecipes) {
            if (q.isEmpty() || r.getTitle().toLowerCase().contains(q)) {
                filteredRecipes.add(r);
            }
        }
        adapter.notifyDataSetChanged();
    }
}
