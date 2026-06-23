package com.recipebookpro.presentation.ui.planner;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textview.MaterialTextView;
import com.google.firebase.auth.FirebaseAuth;
import com.recipebookpro.R;
import com.recipebookpro.data.repository.MealPlanRepositoryImpl;
import com.recipebookpro.domain.model.MealPlan;
import com.recipebookpro.domain.repository.MealPlanRepository;
import com.recipebookpro.domain.usecase.DeleteMealPlanUseCase;

import java.util.ArrayList;
import java.util.List;

public class SavedPlansBottomSheet extends BottomSheetDialogFragment {

    public interface OnPlanSelectedListener {
        void onSelected(MealPlan plan);
    }

    /**
     * Açık ekranın dinlediği plan Firestore\'dan silindiğinde (bu sheet veya başka yerden).
     */
    public interface OnMealPlanDeletedListener {
        void onMealPlanDeleted(String deletedPlanId);
    }

    private OnPlanSelectedListener listener;
    private OnMealPlanDeletedListener deletedListener;

    private final List<MealPlan> plans = new ArrayList<>();
    private MealPlanRepository mealPlanRepository;
    private DeleteMealPlanUseCase deleteMealPlanUseCase;
    private SavedPlansAdapter adapter;
    private ProgressBar progressBar;
    private MaterialTextView tvEmpty;

    public void setOnPlanSelectedListener(OnPlanSelectedListener listener) {
        this.listener = listener;
    }

    public void setOnMealPlanDeletedListener(OnMealPlanDeletedListener listener) {
        this.deletedListener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mealPlanRepository = new MealPlanRepositoryImpl();
        deleteMealPlanUseCase = new DeleteMealPlanUseCase(mealPlanRepository);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_saved_plans, container, false);

        RecyclerView rv = view.findViewById(R.id.rvSavedPlans);
        progressBar = view.findViewById(R.id.progressSavedPlans);
        tvEmpty = view.findViewById(R.id.tvEmptySavedPlans);

        String uid = FirebaseAuth.getInstance().getUid();
        adapter = new SavedPlansAdapter(plans, uid, plan -> {
            if (listener != null) listener.onSelected(plan);
            dismiss();
        }, this::confirmDeletePlan);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        rv.setAdapter(adapter);

        loadPlans();

        return view;
    }

    private void confirmDeletePlan(MealPlan plan) {
        if (plan == null || getContext() == null) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(plan.getName())
                .setMessage(R.string.meal_plan_delete_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (d, w) -> performDelete(plan))
                .show();
    }

    private void performDelete(MealPlan plan) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        deleteMealPlanUseCase.execute(plan, uid, new MealPlanRepository.OnMealPlanActionCompleteListener() {
            @Override
            public void onSuccess() {
                if (getContext() == null || !isAdded()) return;

                Toast.makeText(requireContext(), R.string.meal_plan_deleted, Toast.LENGTH_SHORT).show();
                plans.remove(plan);
                adapter.notifyDataSetChanged();
                tvEmpty.setVisibility(plans.isEmpty() ? View.VISIBLE : View.GONE);

                if (deletedListener != null && plan.getId() != null) {
                    deletedListener.onMealPlanDeleted(plan.getId());
                }
            }

            @Override
            public void onError(Exception e) {
                if (getContext() == null || !isAdded()) return;
                Toast.makeText(requireContext(), R.string.meal_plan_delete_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadPlans() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        progressBar.setVisibility(View.VISIBLE);
        mealPlanRepository.getUserMealPlans(uid, new MealPlanRepository.OnMealPlansLoadedListener() {
            @Override
            public void onLoaded(List<MealPlan> loadedPlans) {
                progressBar.setVisibility(View.GONE);
                plans.clear();
                plans.addAll(loadedPlans);
                adapter.notifyDataSetChanged();
                tvEmpty.setVisibility(plans.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(Exception e) {
                progressBar.setVisibility(View.GONE);
            }
        });
    }

    private static final class SavedPlansAdapter extends RecyclerView.Adapter<SavedPlansAdapter.ViewHolder> {

        interface OnPickPlanListener {
            void onPickPlan(MealPlan plan);
        }

        interface OnRequestDeletePlanListener {
            void onRequestDelete(MealPlan plan);
        }

        private final List<MealPlan> plans;
        private final String currentUserIdOrNull;
        private final OnPickPlanListener pickListener;
        private final OnRequestDeletePlanListener deleteRequestListener;

        SavedPlansAdapter(List<MealPlan> plans,
                          String currentUserIdOrNull,
                          OnPickPlanListener pickListener,
                          OnRequestDeletePlanListener deleteRequestListener) {
            this.plans = plans;
            this.currentUserIdOrNull = currentUserIdOrNull;
            this.pickListener = pickListener;
            this.deleteRequestListener = deleteRequestListener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_saved_meal_plan, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            MealPlan p = plans.get(position);
            holder.tvName.setText(p.getName());
            holder.tvSubtitle.setText(holder.itemView.getResources()
                    .getQuantityString(R.plurals.meal_plan_duration_calories_display,
                            p.getDuration(), p.getDuration(), p.getTotalCalories()));

            boolean owned = currentUserIdOrNull != null && currentUserIdOrNull.equals(p.getUserId());
            holder.btnDelete.setVisibility(owned ? View.VISIBLE : View.GONE);

            holder.containerMain.setOnClickListener(v -> {
                if (pickListener != null) pickListener.onPickPlan(p);
            });
            holder.btnDelete.setOnClickListener(v -> {
                if (deleteRequestListener != null) deleteRequestListener.onRequestDelete(p);
            });
        }

        @Override
        public int getItemCount() {
            return plans.size();
        }

        static final class ViewHolder extends RecyclerView.ViewHolder {
            final View containerMain;
            final MaterialTextView tvName;
            final MaterialTextView tvSubtitle;
            final View btnDelete;

            ViewHolder(View itemView) {
                super(itemView);
                containerMain = itemView.findViewById(R.id.containerSavedPlanMain);
                tvName = itemView.findViewById(R.id.tvSavedPlanName);
                tvSubtitle = itemView.findViewById(R.id.tvSavedPlanSubtitle);
                btnDelete = itemView.findViewById(R.id.btnDeleteSavedPlan);
            }
        }
    }
}
