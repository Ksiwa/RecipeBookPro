package com.recipebookpro.presentation.ui.kitchen;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.recipebookpro.R;
import com.recipebookpro.domain.model.Cookbook;
import com.recipebookpro.domain.model.Recipe;
import com.recipebookpro.domain.model.User;
import com.recipebookpro.presentation.adapter.RecipeAdapter;
import com.recipebookpro.presentation.ui.BaseActivity;
import com.recipebookpro.presentation.ui.kitchen.adapter.CookbookAdapter;
import com.recipebookpro.presentation.ui.recipe.RecipeDetailActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import coil.Coil;
import coil.request.ImageRequest;
import coil.transform.CircleCropTransformation;

public class PublicProfileActivity extends BaseActivity {

    public static final String EXTRA_USER_ID = "user_id";

    private String userId;
    private FirebaseFirestore db;

    private MaterialToolbar toolbar;
    private ImageView ivAvatar;
    private TextView tvName;
    private TextView tvFollowerCount;
    private TextView tvFollowingCount;
    private TextView tvPublicCookbooksEmpty;
    private TextView tvPublicRecipesEmpty;
    private TextView tvFollowedCookbooksEmpty;
    private MaterialButton btnFollowUser;
    private RecyclerView rvCookbooks;
    private RecyclerView rvPublicRecipes;
    private RecyclerView rvUserFollowedCookbooks;
    private ProgressBar progress;

    private CookbookAdapter adapter;
    private CookbookAdapter followedAdapter;
    private RecipeAdapter publicRecipeAdapter;
    private List<Cookbook> publicCookbooks = new ArrayList<>();
    private List<Cookbook> followedCookbooks = new ArrayList<>();
    private List<Recipe> publicRecipes = new ArrayList<>();
    private FirebaseUser currentUser;
    private ListenerRegistration userProfileListener;
    private ListenerRegistration publicCookbooksListener;
    private ListenerRegistration followedCookbooksListener;
    private ListenerRegistration publicRecipesListener;

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
        tvFollowerCount = findViewById(R.id.tvFollowerCount);
        tvFollowingCount = findViewById(R.id.tvFollowingCount);
        tvPublicCookbooksEmpty = findViewById(R.id.tvPublicCookbooksEmpty);
        tvPublicRecipesEmpty = findViewById(R.id.tvPublicRecipesEmpty);
        tvFollowedCookbooksEmpty = findViewById(R.id.tvFollowedCookbooksEmpty);
        btnFollowUser = findViewById(R.id.btnFollowUser);
        rvCookbooks = findViewById(R.id.rvPublicCookbooks);
        rvPublicRecipes = findViewById(R.id.rvPublicRecipes);
        rvUserFollowedCookbooks = findViewById(R.id.rvUserFollowedCookbooks);
        progress = findViewById(R.id.progressPublicProfile);

        toolbar.setNavigationOnClickListener(v -> finish());

        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        rvCookbooks.setLayoutManager(new GridLayoutManager(this, 2));
        adapter = new CookbookAdapter(publicCookbooks, book -> {
            Intent intent = new Intent(this, CookbookDetailActivity.class);
            intent.putExtra(CookbookDetailActivity.EXTRA_COOKBOOK_ID, book.getId());
            startActivity(intent);
        });
        rvCookbooks.setAdapter(adapter);

        rvPublicRecipes.setLayoutManager(new LinearLayoutManager(this));
        publicRecipeAdapter = new RecipeAdapter(recipe -> {
            Intent intent = new Intent(this, RecipeDetailActivity.class);
            intent.putExtra(RecipeDetailActivity.EXTRA_RECIPE, recipe);
            startActivity(intent);
        });
        rvPublicRecipes.setAdapter(publicRecipeAdapter);

        rvUserFollowedCookbooks.setLayoutManager(new GridLayoutManager(this, 2));
        followedAdapter = new CookbookAdapter(followedCookbooks, book -> {
            Intent intent = new Intent(this, CookbookDetailActivity.class);
            intent.putExtra(CookbookDetailActivity.EXTRA_COOKBOOK_ID, book.getId());
            startActivity(intent);
        });
        rvUserFollowedCookbooks.setAdapter(followedAdapter);
        
        btnFollowUser.setOnClickListener(v -> toggleUserFollow());
    }

    private void loadUserProfile() {
        if (TextUtils.isEmpty(userId)) return;
        
        progress.setVisibility(View.VISIBLE);
        if (userProfileListener != null) {
            userProfileListener.remove();
        }
        
        userProfileListener = db.collection("users").document(userId).addSnapshotListener((doc, e) -> {
            progress.setVisibility(View.GONE);
            if (e != null) {
                Toast.makeText(this, R.string.load_failed, Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (doc == null || !doc.exists()) {
                Toast.makeText(this, R.string.recipe_owner_unknown, Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                User user = doc.toObject(User.class);
                if (user != null) {
                    // Update user fields if document ID is different from uid field
                    if (TextUtils.isEmpty(user.getUid())) user.setUid(doc.getId());
                    
                    tvName.setText(user.getDisplayName());
                    toolbar.setTitle(user.getDisplayName());
                    
                    // Safe string formatting for counts
                    tvFollowerCount.setText(getResources().getQuantityString(
                            R.plurals.followers_count_display, user.getFollowerCount(), user.getFollowerCount()));
                    tvFollowingCount.setText(getResources().getQuantityString(
                            R.plurals.following_count_display, user.getFollowingCount(), user.getFollowingCount()));

                    if (!TextUtils.isEmpty(user.getProfileImageUrl())) {
                        ImageRequest request = new ImageRequest.Builder(this)
                                .data(user.getProfileImageUrl())
                                .target(ivAvatar)
                                .crossfade(true)
                                .transformations(new CircleCropTransformation())
                                .placeholder(R.drawable.ic_nav_profile)
                                .error(R.drawable.ic_nav_profile)
                                .build();
                        Coil.imageLoader(this).enqueue(request);
                    } else {
                        ivAvatar.setImageResource(R.drawable.ic_nav_profile);
                    }

                    if (currentUser != null && !currentUser.getUid().equals(userId)) {
                        btnFollowUser.setVisibility(View.VISIBLE);
                        if (user.getFollowerIds().contains(currentUser.getUid())) {
                            btnFollowUser.setText(R.string.unfollow);
                        } else {
                            btnFollowUser.setText(R.string.follow);
                        }
                    } else {
                        btnFollowUser.setVisibility(View.GONE);
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadPublicCookbooks() {
        if (publicCookbooksListener != null) {
            publicCookbooksListener.remove();
        }
        publicCookbooksListener = db.collection("cookbooks")
          .whereEqualTo("userId", userId)
          .addSnapshotListener((value, error) -> {
              if (error != null) return;
              try {
                  publicCookbooks.clear();
                  if (value != null) {
                      for (QueryDocumentSnapshot doc : value) {
                          Cookbook cookbook = Cookbook.fromDocument(doc);
                          if (cookbook != null && cookbook.isPublic()) {
                              publicCookbooks.add(cookbook);
                          }
                      }
                      Collections.sort(publicCookbooks, (b1, b2) -> Long.compare(b2.getCreatedAt(), b1.getCreatedAt()));
                  }
                  adapter.notifyDataSetChanged();
                  tvPublicCookbooksEmpty.setVisibility(publicCookbooks.isEmpty() ? View.VISIBLE : View.GONE);
                  loadFollowedCookbooks();
                  loadPublicRecipes();
              } catch (Exception e) {
                  e.printStackTrace();
              }
          });
    }

    private void loadFollowedCookbooks() {
        if (followedCookbooksListener != null) {
            followedCookbooksListener.remove();
        }
        followedCookbooksListener = db.collection("cookbooks")
          .whereArrayContains("followerIds", userId)
          .addSnapshotListener((value, error) -> {
              if (error != null) return;
              try {
                  followedCookbooks.clear();
                  if (value != null) {
                      for (QueryDocumentSnapshot doc : value) {
                          Cookbook cookbook = Cookbook.fromDocument(doc);
                          if (cookbook != null && cookbook.isPublic()) {
                              followedCookbooks.add(cookbook);
                          }
                      }
                  }
                  followedAdapter.notifyDataSetChanged();
                  tvFollowedCookbooksEmpty.setVisibility(followedCookbooks.isEmpty() ? View.VISIBLE : View.GONE);
              } catch (Exception e) {
                  e.printStackTrace();
              }
          });
    }

    private void loadPublicRecipes() {
        if (publicRecipesListener != null) {
            publicRecipesListener.remove();
        }
        publicRecipesListener = db.collection("recipes")
                .whereEqualTo("userId", userId)
                .addSnapshotListener((value, error) -> {
                    if (error != null) return;
                    try {
                        publicRecipes.clear();
                        if (value != null) {
                            for (QueryDocumentSnapshot doc : value) {
                                Recipe recipe = Recipe.fromDocument(doc);
                                if (recipe != null && recipe.isPublic()) {
                                    publicRecipes.add(recipe);
                                }
                            }
                            Collections.sort(publicRecipes, (r1, r2) -> Long.compare(r2.getCreatedAt(), r1.getCreatedAt()));
                        }
                        publicRecipeAdapter.setRecipeList(publicRecipes);
                        tvPublicRecipesEmpty.setVisibility(publicRecipes.isEmpty() ? View.VISIBLE : View.GONE);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
    }

    private void toggleUserFollow() {
        if (currentUser == null) return;
        btnFollowUser.setEnabled(false);
        String currentUid = currentUser.getUid();

        db.runTransaction(transaction -> {
            DocumentSnapshot targetUserDoc = transaction.get(db.collection("users").document(userId));
            DocumentSnapshot currentUserDoc = transaction.get(db.collection("users").document(currentUid));

            List<String> targetFollowerIds = (List<String>) targetUserDoc.get("followerIds");
            if (targetFollowerIds == null) targetFollowerIds = new ArrayList<>();
            long targetFollowerCount = targetUserDoc.getLong("followerCount") != null ? targetUserDoc.getLong("followerCount") : 0;

            List<String> currentFollowingIds = (List<String>) currentUserDoc.get("followingIds");
            if (currentFollowingIds == null) currentFollowingIds = new ArrayList<>();
            long currentFollowingCount = currentUserDoc.getLong("followingCount") != null ? currentUserDoc.getLong("followingCount") : 0;

            if (targetFollowerIds.contains(currentUid)) {
                targetFollowerIds.remove(currentUid);
                targetFollowerCount = Math.max(0, targetFollowerCount - 1);
                currentFollowingIds.remove(userId);
                currentFollowingCount = Math.max(0, currentFollowingCount - 1);
            } else {
                targetFollowerIds.add(currentUid);
                targetFollowerCount++;
                currentFollowingIds.add(userId);
                currentFollowingCount++;
            }

            transaction.update(db.collection("users").document(userId),
                    "followerIds", targetFollowerIds,
                    "followerCount", targetFollowerCount);
            
            transaction.update(db.collection("users").document(currentUid),
                    "followingIds", currentFollowingIds,
                    "followingCount", currentFollowingCount);

            return null;
        }).addOnSuccessListener(aVoid -> btnFollowUser.setEnabled(true))
        .addOnFailureListener(e -> {
            btnFollowUser.setEnabled(true);
            Toast.makeText(this, R.string.follow_action_failed, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onDestroy() {
        if (userProfileListener != null) userProfileListener.remove();
        if (publicCookbooksListener != null) publicCookbooksListener.remove();
        if (followedCookbooksListener != null) followedCookbooksListener.remove();
        if (publicRecipesListener != null) publicRecipesListener.remove();
        super.onDestroy();
    }
}
