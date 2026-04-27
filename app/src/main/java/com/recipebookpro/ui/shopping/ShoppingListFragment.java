package com.recipebookpro.ui.shopping;

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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.recipebookpro.R;
import com.recipebookpro.model.ShoppingList;
import com.recipebookpro.ui.shopping.adapter.ShoppingListAdapter;

import java.util.ArrayList;
import java.util.List;

public class ShoppingListFragment extends Fragment {

    private RecyclerView rvShoppingLists;
    private TextView tvEmptyLists;
    private ProgressBar progressShoppingLists;
    
    private ShoppingListAdapter adapter;
    private List<ShoppingList> listCollection = new ArrayList<>();
    
    private FirebaseFirestore db;
    private FirebaseUser currentUser;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_shopping_list, container, false);

        rvShoppingLists = view.findViewById(R.id.rvShoppingLists);
        tvEmptyLists = view.findViewById(R.id.tvEmptyShoppingLists);
        progressShoppingLists = view.findViewById(R.id.progressShoppingLists);
        ExtendedFloatingActionButton fabAddList = view.findViewById(R.id.fabAddShoppingList);

        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        rvShoppingLists.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ShoppingListAdapter(listCollection, list -> {
            Intent intent = new Intent(getContext(), ShoppingListDetailActivity.class);
            intent.putExtra(ShoppingListDetailActivity.EXTRA_LIST_ID, list.getId());
            startActivity(intent);
        });
        rvShoppingLists.setAdapter(adapter);

        fabAddList.setOnClickListener(v -> showCreateListDialog());

        loadShoppingLists();

        return view;
    }

    private void loadShoppingLists() {
        if (currentUser == null) return;
        progressShoppingLists.setVisibility(View.VISIBLE);

        db.collection("shopping_lists")
          .whereEqualTo("userId", currentUser.getUid())
          .orderBy("createdAt", Query.Direction.DESCENDING)
          .addSnapshotListener((value, error) -> {
              progressShoppingLists.setVisibility(View.GONE);
              if (error != null) {
                  Toast.makeText(getContext(), "Listeler yüklenemedi", Toast.LENGTH_SHORT).show();
                  return;
              }
              listCollection.clear();
              if (value != null) {
                  for (QueryDocumentSnapshot doc : value) {
                      listCollection.add(ShoppingList.fromDocument(doc));
                  }
              }
              adapter.notifyDataSetChanged();
              tvEmptyLists.setVisibility(listCollection.isEmpty() ? View.VISIBLE : View.GONE);
          });
    }

    private void showCreateListDialog() {
        if (currentUser == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Yeni Alışveriş Listesi");

        final EditText input = new EditText(requireContext());
        input.setHint("Örn: Hafta Sonu Alışverişi");
        input.setInputType(InputType.TYPE_CLASS_TEXT);

        FrameLayout container = new FrameLayout(requireContext());
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = 50;
        params.rightMargin = 50;
        input.setLayoutParams(params);
        container.addView(input);

        builder.setView(container);

        builder.setPositiveButton("Oluştur", (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (!name.isEmpty()) {
                ShoppingList newList = new ShoppingList(currentUser.getUid(), name);
                db.collection("shopping_lists").add(newList)
                  .addOnSuccessListener(documentReference -> Toast.makeText(getContext(), "Liste oluşturuldu", Toast.LENGTH_SHORT).show())
                  .addOnFailureListener(e -> Toast.makeText(getContext(), "Hata oluştu", Toast.LENGTH_SHORT).show());
            }
        });
        builder.setNegativeButton("İptal", (dialog, which) -> dialog.cancel());
        builder.show();
    }
}
