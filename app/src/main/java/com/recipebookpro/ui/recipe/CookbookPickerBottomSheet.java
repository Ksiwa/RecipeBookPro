package com.recipebookpro.ui.recipe;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.recipebookpro.R;
import com.recipebookpro.model.Cookbook;
import com.recipebookpro.model.Recipe;

import java.util.List;

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

        db.collection("cookbooks")
          .whereEqualTo("userId", currentUser.getUid())
          .get()
          .addOnSuccessListener(queryDocumentSnapshots -> {
              progressPicker.setVisibility(View.GONE);
              containerCookbooks.removeAllViews();

              if (queryDocumentSnapshots.isEmpty()) {
                  Toast.makeText(getContext(), "Önce Mutfağım sekmesinden defter oluşturun", Toast.LENGTH_LONG).show();
                  dismiss();
                  return;
              }

              List<Cookbook> sortedBooks = new java.util.ArrayList<>();
              for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                  sortedBooks.add(Cookbook.fromDocument(doc));
              }
              // Local sort to avoid Firestore index requirement
              java.util.Collections.sort(sortedBooks, (b1, b2) -> Long.compare(b2.getCreatedAt(), b1.getCreatedAt()));

              for (Cookbook book : sortedBooks) {
                  addCookbookButton(book);
              }
          })
          .addOnFailureListener(e -> {
              progressPicker.setVisibility(View.GONE);
              Toast.makeText(getContext(), "Defterler yüklenemedi.", Toast.LENGTH_SHORT).show();
          });
    }

    private void addCookbookButton(Cookbook book) {
        MaterialButton button = new MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = 16;
        button.setLayoutParams(params);
        
        button.setText(book.getName());
        button.setIcon(ContextCompat.getDrawable(requireContext(), R.drawable.ic_book));

        button.setOnClickListener(v -> saveToCookbook(book));

        containerCookbooks.addView(button);
    }

    private void saveToCookbook(Cookbook book) {
        if (recipe == null) return;

        db.collection("cookbooks").document(book.getId())
          .update("recipeIds", FieldValue.arrayUnion(recipe.getId()))
          .addOnSuccessListener(aVoid -> {
              Toast.makeText(getContext(), "Tarif " + book.getName() + " defterine eklendi!", Toast.LENGTH_SHORT).show();
              dismiss();
          })
          .addOnFailureListener(e -> {
              Toast.makeText(getContext(), "Hata oluştu", Toast.LENGTH_SHORT).show();
          });
    }
}
