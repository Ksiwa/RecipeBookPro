package com.recipebookpro.ui.kitchen;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.recipebookpro.R;
import com.recipebookpro.model.RecipeCollection;

public class CollectionPickerBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_RECIPE_ID = "recipe_id";

    private String recipeId;
    private FirebaseFirestore db;
    private String currentUserId;

    private LinearLayout containerCollections;
    private ProgressBar progressPicker;

    public static CollectionPickerBottomSheet newInstance(String recipeId) {
        CollectionPickerBottomSheet fragment = new CollectionPickerBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_RECIPE_ID, recipeId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            recipeId = getArguments().getString(ARG_RECIPE_ID);
        }
        db = FirebaseFirestore.getInstance();
        currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null ?
                FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_collection_picker, container, false);

        containerCollections = view.findViewById(R.id.containerCollections);
        progressPicker = view.findViewById(R.id.progressCollectionPicker);
        MaterialButton btnNewCollection = view.findViewById(R.id.btnNewCollection);

        btnNewCollection.setOnClickListener(v -> showCreateDialog());

        loadCollections();

        return view;
    }

    private void showCreateDialog() {
        if (currentUserId == null) return;

        TextInputLayout tilName = new TextInputLayout(requireContext());
        tilName.setHint("Koleksiyon Adı");
        TextInputEditText etName = new TextInputEditText(tilName.getContext());
        tilName.addView(etName);

        TextInputLayout tilEmoji = new TextInputLayout(requireContext());
        tilEmoji.setHint("Emoji (örn: \uD83C\uDF70)");
        TextInputEditText etEmoji = new TextInputEditText(tilEmoji.getContext());
        tilEmoji.addView(etEmoji);

        android.widget.LinearLayout layout = new android.widget.LinearLayout(requireContext());
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);
        layout.addView(tilName);
        layout.addView(tilEmoji);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Yeni Koleksiyon")
                .setView(layout)
                .setPositiveButton("Oluştur", (dialog, which) -> {
                    String name = etName.getText() != null ? etName.getText().toString().trim() : "";
                    String emoji = etEmoji.getText() != null ? etEmoji.getText().toString().trim() : "";
                    if (!name.isEmpty()) {
                        if (emoji.isEmpty()) emoji = "\uD83D\uDCC1";
                        RecipeCollection collection = new RecipeCollection(currentUserId, name, emoji);
                        db.collection("collections").add(collection)
                          .addOnSuccessListener(docRef -> {
                              if (recipeId != null && !recipeId.isEmpty()) {
                                  docRef.update("recipeIds", FieldValue.arrayUnion(recipeId))
                                        .addOnSuccessListener(a ->
                                            Toast.makeText(getContext(), "Koleksiyona eklendi", Toast.LENGTH_SHORT).show());
                              }
                              dismiss();
                          });
                    }
                })
                .setNegativeButton("İptal", null)
                .show();
    }

    private void loadCollections() {
        if (currentUserId == null) return;

        progressPicker.setVisibility(View.VISIBLE);
        db.collection("collections")
          .whereEqualTo("userId", currentUserId)
          .get()
          .addOnSuccessListener(queryDocumentSnapshots -> {
              progressPicker.setVisibility(View.GONE);
              containerCollections.removeAllViews();

              if (queryDocumentSnapshots.isEmpty()) {
                  TextView tvEmpty = new TextView(getContext());
                  tvEmpty.setText("Hiç koleksiyonunuz yok.");
                  tvEmpty.setPadding(0, 16, 0, 16);
                  containerCollections.addView(tvEmpty);
                  return;
              }

              for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                  RecipeCollection collection = RecipeCollection.fromDocument(doc);
                  addCollectionView(collection);
              }
          })
          .addOnFailureListener(e -> {
              progressPicker.setVisibility(View.GONE);
              Toast.makeText(getContext(), "Hata oluştu", Toast.LENGTH_SHORT).show();
          });
    }

    private void addCollectionView(RecipeCollection collection) {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.item_collection, containerCollections, false);

        TextView tvEmoji = view.findViewById(R.id.tvCollectionEmoji);
        TextView tvName = view.findViewById(R.id.tvCollectionName);
        TextView tvCount = view.findViewById(R.id.tvCollectionCount);

        tvEmoji.setText(collection.getEmoji());
        tvName.setText(collection.getName());
        int count = collection.getRecipeIds() != null ? collection.getRecipeIds().size() : 0;
        tvCount.setText(count + " tarif");

        view.setOnClickListener(v -> addRecipeToCollection(collection.getId()));

        containerCollections.addView(view);
    }

    private void addRecipeToCollection(String collectionId) {
        db.collection("collections").document(collectionId)
          .update("recipeIds", FieldValue.arrayUnion(recipeId))
          .addOnSuccessListener(aVoid -> {
              Toast.makeText(getContext(), "Koleksiyona eklendi", Toast.LENGTH_SHORT).show();
              dismiss();
          });
    }
}
