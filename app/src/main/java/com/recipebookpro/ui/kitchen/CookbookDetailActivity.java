package com.recipebookpro.ui.kitchen;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

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
import com.recipebookpro.model.Cookbook;
import com.recipebookpro.model.Recipe;
import com.recipebookpro.model.User;
import com.recipebookpro.ui.BaseActivity;
import com.recipebookpro.ui.book.BookReaderActivity;
import com.recipebookpro.adapter.RecipeAdapter;
import com.recipebookpro.ui.recipe.RecipeDetailActivity;

import java.util.ArrayList;
import java.util.List;

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
                Toast.makeText(this, "Defter bulunamadı", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            cookbook = Cookbook.fromDocument(doc);
            updateUI();
            loadRecipes();
        });
    }

    private void updateUI() {
        toolbar.setTitle(cookbook.getName());
        
        if (!TextUtils.isEmpty(cookbook.getDescription())) {
            tvDescription.setText(cookbook.getDescription());
            tvDescription.setVisibility(View.VISIBLE);
        }

        if (!TextUtils.isEmpty(cookbook.getCoverImageUrl())) {
            ivCover.setPadding(0, 0, 0, 0);
            ivCover.setBackground(null);
            ivCover.setImageTintList(null);
            ImageRequest request = new ImageRequest.Builder(this)
                .data(cookbook.getCoverImageUrl())
                .target(ivCover)
                .build();
            Coil.imageLoader(this).enqueue(request);
        }

        boolean isOwner = currentUser != null && currentUser.getUid().equals(cookbook.getUserId());
        boolean isCollaborator = currentUser != null && cookbook.getCollaboratorIds().contains(currentUser.getUid());
        
        if (isOwner) {
            btnFollow.setVisibility(View.GONE);
            toolbar.getMenu().findItem(R.id.action_collaborators).setVisible(true);
            toolbar.getMenu().findItem(R.id.action_edit_cookbook).setVisible(true);
            toolbar.getMenu().findItem(R.id.action_leave_cookbook).setVisible(false);
        } else if (isCollaborator) {
            btnFollow.setVisibility(View.GONE);
            toolbar.getMenu().findItem(R.id.action_collaborators).setVisible(true);
            toolbar.getMenu().findItem(R.id.action_edit_cookbook).setVisible(false);
            toolbar.getMenu().findItem(R.id.action_leave_cookbook).setVisible(true);
        } else {
            toolbar.getMenu().findItem(R.id.action_collaborators).setVisible(false);
            toolbar.getMenu().findItem(R.id.action_edit_cookbook).setVisible(false);
            toolbar.getMenu().findItem(R.id.action_leave_cookbook).setVisible(false);
            if (cookbook.isPublic()) {
                btnFollow.setVisibility(View.VISIBLE);
                boolean isFollowing = currentUser != null && cookbook.getFollowerIds().contains(currentUser.getUid());
                btnFollow.setText(isFollowing ? "Takipten Çık" : "Takip Et");
            }
        }

        updateCollaborators();
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
        boolean isFollowing = cookbook.getFollowerIds().contains(currentUser.getUid());
        
        if (isFollowing) {
            db.collection("cookbooks").document(cookbookId)
              .update("followerIds", FieldValue.arrayRemove(currentUser.getUid()));
        } else {
            db.collection("cookbooks").document(cookbookId)
              .update("followerIds", FieldValue.arrayUnion(currentUser.getUid()));
        }
    }

    private void leaveCookbook() {
        if (currentUser == null) return;
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Ortaklıktan Ayrıl")
                .setMessage("Bu defterden ayrılmak istiyor musunuz?")
                .setPositiveButton("Ayrıl", (d, w) -> {
                    db.collection("cookbooks").document(cookbookId)
                            .update("collaboratorIds", FieldValue.arrayRemove(currentUser.getUid()))
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this, "Defterden ayrıldınız", Toast.LENGTH_SHORT).show();
                                finish();
                            });
                })
                .setNegativeButton("İptal", null)
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
        String deepLink = "recipebook://cookbook/" + cookbook.getId();
        String shareText = cookbook.getName() + " defterine göz at!\n" + deepLink;
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, shareText);
        startActivity(Intent.createChooser(intent, "Defteri Paylaş"));
    }
}
