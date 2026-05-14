package com.recipebookpro.presentation.ui.kitchen;

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
import com.google.android.material.textview.MaterialTextView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.recipebookpro.R;
import com.recipebookpro.domain.model.Invitation;
import com.recipebookpro.domain.model.User;
import com.recipebookpro.util.NotificationTrigger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CollaboratorsBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_TARGET_ID = "target_id";
    private static final String ARG_TARGET_TYPE = "target_type";
    private static final String ARG_TARGET_NAME = "target_name";

    private String targetId;
    private String targetType;
    private String targetName;
    
    private List<String> collaboratorIds = new ArrayList<>();
    private String ownerId;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;

    private TextInputEditText etSearch;
    private LinearLayout containerSearchResults;
    private LinearLayout containerCollaborators;
    private LinearLayout containerPendingInvitations;
    private MaterialTextView tvPendingInvitationsLabel;
    private ListenerRegistration cookbookListener;
    private ListenerRegistration pendingInvitationsListener;

    private final List<Invitation> pendingOutgoing = new ArrayList<>();
    private final Set<String> pendingToUserIds = new HashSet<>();

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    public static CollaboratorsBottomSheet newInstance(String targetId, String targetType, String targetName) {
        CollaboratorsBottomSheet fragment = new CollaboratorsBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_TARGET_ID, targetId);
        args.putString(ARG_TARGET_TYPE, targetType);
        args.putString(ARG_TARGET_NAME, targetName);
        fragment.setArguments(args);
        return fragment;
    }

    public static CollaboratorsBottomSheet newInstance(String targetId) {
        return newInstance(targetId, "cookbook", "");
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            targetId = getArguments().getString(ARG_TARGET_ID);
            targetType = getArguments().getString(ARG_TARGET_TYPE, "cookbook");
            targetName = getArguments().getString(ARG_TARGET_NAME, "");
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
        containerPendingInvitations = view.findViewById(R.id.containerPendingInvitations);
        tvPendingInvitationsLabel = view.findViewById(R.id.tvPendingInvitationsLabel);

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

        loadTarget();
        loadPendingInvitations();

        return view;
    }

    private void loadTarget() {
        String collection;
        switch (targetType) {
            case "meal_plan":
                collection = "meal_plans";
                break;
            case "shopping_list":
                collection = "shopping_lists";
                break;
            default:
                collection = "cookbooks";
                break;
        }

        cookbookListener = db.collection(collection).document(targetId).addSnapshotListener((doc, e) -> {
            if (e != null || doc == null || !doc.exists()) return;
            if (!isAdded() || getContext() == null) return;
            
            collaboratorIds = (List<String>) doc.get("collaboratorIds");
            if (collaboratorIds == null) collaboratorIds = new ArrayList<>();
            ownerId = doc.getString("userId");
            if (targetName == null || targetName.isEmpty()) {
                targetName = doc.getString("name");
                if (targetName == null) targetName = doc.getString("title");
            }
            
            updateCollaboratorsList();
            updatePendingInvitationsUI();
        });
    }

    private void loadPendingInvitations() {
        if (TextUtils.isEmpty(targetId) || currentUser == null) {
            return;
        }
        if (pendingInvitationsListener != null) {
            pendingInvitationsListener.remove();
        }
        pendingInvitationsListener = db.collection("invitations")
                .whereEqualTo("targetId", targetId)
                .whereEqualTo("status", Invitation.STATUS_PENDING)
                .addSnapshotListener((snap, e) -> {
                    if (!isAdded() || getContext() == null || snap == null) return;
                    pendingOutgoing.clear();
                    pendingToUserIds.clear();
                    for (QueryDocumentSnapshot doc : snap) {
                        Invitation inv = Invitation.fromDocument(doc);
                        if (!targetType.equals(inv.getType())) {
                            continue;
                        }
                        if (currentUser.getUid().equals(inv.getFromUserId())) {
                            if (TextUtils.isEmpty(inv.getToUserId())) {
                                continue;
                            }
                            pendingOutgoing.add(inv);
                            pendingToUserIds.add(inv.getToUserId());
                        }
                    }
                    updatePendingInvitationsUI();
                });
    }

    private void updatePendingInvitationsUI() {
        if (containerPendingInvitations == null || tvPendingInvitationsLabel == null || !isAdded() || getContext() == null) {
            return;
        }
        containerPendingInvitations.removeAllViews();
        boolean owner = currentUser != null && ownerId != null && currentUser.getUid().equals(ownerId);
        if (!owner || pendingOutgoing.isEmpty()) {
            tvPendingInvitationsLabel.setVisibility(View.GONE);
            containerPendingInvitations.setVisibility(View.GONE);
            return;
        }
        tvPendingInvitationsLabel.setVisibility(View.VISIBLE);
        containerPendingInvitations.setVisibility(View.VISIBLE);

        List<Task<DocumentSnapshot>> tasks = new ArrayList<>();
        for (Invitation inv : pendingOutgoing) {
            tasks.add(db.collection("users").document(inv.getToUserId()).get());
        }
        if (tasks.isEmpty()) {
            return;
        }
        Tasks.whenAllSuccess(tasks).addOnSuccessListener(results -> {
            if (containerPendingInvitations == null || !isAdded() || getContext() == null) return;
            containerPendingInvitations.removeAllViews();
            for (int i = 0; i < pendingOutgoing.size(); i++) {
                Invitation inv = pendingOutgoing.get(i);
                DocumentSnapshot udoc = (DocumentSnapshot) results.get(i);

                View row = LayoutInflater.from(getContext())
                        .inflate(R.layout.item_pending_invitation_row, containerPendingInvitations, false);
                TextView tvName = row.findViewById(R.id.tvPendingInviteeName);
                TextView tvEmail = row.findViewById(R.id.tvPendingInviteeEmail);
                MaterialButton btnWithdraw = row.findViewById(R.id.btnWithdrawInvitation);

                String displayName = inv.getToUserId();
                String displayEmail = "";
                if (udoc != null && udoc.exists()) {
                    User u = udoc.toObject(User.class);
                    if (u != null) {
                        if (!TextUtils.isEmpty(u.getDisplayName())) {
                            displayName = u.getDisplayName();
                        }
                        if (!TextUtils.isEmpty(u.getEmail())) {
                            displayEmail = u.getEmail();
                        }
                    }
                }
                tvName.setText(displayName);
                tvEmail.setText(displayEmail);

                final String invitationId = inv.getId();
                btnWithdraw.setOnClickListener(v -> confirmWithdrawInvitation(invitationId));
                containerPendingInvitations.addView(row);
            }
        });
    }

    private void confirmWithdrawInvitation(String invitationId) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.invitation_withdraw)
                .setMessage(R.string.invitation_withdraw_confirm)
                .setPositiveButton(R.string.invitation_withdraw, (d, w) ->
                        db.collection("invitations").document(invitationId).delete()
                                .addOnSuccessListener(aVoid -> Toast.makeText(getContext(),
                                        R.string.invitation_withdraw_done, Toast.LENGTH_SHORT).show())
                                .addOnFailureListener(err -> Toast.makeText(getContext(),
                                        R.string.error_generic, Toast.LENGTH_SHORT).show()))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (cookbookListener != null) {
            cookbookListener.remove();
            cookbookListener = null;
        }
        if (pendingInvitationsListener != null) {
            pendingInvitationsListener.remove();
            pendingInvitationsListener = null;
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
                        tvEmpty.setText(R.string.no_results_found);
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
                        if (collaboratorIds.contains(user.getUid())) continue;
                        if (pendingToUserIds.contains(user.getUid())) continue;

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
        db.collection("invitations")
                .whereEqualTo("targetId", targetId)
                .whereEqualTo("toUserId", userId)
                .whereEqualTo("status", Invitation.STATUS_PENDING)
                .get()
                .addOnSuccessListener(existing -> {
                    if (!existing.isEmpty()) {
                        Toast.makeText(getContext(), R.string.invitation_already_sent, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    Invitation invitation = new Invitation();
                    invitation.setTargetId(targetId);
                    invitation.setType(targetType);
                    invitation.setTargetName(targetName);
                    invitation.setFromUserId(currentUser.getUid());
                    invitation.setFromUserName(currentUser.getDisplayName());
                    invitation.setToUserId(userId);
                    invitation.setStatus(Invitation.STATUS_PENDING);
                    invitation.setCreatedAt(System.currentTimeMillis());

                    db.collection("invitations").add(invitation)
                            .addOnSuccessListener(ref -> {
                                NotificationTrigger.triggerInvitation(
                                        currentUser.getDisplayName(),
                                        userId,
                                        targetName,
                                        ref.getId(),
                                        targetType
                                );
                                Toast.makeText(getContext(), getString(R.string.invitation_sent_to_user, displayName), Toast.LENGTH_SHORT).show();
                                etSearch.setText("");
                                containerSearchResults.removeAllViews();
                            });
                });
    }

    private void updateCollaboratorsList() {
        if (containerCollaborators == null || !isAdded() || getContext() == null) return;
        containerCollaborators.removeAllViews();

        boolean isOwner = currentUser != null && currentUser.getUid().equals(ownerId);

        List<String> collabIds = collaboratorIds;
        if (collabIds == null || collabIds.isEmpty()) {
            TextView tvEmpty = new TextView(getContext());
            tvEmpty.setText(R.string.no_collaborators_yet);
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
                        .inflate(R.layout.item_collaborator_row, containerCollaborators, false);
                TextView tvName = row.findViewById(R.id.tvCollabName);
                TextView tvEmail = row.findViewById(R.id.tvCollabEmail);
                MaterialButton btnRemove = row.findViewById(R.id.btnRemoveCollab);

                tvName.setText(user.getDisplayName());
                tvEmail.setText(user.getEmail());

                final String collabUid = user.getUid();
                final String collabName = user.getDisplayName();

                boolean isSelf = currentUser != null && currentUser.getUid().equals(collabUid);

                if (isOwner) {
                    btnRemove.setVisibility(View.VISIBLE);
                    btnRemove.setOnClickListener(v -> {
                        new MaterialAlertDialogBuilder(requireContext())
                                .setTitle(collabName)
                                .setMessage(R.string.remove_collaborator_confirm)
                                .setPositiveButton(R.string.delete, (d, w) -> removeCollaborator(collabUid))
                                .setNegativeButton(R.string.cancel, null)
                                .show();
                    });
                } else if (isSelf) {
                    btnRemove.setVisibility(View.VISIBLE);
                    btnRemove.setOnClickListener(v -> {
                        new MaterialAlertDialogBuilder(requireContext())
                                .setTitle(R.string.leave_collaboration)
                                .setMessage(R.string.cookbook_leave_confirm)
                                .setPositiveButton(R.string.leave, (d, w) -> removeCollaborator(collabUid))
                                .setNegativeButton(R.string.cancel, null)
                                .show();
                    });
                }

                containerCollaborators.addView(row);
            }
        });
    }

    private void removeCollaborator(String uid) {
        String collection;
        switch (targetType) {
            case "meal_plan":
                collection = "meal_plans";
                break;
            case "shopping_list":
                collection = "shopping_lists";
                break;
            default:
                collection = "cookbooks";
                break;
        }
        db.collection(collection).document(targetId)
                .update("collaboratorIds", FieldValue.arrayRemove(uid));
    }
}
