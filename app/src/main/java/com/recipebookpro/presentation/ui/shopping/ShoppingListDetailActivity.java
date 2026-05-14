package com.recipebookpro.presentation.ui.shopping;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.recipebookpro.R;
import com.recipebookpro.data.remote.ShoppingListTranslationHelper;
import com.recipebookpro.domain.model.ShoppingList;
import com.recipebookpro.domain.model.ShoppingList.ShoppingItem;
import com.recipebookpro.presentation.ui.BaseActivity;
import com.recipebookpro.presentation.ui.LocaleHelper;
import com.recipebookpro.presentation.ui.shopping.adapter.ShoppingItemAdapter;
import android.graphics.Rect;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private ListenerRegistration listListener;
    private boolean deletingBecauseEmpty = false;
    private final ExecutorService shoppingLocalizationExecutor = Executors.newSingleThreadExecutor();
    private int shoppingSnapshotGen = 0;
    private String shoppingDisplayedLocale;

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

        View layoutAddItem = findViewById(R.id.layoutAddItemContainer);
        ViewCompat.setOnApplyWindowInsetsListener(layoutAddItem, (v, insets) -> {
            androidx.core.graphics.Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            androidx.core.graphics.Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), 
                    Math.max(systemBars.bottom, ime.bottom));
            return WindowInsetsCompat.CONSUMED;
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

        toolbar.inflateMenu(R.menu.menu_shopping_list_detail);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_share_list) {
                shareList();
                return true;
            } else if (item.getItemId() == R.id.action_delete_list) {
                confirmDeleteList();
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

            @Override
            public void onEditClick(int position) {
                showEditShoppingItemDialog(position);
            }

            @Override
            public void onDeleteClick(int position) {
                confirmRemoveShoppingItem(position);
            }
        });
        rvItems.setAdapter(adapter);
    }

    private void loadList() {
        progress.setVisibility(View.VISIBLE);
        if (listListener != null) {
            listListener.remove();
        }
        listListener = db.collection("shopping_lists").document(listId).addSnapshotListener((doc, e) -> {
            progress.setVisibility(View.GONE);
            if (e != null || doc == null || !doc.exists()) {
                if (!deletingBecauseEmpty) {
                    Toast.makeText(this, R.string.shopping_list_not_found, Toast.LENGTH_SHORT).show();
                }
                finish();
                return;
            }
            shoppingList = ShoppingList.fromDocument(doc);

            String listName = shoppingList.getName();
            if ("system_planner_weekly_menu".equals(listName) || "Weekly Menu Shopping".equals(listName) || "Haftalık Menü Alışverişi".equals(listName)) {
                listName = getString(R.string.planner_weekly_menu_list_name);
            }
            toolbar.setTitle(listName);

            items.clear();
            items.addAll(shoppingList.getItems());
            adapter.notifyDataSetChanged();

            final int gen = ++shoppingSnapshotGen;
            final List<ShoppingItem> snapshot = ShoppingListTranslationHelper.copyItems(shoppingList.getItems());
            shoppingLocalizationExecutor.execute(() -> {
                try {
                    List<ShoppingItem> working = ShoppingListTranslationHelper.copyItems(snapshot);
                    boolean changed = ShoppingListTranslationHelper.localizeItemsSync(
                            getApplicationContext(), working, LocaleHelper.getLanguage(ShoppingListDetailActivity.this));
                    runOnUiThread(() -> {
                        if (gen != shoppingSnapshotGen) {
                            return;
                        }
                        if (shoppingList == null) {
                            return;
                        }
                        items.clear();
                        items.addAll(working);
                        shoppingList.setItems(new ArrayList<>(items));
                        adapter.notifyDataSetChanged();
                        if (changed) {
                            persistTranslatedListQuietly();
                        }
                        shoppingDisplayedLocale = LocaleHelper.getLanguage(ShoppingListDetailActivity.this);
                    });
                } catch (Exception ignored) {
                }
            });
        });
    }

    private void persistTranslatedListQuietly() {
        if (shoppingList == null) {
            return;
        }
        db.collection("shopping_lists").document(listId).set(shoppingList)
                .addOnFailureListener(e -> { });
    }

    @Override
    protected void onResume() {
        super.onResume();
        String lang = LocaleHelper.getLanguage(this);
        if (shoppingList == null) {
            return;
        }
        if (!items.isEmpty() && (shoppingDisplayedLocale == null || !shoppingDisplayedLocale.equals(lang))) {
            scheduleShoppingListReLocalization();
            return;
        }
        if (shoppingDisplayedLocale == null) {
            shoppingDisplayedLocale = lang;
        }
    }

    private void scheduleShoppingListReLocalization() {
        final int gen = ++shoppingSnapshotGen;
        final List<ShoppingItem> snapshot = ShoppingListTranslationHelper.copyItems(items);
        shoppingLocalizationExecutor.execute(() -> {
            try {
                List<ShoppingItem> working = ShoppingListTranslationHelper.copyItems(snapshot);
                boolean changed = ShoppingListTranslationHelper.localizeItemsSync(
                        getApplicationContext(), working, LocaleHelper.getLanguage(ShoppingListDetailActivity.this));
                runOnUiThread(() -> {
                    if (gen != shoppingSnapshotGen) {
                        return;
                    }
                    if (shoppingList == null) {
                        return;
                    }
                    items.clear();
                    items.addAll(working);
                    shoppingList.setItems(new ArrayList<>(items));
                    adapter.notifyDataSetChanged();
                    if (changed) {
                        persistTranslatedListQuietly();
                    }
                    shoppingDisplayedLocale = LocaleHelper.getLanguage(ShoppingListDetailActivity.this);
                });
            } catch (Exception ignored) {
            }
        });
    }

    private void addNewItem() {
        String text = etNewItem.getText() != null ? etNewItem.getText().toString().trim() : "";
        if (text.isEmpty()) return;

        ShoppingItem newItem = new ShoppingItem(text, "", "");
        newItem.setUserAdded(true);
        items.add(newItem);
        if (shoppingList != null) {
            shoppingList.setItems(new ArrayList<>(items));
        }
        adapter.notifyItemInserted(items.size() - 1);
        rvItems.scrollToPosition(items.size() - 1);
        etNewItem.setText("");
        saveListChanges();
    }

    private void showEditShoppingItemDialog(int position) {
        if (position < 0 || position >= items.size()) {
            return;
        }
        ShoppingItem item = items.get(position);
        if (!item.isUserAdded()) {
            return;
        }
        View form = LayoutInflater.from(this).inflate(R.layout.dialog_edit_shopping_item, null, false);
        TextInputEditText etLine = form.findViewById(R.id.etEditShoppingLine);
        etLine.setText(item.getDisplayText());

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.shopping_item_edit_title)
                .setView(form)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(v -> {
                        String line = etLine.getText() != null ? etLine.getText().toString().trim() : "";
                        if (line.isEmpty()) {
                            Toast.makeText(this, R.string.shopping_item_name_required, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        item.setName(line);
                        item.setAmount("");
                        item.setUnit("");
                        if (shoppingList != null) {
                            shoppingList.setItems(new ArrayList<>(items));
                        }
                        adapter.notifyItemChanged(position);
                        saveListChanges();
                        dialog.dismiss();
                    });
            etLine.requestFocus();
            etLine.selectAll();
        });
        dialog.show();
    }

    private void confirmRemoveShoppingItem(int position) {
        if (position < 0 || position >= items.size()) {
            return;
        }
        ShoppingItem item = items.get(position);
        if (!item.isUserAdded()) {
            return;
        }
        String label = item.getDisplayText();
        if (TextUtils.isEmpty(label)) {
            label = item.getName();
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.shopping_item_delete)
                .setMessage(getString(R.string.shopping_item_delete_confirm, label))
                .setPositiveButton(R.string.delete, (d, w) -> {
                    items.remove(position);
                    adapter.notifyItemRemoved(position);
                    adapter.notifyItemRangeChanged(position, items.size());
                    if (shoppingList != null) {
                        shoppingList.setItems(new ArrayList<>(items));
                    }
                    saveListChanges();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void saveListChanges() {
        if (shoppingList != null) {
            if (items.isEmpty()) {
                deletingBecauseEmpty = true;
                db.collection("shopping_lists").document(listId).delete()
                        .addOnFailureListener(e -> Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show());
                return;
            }
            db.collection("shopping_lists").document(listId).set(shoppingList)
                    .addOnFailureListener(e -> Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show());
        }
    }

    private void shareList() {
        if (shoppingList == null) return;
        com.recipebookpro.presentation.ui.kitchen.CollaboratorsBottomSheet sheet = 
            com.recipebookpro.presentation.ui.kitchen.CollaboratorsBottomSheet.newInstance(
                listId, "shopping_list", shoppingList.getName());
        sheet.show(getSupportFragmentManager(), "Collaborators");
    }

    private void confirmDeleteList() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.delete_recipe)
                .setMessage(R.string.shopping_list_delete_confirm)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    deletingBecauseEmpty = true;
                    db.collection("shopping_lists").document(listId).delete()
                            .addOnSuccessListener(aVoid -> finish())
                            .addOnFailureListener(e -> Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    protected void onDestroy() {
        if (listListener != null) {
            listListener.remove();
            listListener = null;
        }
        shoppingLocalizationExecutor.shutdown();
        super.onDestroy();
    }
}
