package com.recipebookpro.ui.recipe;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.recipebookpro.R;
import com.recipebookpro.model.Recipe;

import java.util.HashMap;
import java.util.Map;

public class NotesTabFragment extends Fragment {

    private static final String ARG_RECIPE = "recipe";
    private Recipe recipe;
    
    private TextInputEditText etNotes;
    private MaterialButton btnSaveNotes;
    
    private FirebaseFirestore db;
    private FirebaseUser currentUser;

    public static NotesTabFragment newInstance(Recipe recipe) {
        NotesTabFragment fragment = new NotesTabFragment();
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
        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_tab_notes, container, false);
        
        etNotes = view.findViewById(R.id.etNotes);
        btnSaveNotes = view.findViewById(R.id.btnSaveNotes);
        
        if (currentUser != null && recipe != null && !TextUtils.isEmpty(recipe.getId())) {
            loadNotes();
        }
        
        btnSaveNotes.setOnClickListener(v -> saveNotes());
        
        return view;
    }
    
    private void loadNotes() {
        db.collection("recipes").document(recipe.getId())
          .collection("notes").document(currentUser.getUid())
          .get()
          .addOnSuccessListener(documentSnapshot -> {
              if (documentSnapshot.exists()) {
                  String text = documentSnapshot.getString("text");
                  if (text != null) etNotes.setText(text);
              }
          });
    }
    
    private void saveNotes() {
        if (currentUser == null || recipe == null || TextUtils.isEmpty(recipe.getId())) return;
        
        String text = etNotes.getText() != null ? etNotes.getText().toString().trim() : "";
        Map<String, Object> data = new HashMap<>();
        data.put("text", text);
        data.put("updatedAt", System.currentTimeMillis());
        
        btnSaveNotes.setEnabled(false);
        db.collection("recipes").document(recipe.getId())
          .collection("notes").document(currentUser.getUid())
          .set(data)
          .addOnSuccessListener(aVoid -> {
              if (getContext() != null) {
                  Toast.makeText(getContext(), R.string.note_saved, Toast.LENGTH_SHORT).show();
              }
              btnSaveNotes.setEnabled(true);
          })
          .addOnFailureListener(e -> {
              if (getContext() != null) {
                  Toast.makeText(getContext(), R.string.note_save_failed, Toast.LENGTH_SHORT).show();
              }
              btnSaveNotes.setEnabled(true);
          });
    }
}
