package com.recipebookpro.ui.book;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textview.MaterialTextView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.recipebookpro.R;
import com.recipebookpro.model.Recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class BookReaderActivity extends AppCompatActivity {

    private MaterialTextView tvToolbarTitle;
    private MaterialTextView tvToolbarSubtitle;
    private ViewPager2 viewPager;
    private MaterialTextView tvPageIndicator;
    private MaterialTextView tvEmpty;
    private CircularProgressIndicator progressBar;
    private final List<Recipe> recipeList = new ArrayList<>();
    private String userIdentity = "";
    private View rootView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book_reader);

        rootView = findViewById(R.id.bookReaderRoot);
        MaterialToolbar toolbar = findViewById(R.id.toolbarBook);
        tvToolbarTitle = findViewById(R.id.tvToolbarTitle);
        tvToolbarSubtitle = findViewById(R.id.tvToolbarSubtitle);
        MaterialButton btnToolbarToc = findViewById(R.id.btnToolbarToc);
        viewPager = findViewById(R.id.viewPager);
        tvPageIndicator = findViewById(R.id.tvPageIndicator);
        tvEmpty = findViewById(R.id.tvBookEmpty);
        progressBar = findViewById(R.id.progressBook);

        toolbar.setNavigationOnClickListener(v -> finish());
        btnToolbarToc.setOnClickListener(v -> goToToc());

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            userIdentity = user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty()
                    ? user.getDisplayName().trim()
                    : user.getEmail();
        }

        viewPager.setClipToPadding(false);
        viewPager.setClipChildren(false);
        viewPager.setOffscreenPageLimit(3);
        viewPager.setPageTransformer(new BookPageTransformer());

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

        FirebaseFirestore.getInstance()
                .collection("recipes")
                .whereEqualTo("userId", user.getUid())
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(snap -> {
                    recipeList.clear();
                    for (QueryDocumentSnapshot doc : snap) {
                        recipeList.add(Recipe.fromDocument(doc));
                    }
                    onLoaded();
                })
                .addOnFailureListener(e -> {
                    String message = e.getMessage() != null ? e.getMessage() : "";
                    if (message.contains("FAILED_PRECONDITION") || message.contains("index")) {
                        loadFallback(user.getUid());
                    } else {
                        hideLoading();
                        showEmpty();
                        Snackbar.make(rootView, R.string.recipes_load_failed, Snackbar.LENGTH_LONG).show();
                    }
                });
    }

    private void loadFallback(String uid) {
        FirebaseFirestore.getInstance()
                .collection("recipes")
                .whereEqualTo("userId", uid)
                .get()
                .addOnSuccessListener(snap -> {
                    recipeList.clear();
                    for (QueryDocumentSnapshot doc : snap) {
                        recipeList.add(Recipe.fromDocument(doc));
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
        if (position == 0) {
            tvPageIndicator.setText(R.string.cover);
            tvToolbarTitle.setText(R.string.my_recipe_book);
            tvToolbarSubtitle.setText(R.string.cover_page_label);
            return;
        }

        if (position == 1) {
            tvPageIndicator.setText(R.string.table_of_contents);
            tvToolbarTitle.setText(R.string.table_of_contents);
            tvToolbarSubtitle.setText(getString(R.string.recipe_count_label, recipeList.size()));
            return;
        }

        int absolutePage = position + 1;
        int totalPages = recipeList.size() + 2;
        Recipe recipe = recipeList.get(position - 2);
        tvPageIndicator.setText(getString(R.string.page_number, absolutePage, totalPages));
        tvToolbarTitle.setText(recipe.getTitle());
        tvToolbarSubtitle.setText(getString(R.string.recipe_page_label, absolutePage));
    }

    private void showLoading() {
        progressBar.setVisibility(View.VISIBLE);
        viewPager.setVisibility(View.GONE);
        tvEmpty.setVisibility(View.GONE);
        tvPageIndicator.setVisibility(View.GONE);
        tvToolbarTitle.setText(R.string.my_recipe_book);
        tvToolbarSubtitle.setText(R.string.loading);
    }

    private void hideLoading() {
        progressBar.setVisibility(View.GONE);
    }

    private void showEmpty() {
        tvEmpty.setVisibility(View.VISIBLE);
        viewPager.setVisibility(View.GONE);
        tvPageIndicator.setVisibility(View.GONE);
        tvToolbarTitle.setText(R.string.my_recipe_book);
        tvToolbarSubtitle.setText(R.string.no_recipes_in_book);
    }

    private void showPager() {
        viewPager.setVisibility(View.VISIBLE);
        tvPageIndicator.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
    }
}
