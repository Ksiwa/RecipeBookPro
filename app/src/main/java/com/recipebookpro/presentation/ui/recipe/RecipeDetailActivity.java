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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
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
import com.recipebookpro.presentation.share.PublicShareIntentHelper;
import com.recipebookpro.presentation.ui.recipe.adapter.RecipeDetailPagerAdapter;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import com.recipebookpro.data.worker.MergeIngredientsWorker;
import com.recipebookpro.presentation.ui.cooking.CookingModeActivity;
import com.recipebookpro.presentation.ui.kitchen.PublicProfileActivity;

import java.util.ArrayList;
import java.util.List;

import coil.Coil;
import coil.request.ImageRequest;
import com.recipebookpro.domain.service.TranslationService;
import com.recipebookpro.data.remote.MLKitTranslationService;
import com.recipebookpro.domain.usecase.TranslateRecipeUseCase;

public class RecipeDetailActivity extends BaseActivity {

    public static final String EXTRA_RECIPE = "extra_recipe";

    private Recipe recipe;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    
    private ArrayList<String> userAllergens = new ArrayList<>();
    private List<String> userHealthConditions = new ArrayList<>();
    private List<String> userCustomHealthConditions = new ArrayList<>();
    private List<com.recipebookpro.domain.model.LocalizedText> activeCustomHealthConditionsI18n = new ArrayList<>();
    private java.util.Map<String, java.util.List<String>> userHealthTriggers = new java.util.HashMap<>();
    private java.util.Map<String, String> customAllergenTranslations = new java.util.HashMap<>();
    
    private RecipeDetailViewModel viewModel;
    
    private boolean isLiked = false;
    private boolean healthWarningExpanded = false;
    private MenuItem likeMenuItem;
    private ListenerRegistration likeStateListener;
    private TranslationService translationService;
    private TranslateRecipeUseCase translateRecipeUseCase;
    private TabLayoutMediator recipeTabLayoutMediator;
    private boolean firstResume = true;
    private List<String> currentRiskyIngredients = new ArrayList<>();
    private List<String> currentRiskyMatchTerms = new ArrayList<>();

    public List<String> getRiskyIngredients() {
        return currentRiskyIngredients;
    }

    public List<String> getRiskyMatchTerms() {
        return currentRiskyMatchTerms;
    }

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
        
        viewModel = new ViewModelProvider(this).get(RecipeDetailViewModel.class);

        Runnable afterRecipeReady = () -> {
            setupToolbar();
            loadUserAllergensAndSetupPager();
            setupFAB();
            setupOwnerCard();

            translationService = new MLKitTranslationService(this);
            translateRecipeUseCase = new TranslateRecipeUseCase(translationService);
            findViewById(R.id.btnCloseTranslation).setOnClickListener(v -> {
                findViewById(R.id.cardTranslation).setVisibility(View.GONE);
            });

            findViewById(R.id.btnRevertTranslation).setOnClickListener(v -> revertTranslation());
        };

        String recipeId = recipe.getId();
        if (!TextUtils.isEmpty(recipeId)) {
            db.collection("recipes").document(recipeId)
                    .get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            recipe = Recipe.fromDocument(doc);
                        }
                        afterRecipeReady.run();
                    })
                    .addOnFailureListener(e -> afterRecipeReady.run());
        } else {
            afterRecipeReady.run();
        }
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
                setupOwnerCard();

                translationService = new MLKitTranslationService(RecipeDetailActivity.this);
                translateRecipeUseCase = new TranslateRecipeUseCase(translationService);
                findViewById(R.id.btnCloseTranslation).setOnClickListener(v -> {
                    findViewById(R.id.cardTranslation).setVisibility(View.GONE);
                });
                findViewById(R.id.btnRevertTranslation).setOnClickListener(v -> revertTranslation());
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
                translateRecipe(() -> checkHealthConditions());
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
        // Default placeholder state
        ivRecipeCover.setScaleType(ImageView.ScaleType.FIT_CENTER);
        ivRecipeCover.setImageResource(R.drawable.ic_cook);
        ivRecipeCover.setPadding(0, 0, 0, 0);
        
        android.util.TypedValue typedValue = new android.util.TypedValue();
        getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimaryContainer, typedValue, true);
        ivRecipeCover.setBackgroundColor(typedValue.data);
        getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnPrimaryContainer, typedValue, true);
        ivRecipeCover.setImageTintList(android.content.res.ColorStateList.valueOf(typedValue.data));

        if (!TextUtils.isEmpty(recipe.getImageUrl())) {
            ImageRequest request = new ImageRequest.Builder(this)
                .data(recipe.getImageUrl())
                .target(new coil.target.Target() {
                    @Override
                    public void onStart(@Nullable android.graphics.drawable.Drawable placeholder) {}

                    @Override
                    public void onSuccess(@NonNull android.graphics.drawable.Drawable result) {
                        ivRecipeCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        ivRecipeCover.setBackground(null);
                        ivRecipeCover.setImageTintList(null);
                        ivRecipeCover.setImageDrawable(result);
                    }

                    @Override
                    public void onError(@Nullable android.graphics.drawable.Drawable error) {
                        // Keep the placeholder state
                    }
                })
                .build();
            Coil.imageLoader(this).enqueue(request);
        }
    }

    private void loadUserAllergensAndSetupPager() {
        viewModel.getUserData(currentUser).observe(this, userData -> {
            userAllergens.clear();
            userHealthConditions.clear();
            userCustomHealthConditions.clear();
            activeCustomHealthConditionsI18n.clear();
            userHealthTriggers.clear();
            customAllergenTranslations.clear();

            if (userData != null) {
                if (userData.getHealthConditions() != null) userHealthConditions.addAll(userData.getHealthConditions());
                if (userData.getCustomHealthConditionsForLang(
                        com.recipebookpro.presentation.ui.LocaleHelper.getLanguage(RecipeDetailActivity.this)) != null) {
                    userCustomHealthConditions.addAll(userData.getCustomHealthConditionsForLang(
                            com.recipebookpro.presentation.ui.LocaleHelper.getLanguage(RecipeDetailActivity.this)));
                }
                if (userData.resolveActiveCustomHealthConditionsI18n() != null) {
                    activeCustomHealthConditionsI18n.addAll(userData.resolveActiveCustomHealthConditionsI18n());
                }
                userHealthTriggers.putAll(userData.getActiveHealthTriggers());

                // Merge built-in triggers for static health conditions
                for (String condition : userHealthConditions) {
                    List<String> builtIn = com.recipebookpro.domain.model.BuiltInHealthTriggers.getTriggersFor(condition);
                    if (builtIn != null) {
                        userHealthTriggers.put(condition, builtIn);
                    }
                }

                userAllergens.addAll(userCustomHealthConditions);
            }

            checkAllergens();
            setupViewPagerAndTabs();

            String uiLang = com.recipebookpro.presentation.ui.LocaleHelper.getLanguage(RecipeDetailActivity.this);
            String recipeLang = detectRecipeLanguage(recipe);

            if (!uiLang.equals(recipeLang)) {
                translateRecipe(() -> checkHealthConditions());
            } else {
                checkHealthConditions();
            }

            // Detect and fix Firestore dirty state if Room has 0 active chips (optimized with SharedPreferences)
            if (currentUser != null && userHealthConditions.isEmpty() && activeCustomHealthConditionsI18n.isEmpty()) {
                android.content.SharedPreferences prefs = getSharedPreferences("HealthCheckPrefs", MODE_PRIVATE);
                boolean isSanitized = prefs.getBoolean("firestore_sanitized_v1", false);
                if (!isSanitized) {
                    final String uid = currentUser.getUid();
                    com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("users").document(uid).get()
                        .addOnSuccessListener(doc -> {
                            if (doc.exists()) {
                                java.util.List<?> firestoreHealth = (java.util.List<?>) doc.get("healthConditions");
                                java.util.List<?> firestoreActiveKeys = (java.util.List<?>) doc.get("activeCustomHealthConditionKeys");
                                
                                boolean firestoreIsDirty = (firestoreHealth != null && !firestoreHealth.isEmpty()) || 
                                                           (firestoreActiveKeys != null && !firestoreActiveKeys.isEmpty());
                                
                                if (firestoreIsDirty) {
                                    // Firestore has dirty data but Room has 0 active chips!
                                    // Force overwrite Firestore with empty values to sanitize it
                                    java.util.Map<String, Object> forceCleanUpdates = new java.util.HashMap<>();
                                    forceCleanUpdates.put("healthConditions", new java.util.ArrayList<>());
                                    forceCleanUpdates.put("customHealthConditions", new java.util.ArrayList<>());
                                    forceCleanUpdates.put("customHealthConditionsI18n", new java.util.ArrayList<>());
                                    forceCleanUpdates.put("activeCustomHealthConditionKeys", new java.util.ArrayList<>());
                                    forceCleanUpdates.put("healthTriggers", new java.util.HashMap<>());
                                    forceCleanUpdates.put("healthWarningTemplates", new java.util.HashMap<>());
                                    
                                    com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                        .collection("users").document(uid).update(forceCleanUpdates)
                                        .addOnSuccessListener(aVoid -> {
                                            prefs.edit().putBoolean("firestore_sanitized_v1", true).apply();
                                            // Re-run health check with clean empty profile
                                            userAllergens.clear();
                                            userHealthConditions.clear();
                                            userCustomHealthConditions.clear();
                                            activeCustomHealthConditionsI18n.clear();
                                            userHealthTriggers.clear();
                                            checkAllergens();
                                            String uiLangInner = com.recipebookpro.presentation.ui.LocaleHelper.getLanguage(RecipeDetailActivity.this);
                                            String recipeLangInner = detectRecipeLanguage(recipe);
                                            if (!uiLangInner.equals(recipeLangInner)) {
                                                translateRecipe(() -> checkHealthConditions());
                                            } else {
                                                checkHealthConditions();
                                            }
                                        });
                                } else {
                                    // Already clean, mark true to prevent subsequent checks
                                    prefs.edit().putBoolean("firestore_sanitized_v1", true).apply();
                                }
                            } else {
                                prefs.edit().putBoolean("firestore_sanitized_v1", true).apply();
                            }
                        })
                        .addOnFailureListener(e -> {
                            // Don't mark true, so we can retry on next connection
                        });
                }
            }
        });
    }

    private void checkAllergens() {
        if (userAllergens.isEmpty() || recipe.getAllergens().isEmpty()) return;

        // Canonical alias map: any TR or EN label → lowercase EN key used for matching
        java.util.Map<String, String> canonicalMap = new java.util.HashMap<>();
        canonicalMap.put("gluten",        "gluten");
        canonicalMap.put("glüten",        "gluten");
        canonicalMap.put("dairy",         "dairy");
        canonicalMap.put("süt ürünleri",  "dairy");
        canonicalMap.put("milk",          "dairy");
        canonicalMap.put("süt",           "dairy");
        canonicalMap.put("egg",           "egg");
        canonicalMap.put("yumurta",       "egg");
        canonicalMap.put("nuts",          "nuts");
        canonicalMap.put("kuruyemiş",     "nuts");
        canonicalMap.put("nut",           "nuts");
        canonicalMap.put("fıstık",        "nuts");
        canonicalMap.put("peanut",        "nuts");
        canonicalMap.put("soy",           "soy");
        canonicalMap.put("soya",          "soy");
        canonicalMap.put("seafood",       "seafood");
        canonicalMap.put("deniz ürünleri","seafood");
        canonicalMap.put("fish",          "seafood");
        canonicalMap.put("balık",         "seafood");
        canonicalMap.put("sesame",        "sesame");
        canonicalMap.put("susam",         "sesame");
        canonicalMap.put("celery",        "celery");
        canonicalMap.put("kereviz",       "celery");

        // Merge saved translations for custom allergens into the canonical map
        // e.g. {"fıstık" → "pistachio"} means both map to the same canonical key
        for (com.recipebookpro.domain.model.LocalizedText lt : activeCustomHealthConditionsI18n) {
            String tr = lt.getTr() != null ? lt.getTr().toLowerCase(java.util.Locale.ROOT).trim() : "";
            String en = lt.getEn() != null ? lt.getEn().toLowerCase(java.util.Locale.ROOT).trim() : "";
            String key = lt.getKey().toLowerCase(java.util.Locale.ROOT).trim();
            if (!tr.isEmpty()) {
                canonicalMap.putIfAbsent(tr, key);
            }
            if (!en.isEmpty()) {
                canonicalMap.putIfAbsent(en, key);
            }
        }

        List<String> combinedAllergySearches = new ArrayList<>(userAllergens);
        for (String cond : userHealthConditions) {
            String condLower = cond.toLowerCase(java.util.Locale.ROOT).trim();
            if (condLower.contains("celiac") || condLower.contains("çölyak")) {
                if (!combinedAllergySearches.contains("gluten")) combinedAllergySearches.add("gluten");
                if (!combinedAllergySearches.contains("glüten")) combinedAllergySearches.add("glüten");
            }
        }

        List<String> matchingAllergens = new ArrayList<>();
        String currentLang = com.recipebookpro.presentation.ui.LocaleHelper.getLanguage(this);
        List<String> displayAllergens = recipe.getDisplayAllergens(currentLang);
        List<String> originalAllergens = recipe.getAllergens();

        // Layer 3 preparation: full ingredient text for custom allergen searches
        String ingredientsText = recipe.getFormattedIngredients() != null
                ? recipe.getFormattedIngredients().toLowerCase(java.util.Locale.ROOT) : "";

        for (String userAllergen : combinedAllergySearches) {
            String userAllergenLower = userAllergen.toLowerCase(java.util.Locale.ROOT).trim();
            String userKey = canonicalMap.getOrDefault(userAllergenLower, userAllergenLower);
            boolean matched = false;

            // Layer 1: canonical map + original allergen tags
            for (String recipeAllergen : originalAllergens) {
                String recipeKey = canonicalMap.getOrDefault(recipeAllergen.toLowerCase().trim(), recipeAllergen.toLowerCase().trim());
                if (userKey.equals(recipeKey) || userAllergen.equalsIgnoreCase(recipeAllergen)) {
                    matchingAllergens.add(recipeAllergen);
                    matched = true;
                    break;
                }
            }

            // Layer 2: translated/display allergen tags
            if (!matched && displayAllergens != null) {
                for (String translatedAllergen : displayAllergens) {
                    String transKey = canonicalMap.getOrDefault(translatedAllergen.toLowerCase().trim(), translatedAllergen.toLowerCase().trim());
                    if (userKey.equals(transKey)
                            || userAllergen.equalsIgnoreCase(translatedAllergen)
                            || translatedAllergen.toLowerCase().contains(userAllergenLower)
                            || userAllergenLower.contains(translatedAllergen.toLowerCase())) {
                        matchingAllergens.add(translatedAllergen);
                        matched = true;
                        break;
                    }
                }
            }

            // Layer 3: custom allergen — search directly in recipe ingredient text.
            // Catches user-typed words like "fıstık" or "pistachio" that appear in
            // ingredient names but not in the recipe's allergen tag list.
            if (!matched && !ingredientsText.isEmpty() && userAllergenLower.length() >= 3) {
                if (ingredientsText.contains(userAllergenLower)) {
                    matchingAllergens.add(userAllergen);
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

        if (recipeTabLayoutMediator != null) {
            recipeTabLayoutMediator.detach();
            recipeTabLayoutMediator = null;
        }

        RecipeDetailPagerAdapter pagerAdapter = new RecipeDetailPagerAdapter(this, recipe, userAllergens);
        viewPager.setAdapter(pagerAdapter);

        String[] tabTitles = {
            getString(R.string.tab_ingredients),
            getString(R.string.tab_steps),
            getString(R.string.tab_notes)
        };

        recipeTabLayoutMediator = new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(tabTitles[position])
        );
        recipeTabLayoutMediator.attach();
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
        if (recipe == null) return;
        startActivity(PublicShareIntentHelper.createRecipeShareChooserIntent(this, recipe));
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
            .putString(MergeIngredientsWorker.KEY_TARGET_LANGUAGE, com.recipebookpro.presentation.ui.LocaleHelper.getLanguage(this))
            .build();
            
        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(MergeIngredientsWorker.class)
            .setInputData(inputData)
            .build();
            
        WorkManager.getInstance(this).enqueue(workRequest);
        Toast.makeText(this, R.string.shopping_ingredients_preparing, Toast.LENGTH_SHORT).show();
    }

    private void translateRecipe() {
        translateRecipe(null);
    }

    private void translateRecipe(final Runnable onComplete) {
        if (recipe == null) {
            if (onComplete != null) onComplete.run();
            return;
        }
        
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

                if (onComplete != null) {
                    onComplete.run();
                }
            }

            @Override
            public void onFailure(Exception e) {
                findViewById(R.id.pbTranslation).setVisibility(View.GONE);
                Toast.makeText(RecipeDetailActivity.this, getString(R.string.error_with_reason, e.getMessage()), Toast.LENGTH_LONG).show();
                if (onComplete != null) {
                    onComplete.run();
                }
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

    private void setupOwnerCard() {
        if (recipe == null || TextUtils.isEmpty(recipe.getUserId())) return;
        
        View card = findViewById(R.id.cardOwnerInfo);
        TextView tvOwner = findViewById(R.id.tvOwnerName);
        ImageView ivAvatar = findViewById(R.id.ivOwnerAvatar);
        
        db.collection("users").document(recipe.getUserId()).get()
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
                          Intent intent = new Intent(RecipeDetailActivity.this, PublicProfileActivity.class);
                          intent.putExtra(PublicProfileActivity.EXTRA_USER_ID, finalUid);
                          startActivity(intent);
                      });
                  }
              }
          });
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
    protected void onResume() {
        super.onResume();
        if (firstResume) {
            firstResume = false;
        } else {
            refreshRecipeFromFirestore();
        }
    }

    private void refreshRecipeFromFirestore() {
        if (recipe == null || TextUtils.isEmpty(recipe.getId()) || isFinishing()) {
            return;
        }
        db.collection("recipes").document(recipe.getId())
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists() || isFinishing()) {
                        return;
                    }
                    recipe = Recipe.fromDocument(doc);
                    setupToolbar();
                    setupViewPagerAndTabs();
                    checkAllergens();
                    setupFAB();
                    String uiLang = com.recipebookpro.presentation.ui.LocaleHelper.getLanguage(RecipeDetailActivity.this);
                    String recipeLang = detectRecipeLanguage(recipe);
                    if (!uiLang.equals(recipeLang)) {
                        translateRecipe(() -> checkHealthConditions());
                    } else {
                        checkHealthConditions();
                    }
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
        if (recipeTabLayoutMediator != null) {
            recipeTabLayoutMediator.detach();
            recipeTabLayoutMediator = null;
        }
        if (translationService != null) {
            translationService.close();
        }
        super.onDestroy();
    }



    private void checkHealthConditions() {
        MaterialCardView cardHealth = findViewById(R.id.cardHealthWarning);
        TextView tvHealthText = findViewById(R.id.tvHealthWarningText);
        ImageView ivIcon = findViewById(R.id.ivHealthWarningIcon);
        android.view.View llHeader = findViewById(R.id.llHealthWarningHeader);

        if (cardHealth == null || tvHealthText == null) return;

        if (currentUser == null) {
            cardHealth.setVisibility(View.GONE);
            return;
        }

        cardHealth.setVisibility(View.VISIBLE);

        boolean hasConditions = !userHealthConditions.isEmpty() 
                               || !userCustomHealthConditions.isEmpty()
                               || !userHealthTriggers.isEmpty();

        if (!hasConditions) {
            applyBannerSurface();
            tvHealthText.setText(R.string.health_warning_no_conditions);
            if (ivIcon != null) ivIcon.setImageResource(R.drawable.ic_cook);
            return;
        }

        // Setup expand toggle
        if (llHeader != null) {
            llHeader.setOnClickListener(v -> toggleHealthWarningExpand());
        }

        applyBannerSurface();
        tvHealthText.setText(R.string.health_warning_checking);
        if (ivIcon != null) ivIcon.setImageResource(R.drawable.ic_cook);

        com.recipebookpro.domain.repository.HealthCheckRepository repository = new com.recipebookpro.data.repository.HealthCheckRepositoryImpl();
        com.recipebookpro.domain.usecase.CheckRecipeSafetyUseCase useCase = new com.recipebookpro.domain.usecase.CheckRecipeSafetyUseCase(repository);
        String uiLang = com.recipebookpro.presentation.ui.LocaleHelper.getLanguage(this);
        useCase.execute(recipe, userHealthConditions, userCustomHealthConditions, new ArrayList<>(),
                userHealthTriggers, uiLang, new com.recipebookpro.domain.repository.HealthCheckRepository.HealthCheckCallback() {
            @Override
            public void onResult(String resultRecipeId, boolean isSafe, String rationale, List<String> riskyIngredients) {
                if (isDestroyed() || isFinishing() || !recipe.getId().equals(resultRecipeId)) return;
                deliverHealthCheckResult(isSafe, rationale, riskyIngredients);
            }

            @Override
            public void onError(String resultRecipeId, String errorMessage) {
                if (isDestroyed() || isFinishing() || !recipe.getId().equals(resultRecipeId)) return;
                applyBannerError();
                TextView tv = findViewById(R.id.tvHealthWarningText);
                if (tv != null) tv.setText(getString(R.string.health_warning_error));
                ImageView iv = findViewById(R.id.ivHealthWarningIcon);
                if (iv != null) iv.setImageResource(R.drawable.ic_remove);
            }
        });
    }

    private String detectRecipeLanguage(Recipe recipe) {
        return com.recipebookpro.util.RecipeLanguageDetector.detectFromRecipe(recipe);
    }

    private void deliverHealthCheckResult(boolean isSafe, String rationale, List<String> riskyIngredients) {
        if ((riskyIngredients == null || riskyIngredients.isEmpty()) && !isSafe) {
            String uiLang = com.recipebookpro.presentation.ui.LocaleHelper.getLanguage(this);
            riskyIngredients = com.recipebookpro.util.RiskyIngredientResolver.resolveFromRecipe(
                    recipe, userHealthConditions, userCustomHealthConditions, userHealthTriggers,
                    rationale, uiLang);
        }
        if (riskyIngredients == null || riskyIngredients.isEmpty()) {
            currentRiskyIngredients = new ArrayList<>();
            currentRiskyMatchTerms = new ArrayList<>();
            finalizeHealthCheck(isSafe, rationale, currentRiskyIngredients);
            return;
        }
        final List<String> originalLabels = new ArrayList<>(riskyIngredients);
        String uiLang = com.recipebookpro.presentation.ui.LocaleHelper.getLanguage(this);
        String recipeLang = detectRecipeLanguage(recipe);

        com.recipebookpro.util.RiskyIngredientLocaleHelper.ensureLanguage(this, originalLabels, uiLang, uiLabels -> {
            if (isDestroyed() || isFinishing()) return;
            currentRiskyIngredients = uiLabels;

            com.recipebookpro.util.RiskyIngredientLocaleHelper.ensureLanguage(RecipeDetailActivity.this, originalLabels, recipeLang, recipeLabels -> {
                if (isDestroyed() || isFinishing()) return;

                List<String> allLabels = new ArrayList<>(originalLabels);
                for (String label : uiLabels) {
                    if (!allLabels.contains(label)) {
                        allLabels.add(label);
                    }
                }
                for (String label : recipeLabels) {
                    if (!allLabels.contains(label)) {
                        allLabels.add(label);
                    }
                }

                currentRiskyMatchTerms = com.recipebookpro.util.RiskyIngredientMatcher.buildMatchTerms(recipe, allLabels);
                finalizeHealthCheck(isSafe, rationale, currentRiskyIngredients);
            });
        });
    }

    private void finalizeHealthCheck(boolean isSafe, String rationale, List<String> riskyIngredients) {
        applyHealthWarningState(isSafe, rationale, riskyIngredients);

        // Notify ViewModel instead of fragile fragment tag
        if (viewModel != null) {
            viewModel.setRiskyMatchTerms(currentRiskyMatchTerms);
        }
    }

    private void toggleHealthWarningExpand() {
        android.view.View expanded = findViewById(R.id.llHealthWarningExpanded);
        ImageView chevron = findViewById(R.id.ivHealthWarningChevron);
        if (expanded == null) return;
        healthWarningExpanded = !healthWarningExpanded;
        expanded.setVisibility(healthWarningExpanded ? View.VISIBLE : View.GONE);
        if (chevron != null) {
            chevron.animate().rotation(healthWarningExpanded ? 180f : 0f).setDuration(200).start();
        }
    }

    private void applyHealthWarningState(boolean isSafe, String rationale, List<String> riskyIngredients) {
        TextView tvSummary = findViewById(R.id.tvHealthWarningText);
        TextView tvRationale = findViewById(R.id.tvHealthWarningRationale);
        TextView tvDisclaimer = findViewById(R.id.tvHealthWarningDisclaimer);
        ImageView ivIcon = findViewById(R.id.ivHealthWarningIcon);
        ImageView ivChevron = findViewById(R.id.ivHealthWarningChevron);
        MaterialCardView card = findViewById(R.id.cardHealthWarning);
        if (tvSummary == null || card == null) return;

        // --- Pick colors ---
        int bgAttr, fgAttr;
        if (isSafe) {
            // Green: use a custom green background
            bgAttr = -1;  // handled manually below
            fgAttr = -1;
        } else {
            bgAttr = com.google.android.material.R.attr.colorErrorContainer;
            fgAttr = com.google.android.material.R.attr.colorOnErrorContainer;
        }

        android.util.TypedValue tv = new android.util.TypedValue();
        int fgColor;
        if (isSafe) {
            // Soft green card background
            card.setCardBackgroundColor(android.graphics.Color.parseColor("#D0F0C0"));
            fgColor = android.graphics.Color.parseColor("#1B5E20");
        } else {
            getTheme().resolveAttribute(com.google.android.material.R.attr.colorErrorContainer, tv, true);
            card.setCardBackgroundColor(tv.data);
            getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnErrorContainer, tv, true);
            fgColor = tv.data;
        }

        // Apply label color
        tvSummary.setTextColor(fgColor);
        if (tvDisclaimer != null) tvDisclaimer.setTextColor(fgColor);
        if (tvRationale != null) tvRationale.setTextColor(fgColor);

        // --- Summary label (always visible, locale-aware) ---
        tvSummary.setText(isSafe ? getString(R.string.health_warning_safe) : getString(R.string.health_warning_unsafe));

        // --- Icon ---
        if (ivIcon != null) {
            ivIcon.setImageResource(isSafe ? R.drawable.ic_cook : R.drawable.ic_remove);
            ivIcon.setColorFilter(fgColor);
        }

        // --- Rationale in the expanded view ---
        if (tvRationale != null) {
            StringBuilder rationaleBuilder = new StringBuilder();
            if (!TextUtils.isEmpty(rationale)) {
                rationaleBuilder.append(rationale);
            }
            // Append risky ingredients if available
            if (riskyIngredients != null && !riskyIngredients.isEmpty()) {
                if (rationaleBuilder.length() > 0) rationaleBuilder.append("\n\n");
                rationaleBuilder.append(getString(R.string.risky_ingredients_label,
                        TextUtils.join(", ", riskyIngredients)));
            }
            if (rationaleBuilder.length() > 0) {
                tvRationale.setText(rationaleBuilder.toString());
                // Show chevron to indicate expandability
                if (ivChevron != null) {
                    ivChevron.setVisibility(View.VISIBLE);
                    ivChevron.setColorFilter(fgColor);
                }
            } else {
                tvRationale.setText("");
                if (ivChevron != null) ivChevron.setVisibility(View.GONE);
            }
        }
    }

    private void applyBannerSurface() {
        MaterialCardView card = findViewById(R.id.cardHealthWarning);
        if (card == null) return;
        android.util.TypedValue tv = new android.util.TypedValue();
        getTheme().resolveAttribute(com.google.android.material.R.attr.colorSurfaceVariant, tv, true);
        card.setCardBackgroundColor(tv.data);
        getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, tv, true);
        int fg = tv.data;
        TextView tvText = findViewById(R.id.tvHealthWarningText);
        TextView tvDisc = findViewById(R.id.tvHealthWarningDisclaimer);
        ImageView ivIcon = findViewById(R.id.ivHealthWarningIcon);
        if (tvText != null) tvText.setTextColor(fg);
        if (tvDisc != null) tvDisc.setTextColor(fg);
        if (ivIcon != null) ivIcon.setColorFilter(fg);
    }

    private void applyBannerError() {
        MaterialCardView card = findViewById(R.id.cardHealthWarning);
        if (card == null) return;
        android.util.TypedValue tv = new android.util.TypedValue();
        getTheme().resolveAttribute(com.google.android.material.R.attr.colorErrorContainer, tv, true);
        card.setCardBackgroundColor(tv.data);
        getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnErrorContainer, tv, true);
        int fg = tv.data;
        TextView tvText = findViewById(R.id.tvHealthWarningText);
        TextView tvDisc = findViewById(R.id.tvHealthWarningDisclaimer);
        ImageView ivIcon = findViewById(R.id.ivHealthWarningIcon);
        if (tvText != null) tvText.setTextColor(fg);
        if (tvDisc != null) tvDisc.setTextColor(fg);
        if (ivIcon != null) ivIcon.setColorFilter(fg);
    }
}
