package com.recipebookpro.ui.kitchen;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.recipebookpro.R;
import com.recipebookpro.model.Cookbook;
import com.recipebookpro.model.User;
import com.recipebookpro.ui.BaseActivity;
import com.recipebookpro.ui.kitchen.adapter.CookbookAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PublicProfileActivity extends BaseActivity {

    public static final String EXTRA_USER_ID = "user_id";

    private String userId;
    private FirebaseFirestore db;

    private MaterialToolbar toolbar;
    private ImageView ivAvatar;
    private TextView tvName;
    private RecyclerView rvCookbooks;
    private ProgressBar progress;

    private CookbookAdapter adapter;
    private List<Cookbook> publicCookbooks = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_public_profile);

        applyInsetsToView(findViewById(android.R.id.content));

        userId = getIntent().getStringExtra(EXTRA_USER_ID);
        if (TextUtils.isEmpty(userId)) {
            finish();
            return;
        }

        db = FirebaseFirestore.getInstance();

        initViews();
        loadUserProfile();
        loadPublicCookbooks();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbarPublicProfile);
        ivAvatar = findViewById(R.id.ivPublicProfileAvatar);
        tvName = findViewById(R.id.tvPublicProfileName);
        rvCookbooks = findViewById(R.id.rvPublicCookbooks);
        progress = findViewById(R.id.progressPublicProfile);

        toolbar.setNavigationOnClickListener(v -> finish());

        rvCookbooks.setLayoutManager(new GridLayoutManager(this, 2));
        adapter = new CookbookAdapter(publicCookbooks, book -> {
            Intent intent = new Intent(this, CookbookDetailActivity.class);
            intent.putExtra(CookbookDetailActivity.EXTRA_COOKBOOK_ID, book.getId());
            startActivity(intent);
        });
        rvCookbooks.setAdapter(adapter);
    }

    private void loadUserProfile() {
        progress.setVisibility(View.VISIBLE);
        db.collection("users").document(userId).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                User user = doc.toObject(User.class);
                if (user != null) {
                    tvName.setText(user.getDisplayName());
                    toolbar.setTitle(user.getDisplayName());
                }
            }
        });
    }

    private void loadPublicCookbooks() {
        db.collection("cookbooks")
          .whereEqualTo("userId", userId)
          .whereEqualTo("isPublic", true)
          .addSnapshotListener((value, error) -> {
              progress.setVisibility(View.GONE);
              if (error != null) return;
              publicCookbooks.clear();
              if (value != null) {
                  for (QueryDocumentSnapshot doc : value) {
                      publicCookbooks.add(Cookbook.fromDocument(doc));
                  }
                  Collections.sort(publicCookbooks, (b1, b2) -> Long.compare(b2.getCreatedAt(), b1.getCreatedAt()));
              }
              adapter.notifyDataSetChanged();
          });
    }
}
