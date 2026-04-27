package com.recipebookpro.ui.book;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.textview.MaterialTextView;
import com.recipebookpro.R;
import com.google.firebase.firestore.FirebaseFirestore;
import com.recipebookpro.model.Cookbook;
import com.recipebookpro.model.Recipe;
import com.recipebookpro.model.StickerModel;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.view.MotionEvent;
import coil.Coil;
import coil.request.ImageRequest;
import java.util.ArrayList;
import java.util.List;
import android.widget.PopupMenu;
import com.google.android.material.button.MaterialButton;

public class RecipePageFragment extends Fragment {

    private static final String ARG_RECIPE = "recipe";
    private static final String ARG_PAGE_NO = "page_no";
    private static final String ARG_TOTAL = "total";

    private Recipe recipe;
    private String cookbookId;
    private FrameLayout stickerCanvas;
    private boolean isEditing = false;

    private ArrayList<String> allBookIds;

    public static RecipePageFragment newInstance(Recipe recipe, String cookbookId, ArrayList<String> allBookIds, int pageNo, int totalPages) {
        RecipePageFragment fragment = new RecipePageFragment();
        Bundle args = new Bundle();
        args.putSerializable("recipe", recipe);
        args.putString("cookbookId", cookbookId);
        args.putStringArrayList("allBookIds", allBookIds);
        args.putInt(ARG_PAGE_NO, pageNo);
        args.putInt(ARG_TOTAL, totalPages);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_recipe_page, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getArguments() != null) {
            recipe = (Recipe) getArguments().getSerializable("recipe");
            cookbookId = getArguments().getString("cookbookId");
        }

        if (recipe == null) {
            return;
        }

        MaterialTextView tvTitle = view.findViewById(R.id.tvPageTitle);
        MaterialTextView tvCategory = view.findViewById(R.id.tvPageCategory);
        MaterialTextView tvDescLabel = view.findViewById(R.id.tvPageDescLabel);
        MaterialTextView tvDesc = view.findViewById(R.id.tvPageDesc);
        MaterialTextView tvIngredients = view.findViewById(R.id.tvPageIngredients);
        MaterialTextView tvSteps = view.findViewById(R.id.tvPageSteps);
        MaterialTextView tvPageNum = view.findViewById(R.id.tvPageNum);

        tvTitle.setText(recipe.getTitle());
        tvCategory.setText(recipe.getCategory().isEmpty()
                ? getString(R.string.category_unknown)
                : recipe.getCategory());

        if (TextUtils.isEmpty(recipe.getDescription())) {
            tvDescLabel.setVisibility(View.GONE);
            tvDesc.setVisibility(View.GONE);
        } else {
            tvDesc.setText(recipe.getDescription());
        }

        tvIngredients.setText(recipe.getFormattedIngredients().isEmpty()
                ? getString(R.string.no_ingredients)
                : formatBullets(recipe.getFormattedIngredients()));
        tvSteps.setText(recipe.getSteps().isEmpty()
                ? getString(R.string.no_steps)
                : formatSteps(recipe.getSteps()));
        tvPageNum.setText(getString(R.string.page_number, getArguments().getInt(ARG_PAGE_NO), getArguments().getInt(ARG_TOTAL)));

        if (getArguments() != null) {
            recipe = (Recipe) getArguments().getSerializable("recipe");
            cookbookId = getArguments().getString("cookbookId");
            allBookIds = getArguments().getStringArrayList("allBookIds");
        }

        stickerCanvas = view.findViewById(R.id.stickerCanvas);
        MaterialButton btnSelectDesign = view.findViewById(R.id.btnSelectDesign);

        // Show design selector only if in "All Recipes" mode and multiple books exist
        if (allBookIds != null && allBookIds.size() > 1) {
            btnSelectDesign.setVisibility(View.VISIBLE);
            btnSelectDesign.setOnClickListener(v -> showDesignSelector(v));
        } else {
            btnSelectDesign.setVisibility(View.GONE);
        }

        loadStickersFromCookbook(cookbookId);
    }

    private void showDesignSelector(View anchor) {
        if (allBookIds == null || allBookIds.isEmpty()) return;

        PopupMenu popup = new PopupMenu(getContext(), anchor);
        
        // Fetch cookbook names (Ideally we'd have them already, but let's fetch)
        for (int i = 0; i < allBookIds.size(); i++) {
            String bid = allBookIds.get(i);
            int index = i;
            FirebaseFirestore.getInstance().collection("cookbooks").document(bid).get()
                .addOnSuccessListener(doc -> {
                    String name = doc.getString("name");
                    if (name == null) name = "Defter " + (index + 1);
                    popup.getMenu().add(0, index, 0, name);
                    
                    if (index == allBookIds.size() - 1) {
                        popup.show();
                    }
                });
        }

        popup.setOnMenuItemClickListener(item -> {
            String selectedId = allBookIds.get(item.getItemId());
            this.cookbookId = selectedId;
            loadStickersFromCookbook(selectedId);
            return true;
        });
    }

    private void loadStickersFromCookbook(String cid) {
        if (cid == null || recipe == null) return;
        
        stickerCanvas.removeAllViews(); // Clear current
        
        FirebaseFirestore.getInstance().collection("cookbooks").document(cid)
                .get()
                .addOnSuccessListener(doc -> {
                    Cookbook book = Cookbook.fromDocument(doc);
                    if (book != null && book.getRecipeStickers() != null) {
                        List<StickerModel> stickers = book.getRecipeStickers().get(recipe.getId());
                        if (stickers != null) {
                            for (StickerModel sm : stickers) {
                                addStickerToCanvas(sm, false);
                            }
                        }
                    }
                });
    }

    public void addSticker(String imageUrl) {
        StickerModel model = new StickerModel(imageUrl, 100, 100, 0, 1.0f);
        addStickerToCanvas(model, true);
    }

    public void performSave(Runnable onComplete) {
        saveStickersToFirestore(onComplete);
    }

    private void addStickerToCanvas(StickerModel model, boolean isNew) {
        ImageView iv = new ImageView(getContext());
        int size = (int) (100 * getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size);
        iv.setLayoutParams(lp);
        iv.setTag(model);
        
        iv.setX(model.getX());
        iv.setY(model.getY());
        iv.setRotation(model.getRotation());
        iv.setScaleX(model.getScale());
        iv.setScaleY(model.getScale());

        ImageRequest request = new ImageRequest.Builder(requireContext())
                .data(model.getImageUrl())
                .target(iv)
                .build();
        Coil.imageLoader(requireContext()).enqueue(request);

        setupStickerTouchListener(iv, model);
        stickerCanvas.addView(iv);
    }

    private void setupStickerTouchListener(View view, StickerModel model) {
        view.setOnTouchListener(new View.OnTouchListener() {
            private float lastX, lastY;
            private float lastRotation, lastScale;
            private float initialDist, initialRotation;
            private int mode = 0;
            private long startTime;
            private boolean isMoved = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (!isEditing) return false;

                switch (event.getAction() & MotionEvent.ACTION_MASK) {
                    case MotionEvent.ACTION_DOWN:
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                        mode = 1;
                        lastX = event.getRawX();
                        lastY = event.getRawY();
                        startTime = System.currentTimeMillis();
                        isMoved = false;
                        v.bringToFront();
                        break;
                    
                    case MotionEvent.ACTION_POINTER_DOWN:
                        if (event.getPointerCount() == 2) {
                            mode = 2;
                            initialDist = getDistance(event);
                            initialRotation = getRotation(event);
                            lastScale = v.getScaleX();
                            lastRotation = v.getRotation();
                        }
                        break;

                    case MotionEvent.ACTION_MOVE:
                        if (mode == 1) {
                            float deltaX = event.getRawX() - lastX;
                            float deltaY = event.getRawY() - lastY;
                            if (Math.abs(deltaX) > 5 || Math.abs(deltaY) > 5) isMoved = true;
                            
                            v.setX(v.getX() + deltaX);
                            v.setY(v.getY() + deltaY);
                            lastX = event.getRawX();
                            lastY = event.getRawY();
                        } else if (mode == 2 && event.getPointerCount() == 2) {
                            isMoved = true;
                            float newDist = getDistance(event);
                            float newRot = getRotation(event);
                            
                            float scale = (newDist / initialDist) * lastScale;
                            v.setScaleX(scale);
                            v.setScaleY(scale);
                            
                            float rotation = (newRot - initialRotation) + lastRotation;
                            v.setRotation(rotation);
                        }
                        break;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_POINTER_UP:
                        v.getParent().requestDisallowInterceptTouchEvent(false);
                        if (mode == 1 && !isMoved) {
                            long duration = System.currentTimeMillis() - startTime;
                            if (duration > 400) {
                                showUnifiedMenu(v, model);
                            }
                        }
                        mode = 0;
                        break;
                }
                return true;
            }

            private float getDistance(MotionEvent event) {
                float x = event.getX(0) - event.getX(1);
                float y = event.getY(0) - event.getY(1);
                return (float) Math.sqrt(x * x + y * y);
            }

            private float getRotation(MotionEvent event) {
                double deltaX = (event.getX(0) - event.getX(1));
                double deltaY = (event.getY(0) - event.getY(1));
                double radians = Math.atan2(deltaY, deltaX);
                return (float) Math.toDegrees(radians);
            }
        });
    }

    public void setEditMode(boolean editing) {
        this.isEditing = editing;
        if (editing) {
            stickerCanvas.setBackgroundColor(0x15000000); // Subtle dark overlay for edit mode
        } else {
            stickerCanvas.setBackgroundColor(0);
            stickerCanvas.removeAllViews(); // Clear immediately for visual feedback
            loadStickersFromCookbook(cookbookId);
        }
    }

    private void showUnifiedMenu(View v, StickerModel model) {
        String[] options = {"En Öne Getir", "En Arkaya Gönder", "Sil"};
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Sticker Seçenekleri")
                .setItems(options, (d, which) -> {
                    if (which == 0) {
                        v.bringToFront();
                    } else if (which == 1) {
                        ViewGroup parent = (ViewGroup) v.getParent();
                        if (parent != null) {
                            parent.removeView(v);
                            parent.addView(v, 0);
                        }
                    } else if (which == 2) {
                        stickerCanvas.removeView(v);
                    }
                })
                .show();
    }

    private void saveStickersToFirestore(Runnable onComplete) {
        if (cookbookId == null) {
            android.widget.Toast.makeText(getContext(), "Bu tarifi önce bir deftere eklemelisiniz", android.widget.Toast.LENGTH_LONG).show();
            if (onComplete != null) onComplete.run();
            return;
        }

        List<StickerModel> currentStickers = new java.util.ArrayList<>();
        for (int i = 0; i < stickerCanvas.getChildCount(); i++) {
            View v = stickerCanvas.getChildAt(i);
            Object tag = v.getTag();
            if (tag instanceof StickerModel) {
                StickerModel m = (StickerModel) tag;
                m.setX(v.getX());
                m.setY(v.getY());
                m.setScale(v.getScaleX());
                m.setRotation(v.getRotation());
                currentStickers.add(m);
            }
        }

        FirebaseFirestore.getInstance().collection("cookbooks").document(cookbookId)
                .get().addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Cookbook book = Cookbook.fromDocument(doc);
                        book.getRecipeStickers().put(recipe.getId(), currentStickers);
                        
                        FirebaseFirestore.getInstance().collection("cookbooks").document(cookbookId)
                                .update("recipeStickers", book.getRecipeStickers())
                                .addOnCompleteListener(task -> {
                                    if (onComplete != null) onComplete.run();
                                });
                    } else {
                        if (onComplete != null) onComplete.run();
                    }
                })
                .addOnFailureListener(e -> {
                    if (onComplete != null) onComplete.run();
                });
    }

    private String formatBullets(String text) {
        StringBuilder builder = new StringBuilder();
        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            String cleaned = line.trim();
            if (cleaned.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append("• ").append(cleaned);
        }
        return builder.toString();
    }

    private String formatSteps(String text) {
        StringBuilder builder = new StringBuilder();
        String[] lines = text.split("\\r?\\n");
        int step = 1;
        for (String line : lines) {
            String cleaned = line.trim();
            if (cleaned.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append(step).append(". ")
                    .append(cleaned.replaceFirst("^[\\-•*\\d.)\\s]+", "").trim());
            step++;
        }
        return builder.length() == 0 ? text : builder.toString();
    }
}
