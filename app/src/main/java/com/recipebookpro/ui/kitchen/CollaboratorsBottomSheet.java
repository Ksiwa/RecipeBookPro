package com.recipebookpro.ui.kitchen;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.recipebookpro.R;
import com.recipebookpro.model.Cookbook;
import com.recipebookpro.model.User;

import java.util.ArrayList;
import java.util.List;

public class CollaboratorsBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_COOKBOOK_ID = "cookbook_id";

    private String cookbookId;
    private Cookbook cookbook;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;

    private TextInputEditText etSearch;
    private LinearLayout containerSearchResults;
    private LinearLayout containerCollaborators;
    private ListenerRegistration cookbookListener;

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    public static CollaboratorsBottomSheet newInstance(String cookbookId) {
        CollaboratorsBottomSheet fragment = new CollaboratorsBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_COOKBOOK_ID, cookbookId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            cookbookId = getArguments().getString(ARG_COOKBOOK_ID);
        }
        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_collaborators, container, false);

        etSearch = view.findViewById(R.id.etSearchCollab);
        containerSearchResults = view.findViewById(R.id.containerSearchResults);
        containerCollaborators = view.findViewById(R.id.containerCollaborators);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (searchRunnable != null) searchHandler.removeCallbacks(searchRunnable);
                String query = s.toString().trim();
                if (query.length() < 2) {
                    containerSearchResults.removeAllViews();
                    return;
                }
                searchRunnable = () -> searchUsers(query);
                searchHandler.postDelayed(searchRunnable, 400);
            }
        });

        loadCookbook();

        return view;
    }

    private void loadCookbook() {
        cookbookListener = db.collection("cookbooks").document(cookbookId).addSnapshotListener((doc, e) -> {
            if (e != null || doc == null || !doc.exists()) return;
            if (!isAdded() || getContext() == null) return;
            cookbook = Cookbook.fromDocument(doc);
            updateCollaboratorsList();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (cookbookListener != null) {
            cookbookListener.remove();
            cookbookListener = null;
        }
    }

    private void searchUsers(String query) {
        String lowerQuery = query.toLowerCase();

        db.collection("users")
                .orderBy("email")
                .startAt(lowerQuery)
                .endAt(lowerQuery + "\uf8ff")
                .limit(5)
                .get()
                .addOnSuccessListener(results -> {
                    if (!isAdded() || getContext() == null) return;
                    containerSearchResults.removeAllViews();
                    if (results.isEmpty()) {
                        TextView tvEmpty = new TextView(getContext());
                        tvEmpty.setText("Sonuç bulunamadı");
                        tvEmpty.setPadding(0, 16, 0, 16);
                        containerSearchResults.addView(tvEmpty);
                        return;
                    }

                    for (QueryDocumentSnapshot doc : results) {
                        User user = doc.toObject(User.class);
                        if (user == null) continue;
                        if (user.getUid() == null || user.getUid().isEmpty()) {
                            user.setUid(doc.getId());
                        }
                        if (currentUser != null && currentUser.getUid().equals(user.getUid())) continue;
                        if (cookbook != null && cookbook.getCollaboratorIds().contains(user.getUid())) continue;

                        View row = LayoutInflater.from(getContext())
                                .inflate(android.R.layout.simple_list_item_2, containerSearchResults, false);
                        TextView tv1 = row.findViewById(android.R.id.text1);
                        TextView tv2 = row.findViewById(android.R.id.text2);
                        tv1.setText(user.getDisplayName());
                        tv2.setText(user.getEmail());

                        final String uid = user.getUid();
                        final String name = user.getDisplayName();
                        row.setOnClickListener(v -> inviteUser(uid, name));

                        containerSearchResults.addView(row);
                    }
                });
    }

    private void inviteUser(String userId, String displayName) {
        if (cookbook == null) return;

        db.collection("cookbook_invitations")
                .whereEqualTo("cookbookId", cookbookId)
                .whereEqualTo("toUserId", userId)
                .whereEqualTo("status", "pending")
                .get()
                .addOnSuccessListener(existing -> {
                    if (!existing.isEmpty()) {
                        Toast.makeText(getContext(), "Zaten davet gönderilmiş", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    java.util.Map<String, Object> invitation = new java.util.HashMap<>();
                    invitation.put("cookbookId", cookbookId);
                    invitation.put("cookbookName", cookbook.getName());
                    invitation.put("fromUserId", currentUser.getUid());
                    invitation.put("fromUserName", currentUser.getDisplayName());
                    invitation.put("toUserId", userId);
                    invitation.put("status", "pending");
                    invitation.put("createdAt", System.currentTimeMillis());

                    db.collection("cookbook_invitations").add(invitation)
                            .addOnSuccessListener(ref -> {
                                Toast.makeText(getContext(), displayName + " kullanıcısına davet gönderildi", Toast.LENGTH_SHORT).show();
                                etSearch.setText("");
                                containerSearchResults.removeAllViews();
                            });
                });
    }

    private void updateCollaboratorsList() {
        if (containerCollaborators == null || cookbook == null || !isAdded() || getContext() == null) return;
        containerCollaborators.removeAllViews();

        boolean isOwner = currentUser != null && currentUser.getUid().equals(cookbook.getUserId());

        List<String> collabIds = cookbook.getCollaboratorIds();
        if (collabIds == null || collabIds.isEmpty()) {
            TextView tvEmpty = new TextView(getContext());
            tvEmpty.setText("Henüz ortak çalışan yok");
            tvEmpty.setPadding(0, 24, 0, 24);
            tvEmpty.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            containerCollaborators.addView(tvEmpty);
            return;
        }

        List<Task<DocumentSnapshot>> tasks = new ArrayList<>();
        for (String uid : collabIds) {
            tasks.add(db.collection("users").document(uid).get());
        }

        Tasks.whenAllSuccess(tasks).addOnSuccessListener(results -> {
            if (containerCollaborators == null || !isAdded() || getContext() == null) return;
            containerCollaborators.removeAllViews();

            for (Object result : results) {
                DocumentSnapshot docSnap = (DocumentSnapshot) result;
                if (!docSnap.exists()) continue;

                User user = docSnap.toObject(User.class);
                if (user == null) continue;
                if (user.getUid() == null || user.getUid().isEmpty()) {
                    user.setUid(docSnap.getId());
                }

                View row = LayoutInflater.from(getContext())
                        .inflate(android.R.layout.simple_list_item_2, containerCollaborators, false);
                TextView tv1 = row.findViewById(android.R.id.text1);
                TextView tv2 = row.findViewById(android.R.id.text2);

                tv1.setText(user.getDisplayName());
                tv2.setText(user.getEmail());

                final String collabUid = user.getUid();
                final String collabName = user.getDisplayName();

                boolean isSelf = currentUser != null && currentUser.getUid().equals(collabUid);

                row.setOnLongClickListener(v -> {
                    if (isOwner) {
                        new MaterialAlertDialogBuilder(requireContext())
                                .setTitle(collabName)
                                .setMessage("Bu ortağı kaldırmak istiyor musunuz?")
                                .setPositiveButton("Kaldır", (d, w) -> removeCollaborator(collabUid))
                                .setNegativeButton("İptal", null)
                                .show();
                    } else if (isSelf) {
                        new MaterialAlertDialogBuilder(requireContext())
                                .setTitle("Ortaklıktan Ayrıl")
                                .setMessage("Bu defterden ayrılmak istiyor musunuz?")
                                .setPositiveButton("Ayrıl", (d, w) -> removeCollaborator(collabUid))
                                .setNegativeButton("İptal", null)
                                .show();
                    }
                    return true;
                });

                if (isSelf && !isOwner) {
                    MaterialButton btnLeave = new MaterialButton(requireContext(), null,
                            com.google.android.material.R.attr.materialButtonOutlinedStyle);
                    btnLeave.setText("Ayrıl");
                    btnLeave.setTextColor(requireContext().getColor(com.google.android.material.R.color.m3_ref_palette_error40));
                    btnLeave.setOnClickListener(v ->
                            new MaterialAlertDialogBuilder(requireContext())
                                    .setTitle("Ortaklıktan Ayrıl")
                                    .setMessage("Bu defterden ayrılmak istiyor musunuz?")
                                    .setPositiveButton("Ayrıl", (d, w) -> removeCollaborator(collabUid))
                                    .setNegativeButton("İptal", null)
                                    .show());

                    containerCollaborators.addView(btnLeave);
                }

                containerCollaborators.addView(row);
            }
        });
    }

    private void removeCollaborator(String uid) {
        db.collection("cookbooks").document(cookbookId)
                .update("collaboratorIds", FieldValue.arrayRemove(uid));
    }
}
