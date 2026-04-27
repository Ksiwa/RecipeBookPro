package com.recipebookpro.ui.recipe;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.firestore.DocumentReference;
import com.recipebookpro.R;
import com.recipebookpro.model.Cookbook;
import com.recipebookpro.model.Recipe;
import com.recipebookpro.ui.kitchen.CookbookAddEditActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CookbookPickerBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_RECIPE = "recipe";
    private Recipe recipe;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;

    private LinearLayout containerCookbooks;
    private ProgressBar progressPicker;

    public static CookbookPickerBottomSheet newInstance(Recipe recipe) {
        CookbookPickerBottomSheet fragment = new CookbookPickerBottomSheet();
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
        View view = inflater.inflate(R.layout.bottom_sheet_cookbook_picker, container, false);

        containerCookbooks = view.findViewById(R.id.containerCookbooks);
        progressPicker = view.findViewById(R.id.progressPicker);

        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        loadCookbooks();

        return view;
    }

    private void loadCookbooks() {
        if (currentUser == null) return;
        progressPicker.setVisibility(View.VISIBLE);

        List<Cookbook> allBooks = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        final int[] pending = {2};

        db.collection("cookbooks")
          .whereEqualTo("userId", currentUser.getUid())
          .get()
          .addOnSuccessListener(snap -> {
              for (QueryDocumentSnapshot doc : snap) {
                  Cookbook book = Cookbook.fromDocument(doc);
                  if (seenIds.add(book.getId())) allBooks.add(book);
              }
              pending[0]--;
              if (pending[0] == 0) displayCookbooks(allBooks);
          })
          .addOnFailureListener(e -> {
              pending[0]--;
              if (pending[0] == 0) displayCookbooks(allBooks);
          });

        db.collection("cookbooks")
          .whereArrayContains("collaboratorIds", currentUser.getUid())
          .get()
          .addOnSuccessListener(snap -> {
              for (QueryDocumentSnapshot doc : snap) {
                  Cookbook book = Cookbook.fromDocument(doc);
                  if (seenIds.add(book.getId())) allBooks.add(book);
              }
              pending[0]--;
              if (pending[0] == 0) displayCookbooks(allBooks);
          })
          .addOnFailureListener(e -> {
              pending[0]--;
              if (pending[0] == 0) displayCookbooks(allBooks);
          });
    }

    private void displayCookbooks(List<Cookbook> books) {
        if (!isAdded()) return;
        progressPicker.setVisibility(View.GONE);
        containerCookbooks.removeAllViews();

        java.util.Collections.sort(books, (b1, b2) -> Long.compare(b2.getCreatedAt(), b1.getCreatedAt()));

        for (Cookbook book : books) {
            addCookbookButton(book);
        }

        MaterialButton btnNew = new MaterialButton(requireContext(), null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = books.isEmpty() ? 0 : 24;
        btnNew.setLayoutParams(params);
        btnNew.setText("Yeni Defter Oluştur");
        btnNew.setIcon(ContextCompat.getDrawable(requireContext(), R.drawable.ic_add));
        btnNew.setOnClickListener(v -> {
            startActivity(new Intent(getContext(), CookbookAddEditActivity.class));
            dismiss();
        });
        containerCookbooks.addView(btnNew);
    }

    private void addCookbookButton(Cookbook book) {
        MaterialButton button = new MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = 16;
        button.setLayoutParams(params);

        boolean isCollab = currentUser != null && !currentUser.getUid().equals(book.getUserId());
        String label = isCollab ? book.getName() + " (Ortak)" : book.getName();
        button.setText(label);
        button.setIcon(ContextCompat.getDrawable(requireContext(), R.drawable.ic_book));

        button.setOnClickListener(v -> saveToCookbook(book));

        containerCookbooks.addView(button);
    }

    private boolean isOwnRecipe() {
        return currentUser != null && recipe != null &&
               currentUser.getUid().equals(recipe.getUserId());
    }

    private void saveToCookbook(Cookbook book) {
        if (recipe == null || currentUser == null) return;

        if (isOwnRecipe()) {
            db.collection("cookbooks").document(book.getId())
              .update("recipeIds", FieldValue.arrayUnion(recipe.getId()))
              .addOnSuccessListener(aVoid -> showSuccessAndDismiss(book.getName()))
              .addOnFailureListener(e ->
                  Snackbar.make(requireView(), "Hata oluştu", Snackbar.LENGTH_SHORT).show());
        } else {
            copyRecipeToCookbook(book);
        }
    }

    private void copyRecipeToCookbook(Cookbook book) {
        WriteBatch batch = db.batch();

        DocumentReference newRecipeRef = db.collection("recipes").document();

        Map<String, Object> recipeData = new HashMap<>();
        recipeData.put("title", recipe.getTitle());
        recipeData.put("description", recipe.getDescription());
        recipeData.put("category", recipe.getCategory());
        recipeData.put("ingredients", recipe.getIngredients());
        recipeData.put("stepList", recipe.getStepList());
        recipeData.put("steps", recipe.getSteps());
        recipeData.put("imageUrl", recipe.getImageUrl());
        recipeData.put("servings", recipe.getServings());
        recipeData.put("allergens", recipe.getAllergens());
        recipeData.put("ingredientNames", recipe.getIngredientNames());
        recipeData.put("userId", currentUser.getUid());
        recipeData.put("createdAt", System.currentTimeMillis());
        recipeData.put("isPublic", false);
        recipeData.put("likes", 0);
        recipeData.put("sourceRecipeId", recipe.getId());

        batch.set(newRecipeRef, recipeData);

        DocumentReference cookbookRef = db.collection("cookbooks").document(book.getId());
        batch.update(cookbookRef, "recipeIds", FieldValue.arrayUnion(newRecipeRef.getId()));

        batch.commit()
             .addOnSuccessListener(aVoid -> showSuccessAndDismiss(book.getName()))
             .addOnFailureListener(e ->
                 Snackbar.make(requireView(), "Kopyalama hatası", Snackbar.LENGTH_SHORT).show());
    }

    private void showSuccessAndDismiss(String cookbookName) {
        if (getActivity() != null) {
            View rootView = getActivity().findViewById(android.R.id.content);
            if (rootView != null) {
                Snackbar.make(rootView, "Tarif \"" + cookbookName + "\" defterine eklendi", Snackbar.LENGTH_SHORT).show();
            }
        }
        dismiss();
    }
}
