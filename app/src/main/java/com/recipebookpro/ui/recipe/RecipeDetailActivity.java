package com.recipebookpro.ui.recipe;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.recipebookpro.R;
import com.recipebookpro.model.Recipe;
import com.recipebookpro.model.User;
import com.recipebookpro.ui.BaseActivity;
import com.recipebookpro.ui.kitchen.CollectionPickerBottomSheet;
import com.recipebookpro.ui.recipe.adapter.RecipeDetailPagerAdapter;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import com.recipebookpro.worker.MergeIngredientsWorker;

import java.util.ArrayList;
import java.util.List;

import coil.Coil;
import coil.request.ImageRequest;

public class RecipeDetailActivity extends BaseActivity {

    public static final String EXTRA_RECIPE = "extra_recipe";

    private Recipe recipe;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    
    private ArrayList<String> userAllergens = new ArrayList<>();
    private boolean isLiked = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recipe_detail);

        applyInsetsToView(findViewById(R.id.recipeDetailRoot));

        recipe = (Recipe) getIntent().getSerializableExtra(EXTRA_RECIPE);
        if (recipe == null && getIntent().getData() != null) {
            String recipeId = getIntent().getData().getLastPathSegment();
            if (!TextUtils.isEmpty(recipeId)) {
                fetchRecipeFromDeepLink(recipeId);
                return;
            }
        }

        if (recipe == null) {
            finish();
            return;
        }

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();

        setupToolbar();
        loadUserAllergensAndSetupPager();
        setupFAB();
    }

    private void fetchRecipeFromDeepLink(String recipeId) {
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        
        db.collection("recipes").document(recipeId).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                recipe = Recipe.fromDocument(doc);
                setupToolbar();
                loadUserAllergensAndSetupPager();
                setupFAB();
            } else {
                Toast.makeText(this, "Tarif bulunamadı", Toast.LENGTH_SHORT).show();
                finish();
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Bağlantı hatası", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbarDetail);
        toolbar.setTitle(recipe.getTitle());

        toolbar.setNavigationOnClickListener(v -> finish());
        
        toolbar.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_share) {
                shareRecipe();
                return true;
            } else if (itemId == R.id.action_like) {
                toggleLike(item);
                return true;
            } else if (itemId == R.id.action_edit) {
                Intent intent = new Intent(this, RecipeAddEditActivity.class);
                intent.putExtra(RecipeAddEditActivity.EXTRA_RECIPE, recipe);
                startActivity(intent);
                finish(); // Close current to allow fresh reload when returning
                return true;
            } else if (itemId == R.id.action_delete) {
                showDeleteDialog();
                return true;
            } else if (itemId == R.id.action_add_shopping) {
                addToShoppingList();
                return true;
            } else if (itemId == R.id.action_add_collection) {
                addToCollection();
                return true;
            }
            return false;
        });

        // Hide edit and delete option if user doesn't own it
        String recipeUserId = recipe.getUserId();
        if (currentUser == null || (!TextUtils.isEmpty(recipeUserId) && !currentUser.getUid().equals(recipeUserId))) {
            toolbar.getMenu().findItem(R.id.action_edit).setVisible(false);
            toolbar.getMenu().findItem(R.id.action_delete).setVisible(false);
        }

        // Load image using Coil
        ImageView ivRecipeCover = findViewById(R.id.ivRecipeCover);
        if (!TextUtils.isEmpty(recipe.getImageUrl())) {
            ImageRequest request = new ImageRequest.Builder(this)
                .data(recipe.getImageUrl())
                .target(ivRecipeCover)
                .build();
            Coil.imageLoader(this).enqueue(request);
        } else {
            // fallback generic image if needed, or just let scrim cover it
            ivRecipeCover.setBackgroundColor(getResources().getColor(android.R.color.black, getTheme()));
        }
    }

    private void loadUserAllergensAndSetupPager() {
        if (currentUser == null) {
            setupViewPagerAndTabs();
            return;
        }

        db.collection("users").document(currentUser.getUid()).get()
          .addOnSuccessListener(documentSnapshot -> {
              if (documentSnapshot.exists()) {
                  User user = documentSnapshot.toObject(User.class);
                  if (user != null && user.getAllergens() != null) {
                      userAllergens.addAll(user.getAllergens());
                  }
              }
              checkAllergens();
              setupViewPagerAndTabs();
          })
          .addOnFailureListener(e -> {
              setupViewPagerAndTabs(); // setup anyway
          });
    }

    private void checkAllergens() {
        if (userAllergens.isEmpty() || recipe.getAllergens().isEmpty()) return;

        List<String> matchingAllergens = new ArrayList<>();
        for (String userAllergen : userAllergens) {
            for (String recipeAllergen : recipe.getAllergens()) {
                if (userAllergen.equalsIgnoreCase(recipeAllergen)) {
                    matchingAllergens.add(recipeAllergen);
                }
            }
        }

        if (!matchingAllergens.isEmpty()) {
            MaterialCardView cardAllergy = findViewById(R.id.cardAllergyWarning);
            TextView tvAllergyWarning = findViewById(R.id.tvAllergyWarning);
            cardAllergy.setVisibility(View.VISIBLE);
            String allergensText = TextUtils.join(", ", matchingAllergens);
            tvAllergyWarning.setText(getString(R.string.allergy_contains, allergensText));
        }
    }

    private void setupViewPagerAndTabs() {
        ViewPager2 viewPager = findViewById(R.id.viewPager);
        TabLayout tabLayout = findViewById(R.id.tabLayout);

        RecipeDetailPagerAdapter pagerAdapter = new RecipeDetailPagerAdapter(this, recipe, userAllergens);
        viewPager.setAdapter(pagerAdapter);

        String[] tabTitles = {
            getString(R.string.tab_ingredients),
            getString(R.string.tab_steps),
            getString(R.string.tab_notes)
        };

        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(tabTitles[position])
        ).attach();
    }

    private void setupFAB() {
        ExtendedFloatingActionButton fab = findViewById(R.id.fabAddToCookbook);
        fab.setOnClickListener(v -> {
            CookbookPickerBottomSheet bottomSheet = CookbookPickerBottomSheet.newInstance(recipe);
            bottomSheet.show(getSupportFragmentManager(), "CookbookPickerBottomSheet");
        });
        
        // Hide FAB if scrolling down
        ViewPager2 viewPager = findViewById(R.id.viewPager);
        // Note: ViewPager2 doesn't directly expose scroll for FAB hide, 
        // usually done inside the Fragment's RecyclerView scroll listener.
    }

    private void shareRecipe() {
        String deepLink = "recipebook://recipe/" + recipe.getId();
        StringBuilder sb = new StringBuilder();
        sb.append(recipe.getTitle());
        if (!recipe.getDescription().isEmpty()) {
            sb.append("\n").append(recipe.getDescription());
        }
        sb.append("\n\n").append(deepLink);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(intent, getString(R.string.share_recipe)));
    }

    private void toggleLike(MenuItem item) {
        if (currentUser == null) return;
        isLiked = !isLiked;
        item.setIcon(isLiked ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
        // In a real app, you would add the user's UID to a 'likes' subcollection
        // and increment the recipe's like count atomically.
        String message = isLiked ? getString(R.string.liked) : "Beğeni geri alındı";
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void showDeleteDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_recipe)
                .setMessage(R.string.confirm_delete)
                .setPositiveButton(R.string.delete, (dialog, which) -> deleteRecipe())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void deleteRecipe() {
        db.collection("recipes").document(recipe.getId())
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(RecipeDetailActivity.this, R.string.recipe_deleted, Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> 
                    Toast.makeText(RecipeDetailActivity.this, R.string.recipe_delete_failed, Toast.LENGTH_SHORT).show()
                );
    }

    private void addToCollection() {
        if (recipe == null) return;
        CollectionPickerBottomSheet sheet = CollectionPickerBottomSheet.newInstance(recipe.getId());
        sheet.show(getSupportFragmentManager(), "CollectionPicker");
    }

    private void addToShoppingList() {
        if (currentUser == null) return;
        
        String listName = recipe.getTitle() + " Alışverişi";
        
        Data inputData = new Data.Builder()
            .putString(MergeIngredientsWorker.KEY_USER_ID, currentUser.getUid())
            .putStringArray(MergeIngredientsWorker.KEY_RECIPE_IDS, new String[]{recipe.getId()})
            .putString(MergeIngredientsWorker.KEY_LIST_NAME, listName)
            .build();
            
        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(MergeIngredientsWorker.class)
            .setInputData(inputData)
            .build();
            
        WorkManager.getInstance(this).enqueue(workRequest);
        Toast.makeText(this, "Malzemeler alışveriş listesi olarak hazırlanıyor", Toast.LENGTH_SHORT).show();
    }
}
