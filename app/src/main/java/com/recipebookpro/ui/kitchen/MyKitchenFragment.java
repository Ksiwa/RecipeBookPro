package com.recipebookpro.ui.kitchen;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.recipebookpro.R;
import com.recipebookpro.model.Cookbook;
import com.recipebookpro.ui.book.BookReaderActivity;
import com.recipebookpro.ui.kitchen.adapter.CookbookAdapter;

import java.util.ArrayList;
import java.util.List;

public class MyKitchenFragment extends Fragment {

    private RecyclerView rvCookbooks;
    private TextView tvEmptyCookbooks;
    private ProgressBar progressKitchen;
    private CookbookAdapter adapter;
    private List<Cookbook> cookbookList = new ArrayList<>();
    private FirebaseFirestore db;
    private FirebaseUser currentUser;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_my_kitchen, container, false);
        
        rvCookbooks = view.findViewById(R.id.rvCookbooks);
        tvEmptyCookbooks = view.findViewById(R.id.tvEmptyCookbooks);
        progressKitchen = view.findViewById(R.id.progressKitchen);
        ExtendedFloatingActionButton fabAddCookbook = view.findViewById(R.id.fabAddCookbook);
        
        rvCookbooks.setLayoutManager(new GridLayoutManager(getContext(), 2));
        adapter = new CookbookAdapter(cookbookList, this::openCookbook);
        rvCookbooks.setAdapter(adapter);

        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        fabAddCookbook.setOnClickListener(v -> showCreateCookbookDialog());

        loadCookbooks();
        
        return view;
    }

    private void loadCookbooks() {
        if (currentUser == null) return;
        progressKitchen.setVisibility(View.VISIBLE);
        
        db.collection("cookbooks")
          .whereEqualTo("userId", currentUser.getUid())
          .addSnapshotListener((value, error) -> {
              progressKitchen.setVisibility(View.GONE);
              if (error != null) {
                  Toast.makeText(getContext(), "Defterler yüklenemedi", Toast.LENGTH_SHORT).show();
                  return;
              }
              cookbookList.clear();
              if (value != null) {
                  for (QueryDocumentSnapshot doc : value) {
                      cookbookList.add(Cookbook.fromDocument(doc));
                  }
                  // Local sort to avoid Firestore index requirement
                  java.util.Collections.sort(cookbookList, (b1, b2) -> Long.compare(b2.getCreatedAt(), b1.getCreatedAt()));
              }
              adapter.notifyDataSetChanged();
              tvEmptyCookbooks.setVisibility(cookbookList.isEmpty() ? View.VISIBLE : View.GONE);
          });
    }

    private void showCreateCookbookDialog() {
        if (currentUser == null) return;
        
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Yeni Tarif Defteri");
        
        final EditText input = new EditText(requireContext());
        input.setHint("Örn: Tatlılar, Yöresel Yemekler");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        
        FrameLayout container = new FrameLayout(requireContext());
        FrameLayout.LayoutParams params = new  FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = 50;
        params.rightMargin = 50;
        input.setLayoutParams(params);
        container.addView(input);
        
        builder.setView(container);

        builder.setPositiveButton("Oluştur", (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (!name.isEmpty()) {
                Cookbook book = new Cookbook(currentUser.getUid(), name);
                db.collection("cookbooks").add(book)
                  .addOnSuccessListener(documentReference -> Toast.makeText(getContext(), "Defter oluşturuldu", Toast.LENGTH_SHORT).show())
                  .addOnFailureListener(e -> Toast.makeText(getContext(), "Hata oluştu", Toast.LENGTH_SHORT).show());
            }
        });
        builder.setNegativeButton("İptal", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void openCookbook(Cookbook book) {
        if (book.getRecipeIds() == null || book.getRecipeIds().isEmpty()) {
            Toast.makeText(getContext(), "Bu defter boş. Tarif detayından deftere tarif ekleyebilirsiniz.", Toast.LENGTH_LONG).show();
            return;
        }
        
        Intent intent = new Intent(getContext(), BookReaderActivity.class);
        intent.putExtra(BookReaderActivity.EXTRA_COOKBOOK_ID, book.getId());
        intent.putExtra(BookReaderActivity.EXTRA_COOKBOOK_NAME, book.getName());
        startActivity(intent);
    }
}
