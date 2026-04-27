package com.recipebookpro.ui.shopping;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.FirebaseFirestore;
import com.recipebookpro.R;
import com.recipebookpro.model.ShoppingList;
import com.recipebookpro.model.ShoppingList.ShoppingItem;
import com.recipebookpro.ui.BaseActivity;
import com.recipebookpro.ui.shopping.adapter.ShoppingItemAdapter;
import android.graphics.Rect;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.List;

public class ShoppingListDetailActivity extends BaseActivity {

    public static final String EXTRA_LIST_ID = "list_id";

    private String listId;
    private ShoppingList shoppingList;
    private FirebaseFirestore db;

    private MaterialToolbar toolbar;
    private RecyclerView rvItems;
    private ProgressBar progress;
    private TextInputEditText etNewItem;

    private ShoppingItemAdapter adapter;
    private List<ShoppingItem> items = new ArrayList<>();
    private boolean skipNextSnapshot = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shopping_list_detail);
        
        applyTopInsetToView(findViewById(R.id.appBarShoppingListDetail));

        listId = getIntent().getStringExtra(EXTRA_LIST_ID);
        if (TextUtils.isEmpty(listId)) {
            finish();
            return;
        }

        db = FirebaseFirestore.getInstance();

        initViews();
        loadList();

        View rootView = findViewById(android.R.id.content);
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            Rect r = new Rect();
            rootView.getWindowVisibleDisplayFrame(r);
            int screenHeight = rootView.getRootView().getHeight();
            int keypadHeight = screenHeight - r.bottom;

            if (keypadHeight > screenHeight * 0.15) {
                rootView.setPadding(0, 0, 0, keypadHeight);
            } else {
                rootView.setPadding(0, 0, 0, 0);
            }
        });

        etNewItem.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                rvItems.postDelayed(() -> {
                    if (items.size() > 0) {
                        rvItems.smoothScrollToPosition(items.size() - 1);
                    }
                }, 300);
            }
        });
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbarShoppingListDetail);
        rvItems = findViewById(R.id.rvShoppingItems);
        progress = findViewById(R.id.progressShoppingDetail);

        toolbar.setNavigationOnClickListener(v -> finish());

        etNewItem = findViewById(R.id.etNewItem);
        MaterialButton btnAddItem = findViewById(R.id.btnAddItem);

        btnAddItem.setOnClickListener(v -> addNewItem());
        etNewItem.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                addNewItem();
                return true;
            }
            return false;
        });

        rvItems.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ShoppingItemAdapter(items, new ShoppingItemAdapter.OnItemInteractionListener() {
            @Override
            public void onCheckChanged(int position, boolean isChecked) {
                if (isChecked && position >= 0 && position < items.size()) {
                    items.remove(position);
                    adapter.notifyItemRemoved(position);
                    adapter.notifyItemRangeChanged(position, items.size());
                    if (shoppingList != null) {
                        shoppingList.setItems(new ArrayList<>(items));
                    }
                    saveListChanges();
                }
            }

            @Override
            public void onHomeStatusChanged(int position, boolean haveItAtHome) {
                items.get(position).setHomeStatus(
                        haveItAtHome ? ShoppingItem.STATUS_HAVE_IT : ShoppingItem.STATUS_NEED_TO_BUY
                );
                saveListChanges();
            }
        });
        rvItems.setAdapter(adapter);
    }

    private void loadList() {
        progress.setVisibility(View.VISIBLE);
        db.collection("shopping_lists").document(listId).addSnapshotListener((doc, e) -> {
            progress.setVisibility(View.GONE);
            if (e != null || doc == null || !doc.exists()) {
                Toast.makeText(this, "Liste bulunamadı", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            if (skipNextSnapshot) {
                skipNextSnapshot = false;
                return;
            }
            shoppingList = ShoppingList.fromDocument(doc);
            toolbar.setTitle(shoppingList.getName());
            
            items.clear();
            items.addAll(shoppingList.getItems());
            adapter.notifyDataSetChanged();
        });
    }

    private void addNewItem() {
        String text = etNewItem.getText() != null ? etNewItem.getText().toString().trim() : "";
        if (text.isEmpty()) return;

        ShoppingItem newItem = new ShoppingItem(text, "", "");
        items.add(newItem);
        if (shoppingList != null) {
            shoppingList.setItems(new ArrayList<>(items));
        }
        adapter.notifyItemInserted(items.size() - 1);
        rvItems.scrollToPosition(items.size() - 1);
        etNewItem.setText("");
        saveListChanges();
    }

    private void saveListChanges() {
        if (shoppingList != null) {
            skipNextSnapshot = true;
            db.collection("shopping_lists").document(listId).set(shoppingList);
        }
    }
}
