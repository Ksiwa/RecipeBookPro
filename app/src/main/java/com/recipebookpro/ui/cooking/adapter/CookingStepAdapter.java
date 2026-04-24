package com.recipebookpro.ui.cooking.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.recipebookpro.R;
import com.recipebookpro.model.Step;

import java.util.List;

public class CookingStepAdapter extends RecyclerView.Adapter<CookingStepAdapter.ViewHolder> {

    private final List<Step> steps;
    private final OnManualTimerStartListener timerListener;

    public interface OnManualTimerStartListener {
        void onStartTimer(int minutes);
    }

    public CookingStepAdapter(List<Step> steps, OnManualTimerStartListener timerListener) {
        this.steps = steps;
        this.timerListener = timerListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_cooking_step_fullscreen, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Step step = steps.get(position);

        holder.tvStepNumberBig.setText(holder.itemView.getContext().getString(R.string.step_number, step.getOrder()));
        holder.tvStepDescriptionBig.setText(step.getDescription());

        if (step.hasTimer()) {
            holder.cardTimerInfo.setVisibility(View.VISIBLE);
            holder.tvTimerMinutesBig.setText(step.getTimerMinutes() + " dk");
            holder.btnStartTimerManual.setOnClickListener(v -> timerListener.onStartTimer(step.getTimerMinutes()));
        } else {
            holder.cardTimerInfo.setVisibility(View.GONE);
        }

        // Image loading logic would go here via Coil if images are implemented per step
        holder.ivStepImageBig.setVisibility(View.GONE);
    }

    @Override
    public int getItemCount() {
        return steps == null ? 0 : steps.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvStepNumberBig;
        TextView tvStepDescriptionBig;
        ImageView ivStepImageBig;
        View cardTimerInfo;
        TextView tvTimerMinutesBig;
        MaterialButton btnStartTimerManual;

        ViewHolder(View itemView) {
            super(itemView);
            tvStepNumberBig = itemView.findViewById(R.id.tvStepNumberBig);
            tvStepDescriptionBig = itemView.findViewById(R.id.tvStepDescriptionBig);
            ivStepImageBig = itemView.findViewById(R.id.ivStepImageBig);
            cardTimerInfo = itemView.findViewById(R.id.cardTimerInfo);
            tvTimerMinutesBig = itemView.findViewById(R.id.tvTimerMinutesBig);
            btnStartTimerManual = itemView.findViewById(R.id.btnStartTimerManual);
        }
    }
}
