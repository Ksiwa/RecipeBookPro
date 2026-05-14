package com.recipebookpro.presentation.ui.kitchen;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.recipebookpro.R;
import com.recipebookpro.data.remote.CookbookDescriptionLocalizer;
import com.recipebookpro.domain.model.Cookbook;
import com.recipebookpro.domain.model.Recipe;
import com.recipebookpro.domain.model.User;
import com.recipebookpro.presentation.ui.BaseActivity;
import com.recipebookpro.presentation.ui.LocaleHelper;
import com.recipebookpro.presentation.share.PublicShareIntentHelper;
import com.recipebookpro.presentation.ui.book.BookReaderActivity;
import com.recipebookpro.presentation.adapter.RecipeAdapter;
import com.recipebookpro.presentation.ui.recipe.RecipeDetailActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import coil.Coil;
import coil.request.ImageRequest;

public class CookbookDetailActivity extends BaseActivity {

    public static final String EXTRA_COOKBOOK_ID = "cookbook_id";

    private String cookbookId;
    private Cookbook cookbook;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;

    private MaterialToolbar toolbar;
    private ImageView ivCover;
    private TextView tvDescription;
    private TextView tvEmpty;
    private MaterialButton btnFollow;
    private RecyclerView rvRecipes;
    private ProgressBar progress;
    private ChipGroup chipGroupCollaborators;
    private View scrollCollaborators;

    private RecipeAdapter adapter;
    private List<Recipe> recipeList = new ArrayList<>();

    private final ExecutorService descriptionExecutor = Executors.newSingleThreadExecutor();
    private int descriptionJobSeq = 0;
    /** Son başarılı çeviri veya ham gösterimin yapıldığı uygulama dili (LocaleHelper). */
    private String descriptionAppliedLang;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cookbook_detail);

        applyInsetsToView(findViewById(R.id.cookbookDetailRoot));

        cookbookId = getIntent().getStringExtra(EXTRA_COOKBOOK_ID);
        if (TextUtils.isEmpty(cookbookId) && getIntent().getData() != null) {
            cookbookId = getIntent().getData().getLastPathSegment();
        }
        if (TextUtils.isEmpty(cookbookId)) {
            finish();
            return;
        }

        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        initViews();
        loadCookbook();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbarCookbookDetail);
        ivCover = findViewById(R.id.ivCookbookCover);
        tvDescription = findViewById(R.id.tvCookbookDescription);
        tvEmpty = findViewById(R.id.tvCookbookEmpty);
        btnFollow = findViewById(R.id.btnFollowUnfollow);
        rvRecipes = findViewById(R.id.rvCookbookRecipes);
        progress = findViewById(R.id.progressCookbookDetail);
        chipGroupCollaborators = findViewById(R.id.chipGroupCollaborators);
        scrollCollaborators = findViewById(R.id.scrollCollaborators);

        toolbar.setNavigationOnClickListener(v -> finish());
        
        toolbar.inflateMenu(R.menu.menu_cookbook_detail);
        
        // Sağdaki ikonları beyaz yapalım
        android.view.Menu menu = toolbar.getMenu();
        for (int i = 0; i < menu.size(); i++) {
            android.graphics.drawable.Drawable icon = menu.getItem(i).getIcon();
            if (icon != null) {
                androidx.core.graphics.drawable.DrawableCompat.setTint(icon, android.graphics.Color.WHITE);
            }
        }
        
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_edit_cookbook) {
                Intent editIntent = new Intent(this, CookbookAddEditActivity.class);
                editIntent.putExtra(CookbookAddEditActivity.EXTRA_COOKBOOK_ID, cookbookId);
                startActivity(editIntent);
                return true;
            } else if (item.getItemId() == R.id.action_read) {
                if (cookbook != null) {
                    Intent intent = new Intent(this, BookReaderActivity.class);
                    intent.putExtra(BookReaderActivity.EXTRA_COOKBOOK_ID, cookbook.getId());
                    intent.putExtra(BookReaderActivity.EXTRA_COOKBOOK_NAME, cookbook.getName());
                    startActivity(intent);
                }
                return true;
            } else if (item.getItemId() == R.id.action_share) {
                shareCookbook();
                return true;
            } else if (item.getItemId() == R.id.action_collaborators) {
                CollaboratorsBottomSheet bs = CollaboratorsBottomSheet.newInstance(cookbookId);
                bs.show(getSupportFragmentManager(), "Collaborators");
                return true;
            } else if (item.getItemId() == R.id.action_leave_cookbook) {
                leaveCookbook();
                return true;
            }else if (item.getItemId() == R.id.action_delete_cookbook) {
                confirmDeleteCookbook();
                return true;
            }

            return false;
        });

        rvRecipes.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RecipeAdapter(recipe -> {
            Intent intent = new Intent(this, RecipeDetailActivity.class);
            intent.putExtra(RecipeDetailActivity.EXTRA_RECIPE, recipe);
            startActivity(intent);
        });
        rvRecipes.setAdapter(adapter);

        btnFollow.setOnClickListener(v -> toggleFollow());
    }

    private void loadCookbook() {
        progress.setVisibility(View.VISIBLE);
        db.collection("cookbooks").document(cookbookId).addSnapshotListener((doc, e) -> {
            if (e != null || doc == null || !doc.exists()) {
                progress.setVisibility(View.GONE);
                Toast.makeText(this, R.string.cookbook_not_found, Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            cookbook = Cookbook.fromDocument(doc);
            updateUI();
            setupOwnerCard();
            loadRecipes();
        });
    }

    private void updateUI() {
        toolbar.setTitle(cookbook.getName());
        
        if (!TextUtils.isEmpty(cookbook.getDescription())) {
            refreshCookbookDescriptionText();
        } else {
            tvDescription.setVisibility(View.GONE);
        }

        // Default placeholder state
        ivCover.setScaleType(ImageView.ScaleType.FIT_CENTER);
        ivCover.setImageResource(R.drawable.ic_book);
        ivCover.setPadding(0, 0, 0, 0);
        
        android.util.TypedValue typedValue = new android.util.TypedValue();
        getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimaryContainer, typedValue, true);
        ivCover.setBackgroundColor(typedValue.data);
        getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnPrimaryContainer, typedValue, true);
        ivCover.setImageTintList(android.content.res.ColorStateList.valueOf(typedValue.data));

        if (!TextUtils.isEmpty(cookbook.getCoverImageUrl())) {
            ImageRequest request = new ImageRequest.Builder(this)
                .data(cookbook.getCoverImageUrl())
                .target(new coil.target.Target() {
                    @Override
                    public void onStart(@Nullable android.graphics.drawable.Drawable placeholder) {}

                    @Override
                    public void onSuccess(@NonNull android.graphics.drawable.Drawable result) {
                        ivCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        ivCover.setBackground(null);
                        ivCover.setImageTintList(null);
                        ivCover.setImageDrawable(result);
                    }

                    @Override
                    public void onError(@Nullable android.graphics.drawable.Drawable error) {
                        // Keep placeholder state
                    }
                })
                .build();
            Coil.imageLoader(this).enqueue(request);
        }

        boolean isOwner = currentUser != null && currentUser.getUid().equals(cookbook.getUserId());
        boolean isCollaborator = currentUser != null && cookbook.getCollaboratorIds().contains(currentUser.getUid());
        
        if (isOwner) {
            btnFollow.setVisibility(View.GONE);
            toolbar.getMenu().findItem(R.id.action_collaborators).setVisible(true);
            MenuItem deleteItem = toolbar.getMenu().findItem(R.id.action_delete_cookbook);
            if (deleteItem != null) {
                deleteItem.setVisible(true);
            }
            toolbar.getMenu().findItem(R.id.action_edit_cookbook).setVisible(true);
            toolbar.getMenu().findItem(R.id.action_leave_cookbook).setVisible(false);
            adapter.setOnRecipeRemoveListener(this::removeRecipeFromCookbook);
        } else if (isCollaborator) {
            btnFollow.setVisibility(View.GONE);
            toolbar.getMenu().findItem(R.id.action_collaborators).setVisible(true);
            MenuItem deleteItem = toolbar.getMenu().findItem(R.id.action_delete_cookbook);
            if (deleteItem != null) deleteItem.setVisible(false);
            toolbar.getMenu().findItem(R.id.action_edit_cookbook).setVisible(false);
            toolbar.getMenu().findItem(R.id.action_leave_cookbook).setVisible(true);
            adapter.setOnRecipeRemoveListener(this::removeRecipeFromCookbook);
        } else {
            toolbar.getMenu().findItem(R.id.action_collaborators).setVisible(false);
            toolbar.getMenu().findItem(R.id.action_edit_cookbook).setVisible(false);
            toolbar.getMenu().findItem(R.id.action_leave_cookbook).setVisible(false);
            MenuItem deleteItem = toolbar.getMenu().findItem(R.id.action_delete_cookbook);
            if (deleteItem != null) deleteItem.setVisible(false);
            adapter.setOnRecipeRemoveListener(null);
            if (cookbook.isPublic()) {
                btnFollow.setVisibility(View.VISIBLE);
                boolean isFollowing = currentUser != null && cookbook.getFollowerIds().contains(currentUser.getUid());
                btnFollow.setText(isFollowing ? R.string.unfollow : R.string.follow);
            }
        }

        updateCollaborators();
    }

    private void refreshCookbookDescriptionText() {
        if (tvDescription == null || cookbook == null) {
            return;
        }
        if (TextUtils.isEmpty(cookbook.getDescription())) {
            tvDescription.setVisibility(View.GONE);
            return;
        }
        tvDescription.setVisibility(View.VISIBLE);
        final String rawFull = cookbook.getDescription();
        final String rawTrim = rawFull.trim();
        tvDescription.setText(rawTrim);
        final int job = ++descriptionJobSeq;
        final String uiLang = LocaleHelper.getLanguage(this);
        descriptionExecutor.execute(() -> {
            try {
                String localized = CookbookDescriptionLocalizer.localizeSync(
                        getApplicationContext(), rawFull, uiLang);
                runOnUiThread(() -> {
                    if (isFinishing() || job != descriptionJobSeq || cookbook == null) {
                        return;
                    }
                    if (!rawFull.equals(cookbook.getDescription())) {
                        return;
                    }
                    tvDescription.setText(TextUtils.isEmpty(localized) ? rawTrim : localized);
                    descriptionAppliedLang = uiLang;
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || job != descriptionJobSeq || cookbook == null) {
                        return;
                    }
                    if (!rawFull.equals(cookbook.getDescription())) {
                        return;
                    }
                    tvDescription.setText(rawTrim);
                    descriptionAppliedLang = uiLang;
                });
            }
        });
    }

    private void setupOwnerCard() {
        if (cookbook == null || TextUtils.isEmpty(cookbook.getUserId())) return;

        View card = findViewById(R.id.cardCookbookOwner);
        TextView tvOwner = findViewById(R.id.tvCookbookOwnerName);
        ImageView ivAvatar = findViewById(R.id.ivCookbookOwnerAvatar);

        db.collection("users").document(cookbook.getUserId()).get()
          .addOnSuccessListener(doc -> {
              if (doc.exists()) {
                  User user = doc.toObject(User.class);
                  if (user != null) {
                      tvOwner.setText(user.getDisplayName());
                      if (!TextUtils.isEmpty(user.getProfileImageUrl())) {
                          ImageRequest request = new ImageRequest.Builder(this)
                              .data(user.getProfileImageUrl())
                              .target(ivAvatar)
                              .crossfade(true)
                              .transformations(new coil.transform.CircleCropTransformation())
                              .placeholder(R.drawable.ic_nav_profile)
                              .build();
                          Coil.imageLoader(this).enqueue(request);
                      }
                      String uid = user.getUid();
                      if (TextUtils.isEmpty(uid)) uid = doc.getId();
                      final String finalUid = uid;

                      card.setOnClickListener(v -> {
                          Intent intent = new Intent(CookbookDetailActivity.this, PublicProfileActivity.class);
                          intent.putExtra(PublicProfileActivity.EXTRA_USER_ID, finalUid);
                          startActivity(intent);
                      });
                  }
              }
          });
    }

    private void updateCollaborators() {
        if (cookbook.getCollaboratorIds() == null || cookbook.getCollaboratorIds().isEmpty()) {
            scrollCollaborators.setVisibility(View.GONE);
            return;
        }
        scrollCollaborators.setVisibility(View.VISIBLE);
        chipGroupCollaborators.removeAllViews();

        List<Task<DocumentSnapshot>> tasks = new ArrayList<>();
        for (String uid : cookbook.getCollaboratorIds()) {
            tasks.add(db.collection("users").document(uid).get());
        }

        Tasks.whenAllSuccess(tasks).addOnSuccessListener(results -> {
            chipGroupCollaborators.removeAllViews();
            for (Object result : results) {
                DocumentSnapshot docSnap = (DocumentSnapshot) result;
                if (!docSnap.exists()) continue;
                User user = docSnap.toObject(User.class);
                if (user == null) continue;

                Chip chip = new Chip(this);
                chip.setText(user.getDisplayName());
                chip.setOnClickListener(v -> {
                    Intent intent = new Intent(this, PublicProfileActivity.class);
                    intent.putExtra(PublicProfileActivity.EXTRA_USER_ID, user.getUid());
                    startActivity(intent);
                });
                chipGroupCollaborators.addView(chip);
            }
        });
    }

    private void toggleFollow() {
        if (currentUser == null) return;

        btnFollow.setEnabled(false);
        String uid = currentUser.getUid();

        db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(db.collection("cookbooks").document(cookbookId));
            List<String> followerIds = (List<String>) snapshot.get("followerIds");
            if (followerIds == null) followerIds = new ArrayList<>();
            long followerCount = snapshot.getLong("followerCount") != null ? snapshot.getLong("followerCount") : 0;

            if (followerIds.contains(uid)) {
                followerIds.remove(uid);
                followerCount = Math.max(0, followerCount - 1);
            } else {
                followerIds.add(uid);
                followerCount++;
            }

            transaction.update(db.collection("cookbooks").document(cookbookId),
                    "followerIds", followerIds,
                    "followerCount", followerCount);
            return null;
        }).addOnSuccessListener(aVoid -> btnFollow.setEnabled(true))
        .addOnFailureListener(e -> {
            btnFollow.setEnabled(true);
            Toast.makeText(this, R.string.follow_action_failed, Toast.LENGTH_SHORT).show();
        });
    }

    private void leaveCookbook() {
        if (currentUser == null) return;
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.leave_collaboration)
                .setMessage(R.string.cookbook_leave_confirm)
                .setPositiveButton(R.string.leave, (d, w) -> {
                    db.collection("cookbooks").document(cookbookId)
                            .update("collaboratorIds", FieldValue.arrayRemove(currentUser.getUid()))
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this, R.string.left_cookbook, Toast.LENGTH_SHORT).show();
                                finish();
                            });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void removeRecipeFromCookbook(Recipe recipe) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.remove_recipe_title)
                .setMessage(getString(R.string.remove_recipe_from_cookbook_confirm, recipe.getTitle()))
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    db.collection("cookbooks").document(cookbookId)
                            .update("recipeIds", FieldValue.arrayRemove(recipe.getId()))
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this, R.string.recipe_removed_from_cookbook, Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show();
                            });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void loadRecipes() {
        List<String> ids = cookbook.getRecipeIds();
        if (ids == null || ids.isEmpty()) {
            recipeList.clear();
            adapter.notifyDataSetChanged();
            tvEmpty.setVisibility(View.VISIBLE);
            progress.setVisibility(View.GONE);
            return;
        }
        
        tvEmpty.setVisibility(View.GONE);

        // Firestore in-query limit is 10
        List<com.google.android.gms.tasks.Task<com.google.firebase.firestore.QuerySnapshot>> tasks = new ArrayList<>();
        List<List<String>> chunks = partition(ids, 10);

        for (List<String> chunk : chunks) {
            tasks.add(db.collection("recipes")
                    .whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk).get());
        }

        Tasks.whenAllSuccess(tasks).addOnSuccessListener(results -> {
            recipeList.clear();
            for (Object result : results) {
                com.google.firebase.firestore.QuerySnapshot snap = (com.google.firebase.firestore.QuerySnapshot) result;
                for (com.google.firebase.firestore.DocumentSnapshot doc : snap.getDocuments()) {
                    recipeList.add(Recipe.fromDocument(doc));
                }
            }
            adapter.setRecipeList(recipeList);
            progress.setVisibility(View.GONE);
        }).addOnFailureListener(e -> progress.setVisibility(View.GONE));
    }

    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(new ArrayList<>(list.subList(i, Math.min(list.size(), i + size))));
        }
        return partitions;
    }

    private void shareCookbook() {
        if (cookbook == null) return;
        startActivity(PublicShareIntentHelper.createCookbookShareChooserIntent(this, cookbook));
    }

    private void confirmDeleteCookbook() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_cookbook)
                .setMessage(R.string.delete_cookbook_confirm)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    performDelete();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void performDelete() {
        progress.setVisibility(View.VISIBLE); // İşlem başlarken loading göster

        db.collection("cookbooks").document(cookbookId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    // Başarılı: Kullanıcıya bildir ve ekranı kapat
                    Toast.makeText(this, R.string.cookbook_deleted, Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    // Hata: Loading'i kapat ve hata mesajı ver
                    progress.setVisibility(View.GONE);
                    Toast.makeText(this, R.string.delete_error, Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        String lang = LocaleHelper.getLanguage(this);
        if (cookbook != null && !TextUtils.isEmpty(cookbook.getDescription())
                && descriptionAppliedLang != null && !descriptionAppliedLang.equals(lang)) {
            refreshCookbookDescriptionText();
        }
    }

    @Override
    protected void onDestroy() {
        descriptionExecutor.shutdown();
        super.onDestroy();
    }
}
