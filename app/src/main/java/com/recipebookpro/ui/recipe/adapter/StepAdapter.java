package com.recipebookpro.ui.recipe.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.recipebookpro.R;
import com.recipebookpro.model.Step;

import java.util.List;

public class StepAdapter extends RecyclerView.Adapter<StepAdapter.ViewHolder> {

    private final List<Step> steps;

    public StepAdapter(List<Step> steps) {
        this.steps = steps;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_step_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Step step = steps.get(position);
        
        holder.tvStepNumber.setText(String.valueOf(step.getOrder()));
        holder.tvStepDescription.setText(step.getDescription());
        
        if (step.hasTimer()) {
            holder.chipTimer.setVisibility(View.VISIBLE);
            holder.chipTimer.setText(step.getTimerMinutes() + " dk");
        } else {
            holder.chipTimer.setVisibility(View.GONE);
        }
        
        // Future: Load step.getImageUrl() using Coil if available
        holder.ivStepImage.setVisibility(View.GONE);
    }

    @Override
    public int getItemCount() {
        return steps == null ? 0 : steps.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvStepNumber;
        TextView tvStepDescription;
        Chip chipTimer;
        ImageView ivStepImage;

        ViewHolder(View itemView) {
            super(itemView);
            tvStepNumber = itemView.findViewById(R.id.tvStepNumber);
            tvStepDescription = itemView.findViewById(R.id.tvStepDescription);
            chipTimer = itemView.findViewById(R.id.chipTimer);
            ivStepImage = itemView.findViewById(R.id.ivStepImage);
        }
    }
}
