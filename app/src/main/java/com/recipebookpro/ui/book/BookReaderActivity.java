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
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.recipebookpro.R;
import com.recipebookpro.model.Cookbook;
import com.recipebookpro.model.Recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class BookReaderActivity extends BaseActivity {

    public static final String EXTRA_COOKBOOK_ID = "EXTRA_COOKBOOK_ID";
    public static final String EXTRA_COOKBOOK_NAME = "EXTRA_COOKBOOK_NAME";

    private ViewPager2 viewPager;
    private MaterialTextView tvEmpty;
    private android.widget.ProgressBar progressBar;
    private final List<Recipe> recipeList = new ArrayList<>();
    private String userIdentity = "";
    private View rootView;
    private String filterCookbookId;
    private String filterCookbookName;

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

        btnBack.setOnClickListener(v -> finish());
        btnToc.setOnClickListener(v -> goToToc());

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

    public void goToRecipePage(int recipeIndex) {
        viewPager.setCurrentItem(recipeIndex + 2, true);
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
                    List<String> ids = book.getRecipeIds();
                    if (ids == null || ids.isEmpty()) {
                        onLoaded();
                        return;
                    }
                    fetchAllRecipesAndFilter(user.getUid(), ids);
                }).addOnFailureListener(e -> {
                    hideLoading();
                    showEmpty();
                });
        } else {
            fetchAllRecipesAndFilter(user.getUid(), null);
        }
    }

    private void fetchAllRecipesAndFilter(String uid, List<String> allowedIds) {
        FirebaseFirestore.getInstance()
                .collection("recipes")
                .whereEqualTo("userId", uid)
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(snap -> {
                    recipeList.clear();
                    for (QueryDocumentSnapshot doc : snap) {
                        if (allowedIds == null || allowedIds.contains(doc.getId())) {
                            recipeList.add(Recipe.fromDocument(doc));
                        }
                    }
                    onLoaded();
                })
                .addOnFailureListener(e -> {
                    String message = e.getMessage() != null ? e.getMessage() : "";
                    if (message.contains("FAILED_PRECONDITION") || message.contains("index")) {
                        loadFallback(uid, allowedIds);
                    } else {
                        hideLoading();
                        showEmpty();
                        Snackbar.make(rootView, R.string.recipes_load_failed, Snackbar.LENGTH_LONG).show();
                    }
                });
    }

    private void loadFallback(String uid, List<String> allowedIds) {
        FirebaseFirestore.getInstance()
                .collection("recipes")
                .whereEqualTo("userId", uid)
                .get()
                .addOnSuccessListener(snap -> {
                    recipeList.clear();
                    for (QueryDocumentSnapshot doc : snap) {
                        if (allowedIds == null || allowedIds.contains(doc.getId())) {
                            recipeList.add(Recipe.fromDocument(doc));
                        }
                    }
                    Collections.sort(recipeList, Comparator.comparingLong(Recipe::getCreatedAt));
                    onLoaded();
                })
                .addOnFailureListener(e -> {
                    hideLoading();
                    showEmpty();
                    Snackbar.make(rootView, R.string.recipes_load_failed, Snackbar.LENGTH_LONG).show();
                });
    }

    private void onLoaded() {
        hideLoading();
        if (recipeList.isEmpty()) {
            showEmpty();
            return;
        }

        showPager();
        viewPager.setAdapter(new BookPagerAdapter(this, recipeList, userIdentity));
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateIndicator(position);
            }
        });
        updateIndicator(0);
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
}
