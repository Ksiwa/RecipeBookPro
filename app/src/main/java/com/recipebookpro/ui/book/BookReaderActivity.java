package com.recipebookpro.ui.book;

import android.os.Bundle;
import android.view.View;

import com.recipebookpro.ui.BaseActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textview.MaterialTextView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.recipebookpro.R;
import com.recipebookpro.model.Cookbook;
import com.recipebookpro.model.Recipe;
import com.recipebookpro.ui.recipe.StickerSelectorBottomSheet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class BookReaderActivity extends BaseActivity {

    public static final String EXTRA_COOKBOOK_ID = "EXTRA_COOKBOOK_ID";
    public static final String EXTRA_COOKBOOK_NAME = "EXTRA_COOKBOOK_NAME";

    private ViewPager2 viewPager;
    private MaterialTextView tvEmpty;
    private android.widget.ProgressBar progressBar;
    private final List<Recipe> recipeList = new ArrayList<>();
    private String userIdentity = "Kullanıcı";
    private View rootView;
    private String filterCookbookId;
    private String filterCookbookName;
    private java.util.Map<String, java.util.List<String>> recipeToCookbookMap = new java.util.HashMap<>();
    private android.widget.LinearLayout layoutEditActions;
    private android.widget.LinearLayout layoutNormalActions;
    private boolean isEditMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book_reader);

        applyInsetsToView(findViewById(R.id.bookReaderRoot));

        rootView = findViewById(R.id.bookReaderRoot);
        android.widget.ImageButton btnBack = findViewById(R.id.btnBookBack);
        android.widget.ImageButton btnToc = findViewById(R.id.btnBookToc);
        viewPager = findViewById(R.id.viewPagerBook);
        tvEmpty = findViewById(R.id.tvBookEmpty);
        progressBar = findViewById(R.id.progressBook);

        filterCookbookId = getIntent().getStringExtra(EXTRA_COOKBOOK_ID);
        filterCookbookName = getIntent().getStringExtra(EXTRA_COOKBOOK_NAME);

        layoutNormalActions = findViewById(R.id.layoutNormalRoot);
        layoutEditActions = findViewById(R.id.layoutEditActions);

        btnBack.setOnClickListener(v -> finish());
        btnToc.setOnClickListener(v -> goToToc());
        
        android.view.View btnEdit = findViewById(R.id.btnBookStickers);
        btnEdit.setVisibility(View.GONE); // Default hidden until authorized
        btnEdit.setOnClickListener(v -> toggleEditMode(true));
        
        findViewById(R.id.btnEditCancel).setOnClickListener(v -> {
            toggleEditMode(false);
            android.widget.Toast.makeText(this, "Düzenleme iptal edildi", android.widget.Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.btnEditAdd).setOnClickListener(v -> showStickerSelector());
        findViewById(R.id.btnEditSave).setOnClickListener(v -> saveEdits());

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            userIdentity = user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty()
                    ? user.getDisplayName().trim()
                    : user.getEmail();
        }

        viewPager.setClipToPadding(false);
        viewPager.setClipChildren(false);
        viewPager.setOffscreenPageLimit(3);
        viewPager.setPageTransformer(new NotebookPageTransformer());

        View pagerChild = viewPager.getChildAt(0);
        if (pagerChild instanceof RecyclerView) {
            pagerChild.setOverScrollMode(View.OVER_SCROLL_NEVER);
        }

        loadRecipes();
    }

    public void goToToc() {
        if (viewPager.getVisibility() == View.VISIBLE) {
            viewPager.setCurrentItem(1, true);
        }
    }

    private void loadRecipes() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            showEmpty();
            return;
        }

        showLoading();

        if (filterCookbookId != null) {
            FirebaseFirestore.getInstance().collection("cookbooks").document(filterCookbookId)
                .get().addOnSuccessListener(doc -> {
                    Cookbook book = Cookbook.fromDocument(doc);
                    
                    // Yetki kontrolü: Sahip mi yoksa Ortak Çalışan mı?
                    boolean isAuthorized = user.getUid().equals(book.getUserId()) || 
                                           (book.getCollaboratorIds() != null && book.getCollaboratorIds().contains(user.getUid()));
                    
                    findViewById(R.id.btnBookStickers).setVisibility(isAuthorized ? View.VISIBLE : View.GONE);

                    // Defter sahibinin adını getir
                    FirebaseFirestore.getInstance().collection("users").document(book.getUserId())
                        .get().addOnSuccessListener(userDoc -> {
                            if (userDoc.exists()) {
                                String ownerName = userDoc.getString("displayName");
                                if (ownerName == null || ownerName.trim().isEmpty()) {
                                    ownerName = userDoc.getString("email");
                                }
                                if (ownerName != null) {
                                    userIdentity = ownerName;
                                }
                            }
                            
                            List<String> ids = book.getRecipeIds();
                            if (ids == null || ids.isEmpty()) {
                                onLoaded();
                                return;
                            }
                            fetchAllRecipesAndFilter(user.getUid(), ids);
                        }).addOnFailureListener(e -> {
                            // Kullanıcı çekilemezse mevcut isimle devam et
                            List<String> ids = book.getRecipeIds();
                            if (ids == null || ids.isEmpty()) {
                                onLoaded();
                                return;
                            }
                            fetchAllRecipesAndFilter(user.getUid(), ids);
                        });

                }).addOnFailureListener(e -> {
                    hideLoading();
                    showEmpty();
                });
        } else {
            // Belirli bir defter yoksa (Tüm Tariflerim), kullanıcı yetkilidir
            findViewById(R.id.btnBookStickers).setVisibility(View.VISIBLE);
            fetchAllRecipesAndFilter(user.getUid(), null);
        }
    }

    private void fetchAllRecipesAndFilter(String uid, List<String> allowedIds) {
        if (allowedIds == null || allowedIds.isEmpty()) {
            // "Tümü" modu: Önce tüm tarifleri çek, sonra defter eşleşmelerini bul
            FirebaseFirestore.getInstance().collection("recipes")
                    .whereEqualTo("userId", uid)
                    .get()
                    .addOnSuccessListener(recipeSnap -> {
                        recipeList.clear();
                        for (QueryDocumentSnapshot rDoc : recipeSnap) {
                            recipeList.add(Recipe.fromDocument(rDoc));
                        }

                        // Şimdi defterleri çekip eşleştirme yapalım (Süslemeler için)
                        FirebaseFirestore.getInstance().collection("cookbooks")
                                .whereEqualTo("userId", uid)
                                .get()
                                .addOnSuccessListener(cookbookSnap -> {
                                    recipeToCookbookMap.clear();
                                    for (QueryDocumentSnapshot cbDoc : cookbookSnap) {
                                        Cookbook book = Cookbook.fromDocument(cbDoc);
                                        if (book.getRecipeIds() != null) {
                                            for (String rid : book.getRecipeIds()) {
                                                List<String> books = recipeToCookbookMap.get(rid);
                                                if (books == null) {
                                                    books = new ArrayList<>();
                                                    recipeToCookbookMap.put(rid, books);
                                                }
                                                if (!books.contains(book.getId())) {
                                                    books.add(book.getId());
                                                }
                                            }
                                        }
                                    }
                                    Collections.sort(recipeList, (r1, r2) -> Long.compare(r1.getCreatedAt(), r2.getCreatedAt()));
                                    onLoaded();
                                })
                                .addOnFailureListener(e -> {
                                    Collections.sort(recipeList, (r1, r2) -> Long.compare(r1.getCreatedAt(), r2.getCreatedAt()));
                                    onLoaded();
                                });
                    })
                    .addOnFailureListener(e -> {
                        hideLoading();
                        showEmpty();
                    });
            return;
        }

        // Defter görünümü: Sadece izin verilen ID'leri getir
        recipeList.clear();
        List<com.google.android.gms.tasks.Task<QuerySnapshot>> tasks = new ArrayList<>();
        for (int i = 0; i < allowedIds.size(); i += 10) {
            List<String> chunk = allowedIds.subList(i, Math.min(allowedIds.size(), i + 10));
            tasks.add(FirebaseFirestore.getInstance().collection("recipes")
                    .whereIn(FieldPath.documentId(), chunk)
                    .get());
        }

        com.google.android.gms.tasks.Tasks.whenAllSuccess(tasks).addOnSuccessListener(results -> {
            for (Object result : results) {
                QuerySnapshot snap = (QuerySnapshot) result;
                for (DocumentSnapshot doc : snap.getDocuments()) {
                    recipeList.add(Recipe.fromDocument(doc));
                }
            }
            Collections.sort(recipeList, (r1, r2) -> Long.compare(r1.getCreatedAt(), r2.getCreatedAt()));
            onLoaded();
        }).addOnFailureListener(e -> {
            hideLoading();
            showEmpty();
        });
    }

    private void onLoaded() {
        hideLoading();
        if (recipeList.isEmpty()) {
            showEmpty();
            return;
        }

        showPager();
        viewPager.setAdapter(new BookPagerAdapter(this, recipeList, userIdentity, filterCookbookId, recipeToCookbookMap));
    }

    public void goToRecipePage(int position) {
        // +2 because of Cover and TOC pages
        viewPager.setCurrentItem(position + 2, true);
    }

    private void updateIndicator(int position) {
        // No-op. Page specific indicators are in the fragments.
    }

    private void showLoading() {
        progressBar.setVisibility(View.VISIBLE);
        viewPager.setVisibility(View.GONE);
        tvEmpty.setVisibility(View.GONE);
    }

    private void hideLoading() {
        progressBar.setVisibility(View.GONE);
    }

    private void showEmpty() {
        tvEmpty.setVisibility(View.VISIBLE);
        viewPager.setVisibility(View.GONE);
    }

    private void showPager() {
        viewPager.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
    }

    private void toggleEditMode(boolean enabled) {
        this.isEditMode = enabled;
        layoutNormalActions.setVisibility(enabled ? View.GONE : View.VISIBLE);
        layoutEditActions.setVisibility(enabled ? View.VISIBLE : View.GONE);
        viewPager.setUserInputEnabled(!enabled); // Disable swiping in edit mode

        // Tell the current fragment to enable/disable editing
        androidx.fragment.app.Fragment currentFrag = getSupportFragmentManager().findFragmentByTag("f" + viewPager.getCurrentItem());
        if (currentFrag instanceof RecipePageFragment) {
            ((RecipePageFragment) currentFrag).setEditMode(enabled);
        }
    }

    private void saveEdits() {
        androidx.fragment.app.Fragment currentFrag = getSupportFragmentManager().findFragmentByTag("f" + viewPager.getCurrentItem());
        if (currentFrag instanceof RecipePageFragment) {
            ((RecipePageFragment) currentFrag).performSave(() -> {
                toggleEditMode(false);
                android.widget.Toast.makeText(this, "Değişiklikler kaydedildi", android.widget.Toast.LENGTH_SHORT).show();
            });
        } else {
            toggleEditMode(false);
        }
    }

    private void showStickerSelector() {
        StickerSelectorBottomSheet bottomSheet = new StickerSelectorBottomSheet();
        bottomSheet.setOnStickerSelectedListener(imageUrl -> {
            androidx.fragment.app.Fragment currentFrag = getSupportFragmentManager().findFragmentByTag("f" + viewPager.getCurrentItem());
            if (currentFrag instanceof RecipePageFragment) {
                ((RecipePageFragment) currentFrag).addSticker(imageUrl);
            }
        });
        bottomSheet.show(getSupportFragmentManager(), "StickerSelector");
    }
}
