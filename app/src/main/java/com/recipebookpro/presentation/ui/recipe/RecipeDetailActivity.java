package com.recipebookpro.presentation.ui.recipe;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;

import androidx.appcompat.app.AlertDialog;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.recipebookpro.R;
import com.recipebookpro.domain.model.Recipe;
import com.recipebookpro.domain.model.User;
import com.recipebookpro.presentation.ui.BaseActivity;
import com.recipebookpro.presentation.ui.recipe.adapter.RecipeDetailPagerAdapter;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import com.recipebookpro.data.worker.MergeIngredientsWorker;
import com.recipebookpro.presentation.ui.cooking.CookingModeActivity;

import java.util.ArrayList;
import java.util.List;

import coil.Coil;
import coil.request.ImageRequest;
import com.recipebookpro.domain.service.TranslationService;
import com.recipebookpro.data.remote.MLKitTranslationService;
import com.recipebookpro.domain.usecase.TranslateRecipeUseCase;
import com.google.mlkit.nl.translate.TranslateLanguage;

public class RecipeDetailActivity extends BaseActivity {

    public static final String EXTRA_RECIPE = "extra_recipe";

    private Recipe recipe;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    
    private ArrayList<String> userAllergens = new ArrayList<>();
    private boolean isLiked = false;
    private MenuItem likeMenuItem;
    private ListenerRegistration likeStateListener;
    private TranslationService translationService;
    private TranslateRecipeUseCase translateRecipeUseCase;

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
        
        translationService = new MLKitTranslationService(this);
        translateRecipeUseCase = new TranslateRecipeUseCase(translationService);
        findViewById(R.id.btnCloseTranslation).setOnClickListener(v -> {
            findViewById(R.id.cardTranslation).setVisibility(View.GONE);
        });

        findViewById(R.id.btnRevertTranslation).setOnClickListener(v -> revertTranslation());
        // Automatic translation if needed
        // Always call translateRecipe; the UseCase will independently identify 
        // the language and decide if translation is needed based on current app language.
        translateRecipe();
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
                Toast.makeText(this, R.string.recipe_not_found, Toast.LENGTH_SHORT).show();
                finish();
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(this, R.string.connection_error, Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbarDetail);
        String currentLang = Locale.getDefault().getLanguage();
        toolbar.setTitle(recipe.getDisplayTitle(currentLang));

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
            } else if (itemId == R.id.action_translate) {
                translateRecipe();
                return true;
            }
            return false;
        });

        likeMenuItem = toolbar.getMenu().findItem(R.id.action_like);

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
        ExtendedFloatingActionButton fabAdd = findViewById(R.id.fabAddToCookbook);
        fabAdd.setOnClickListener(v -> {
            CookbookPickerBottomSheet bottomSheet = CookbookPickerBottomSheet.newInstance(recipe);
            bottomSheet.show(getSupportFragmentManager(), "CookbookPickerBottomSheet");
        });

        ExtendedFloatingActionButton fabCook = findViewById(R.id.fabCookingMode);
        if (recipe != null && recipe.getStepList() != null && !recipe.getStepList().isEmpty()) {
            fabCook.setVisibility(View.VISIBLE);
            fabCook.setOnClickListener(v -> {
                Intent intent = new Intent(this, CookingModeActivity.class);
                intent.putExtra("extra_recipe", recipe);
                startActivity(intent);
            });
        } else {
            fabCook.setVisibility(View.GONE);
        }
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
        boolean shouldLike = !isLiked;
        String uid = currentUser.getUid();
        String recipeId = recipe.getId();
        if (TextUtils.isEmpty(recipeId)) return;

        item.setEnabled(false);
        db.collection("users").document(uid)
                .update("likedRecipeIds", shouldLike
                        ? FieldValue.arrayUnion(recipeId)
                        : FieldValue.arrayRemove(recipeId))
                .addOnSuccessListener(unused -> {
                    db.collection("recipes").document(recipeId)
                            .update("likes", FieldValue.increment(shouldLike ? 1 : -1))
                            .addOnCompleteListener(task -> item.setEnabled(true));
                    Toast.makeText(this, shouldLike ? getString(R.string.liked) : getString(R.string.like_removed), Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    item.setEnabled(true);
                    Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show();
                });
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

    private void addToShoppingList() {
        if (currentUser == null) return;
        
        String listName = getString(R.string.shopping_list_for_recipe, recipe.getTitle());
        
        Data inputData = new Data.Builder()
            .putString(MergeIngredientsWorker.KEY_USER_ID, currentUser.getUid())
            .putStringArray(MergeIngredientsWorker.KEY_RECIPE_IDS, new String[]{recipe.getId()})
            .putString(MergeIngredientsWorker.KEY_LIST_NAME, listName)
            .build();
            
        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(MergeIngredientsWorker.class)
            .setInputData(inputData)
            .build();
            
        WorkManager.getInstance(this).enqueue(workRequest);
        Toast.makeText(this, R.string.shopping_ingredients_preparing, Toast.LENGTH_SHORT).show();
    }

    private void translateRecipe() {
        if (recipe == null) return;
        
        findViewById(R.id.pbTranslation).setVisibility(View.VISIBLE);
        ((android.widget.TextView) findViewById(R.id.tvTranslatedContent)).setText(R.string.downloading_model);
        
        String targetLang = com.recipebookpro.presentation.ui.LocaleHelper.getLanguage(this);
        translateRecipeUseCase.execute(recipe, targetLang, new TranslationService.TranslationCallback() {
            @Override
            public void onSuccess(String message) {
                findViewById(R.id.pbTranslation).setVisibility(View.GONE);
                findViewById(R.id.btnRevertTranslation).setVisibility(View.VISIBLE);
                ((android.widget.TextView) findViewById(R.id.tvTranslatedContent)).setText(R.string.translation_completed);
                
                // Refresh all views with the new translated data
                setupToolbar();
                setupViewPagerAndTabs();
                
                // Update original language in Firestore for future persistence
                FirebaseFirestore.getInstance().collection("recipes").document(recipe.getId())
                        .update("originalLanguage", recipe.getOriginalLanguage());
            }

            @Override
            public void onFailure(Exception e) {
                findViewById(R.id.pbTranslation).setVisibility(View.GONE);
                Toast.makeText(RecipeDetailActivity.this, getString(R.string.error_with_reason, e.getMessage()), Toast.LENGTH_LONG).show();
            }

            @Override
            public void onDownloadProgress(String message) {
                runOnUiThread(() -> {
                    findViewById(R.id.cardTranslation).setVisibility(View.VISIBLE);
                    findViewById(R.id.pbTranslation).setVisibility(View.VISIBLE);
                    TextView tvContent = findViewById(R.id.tvTranslatedContent);
                    tvContent.setText(message);
                    findViewById(R.id.tvTranslatedTitle).setVisibility(View.GONE);
                });
            }
        });
    }

    private void showTranslation(String title, String content) {
        View card = findViewById(R.id.cardTranslation);
        card.setVisibility(View.VISIBLE);
        ((TextView) findViewById(R.id.tvTranslatedTitle)).setText(title);
        ((TextView) findViewById(R.id.tvTranslatedContent)).setText(content);
        
        // Localize card labels
        TextView tvLabel = card.findViewById(R.id.tvTranslationLabel);
        if (tvLabel != null) tvLabel.setText(R.string.translation_title);
        
        // Scroll to the top of the card if needed, or just let the user see it
        AppBarLayout appBarLayout = findViewById(R.id.appBarLayout);
        if (appBarLayout != null) {
            appBarLayout.setExpanded(true, true);
        }
    }

    private void revertTranslation() {
        if (recipe != null) {
            recipe.clearAllTranslations();
            setupToolbar();
            setupViewPagerAndTabs();
            findViewById(R.id.cardTranslation).setVisibility(View.GONE);
            
            // Also update Firestore to clear the translations permanently
            saveTranslatedToFirestore();
        }
    }

    private void saveTranslatedToFirestore() {
        if (recipe == null || TextUtils.isEmpty(recipe.getId())) return;
        
        db.collection("recipes").document(recipe.getId())
                .set(recipe) // Save the entire updated object to ensure all translated fields are persisted
                .addOnFailureListener(e -> {
                    // Silently fail or log, not critical for user experience
                });
    }

    @Override
    protected void onStart() {
        super.onStart();
        subscribeLikeState();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (likeStateListener != null) {
            likeStateListener.remove();
            likeStateListener = null;
        }
    }

    private void subscribeLikeState() {
        if (currentUser == null || recipe == null || TextUtils.isEmpty(recipe.getId())) return;
        if (likeStateListener != null) {
            likeStateListener.remove();
        }
        likeStateListener = db.collection("users").document(currentUser.getUid())
                .addSnapshotListener((doc, e) -> {
                    if (e != null || doc == null || !doc.exists()) return;
                    List<String> likedIds = (List<String>) doc.get("likedRecipeIds");
                    boolean newState = likedIds != null && likedIds.contains(recipe.getId());
                    isLiked = newState;
                    if (likeMenuItem != null) {
                        likeMenuItem.setIcon(isLiked ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
                    }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (translationService != null) {
            translationService.close();
        }
    }
}
