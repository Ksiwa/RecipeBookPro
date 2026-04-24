package com.recipebookpro.ui.recipe.adapter;

import android.annotation.SuppressLint;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.recipebookpro.R;
import com.recipebookpro.model.Step;

import java.util.Collections;
import java.util.List;

public class EditableStepAdapter extends RecyclerView.Adapter<EditableStepAdapter.ViewHolder> {

    private final List<Step> steps;
    private final OnStartDragListener dragStartListener;

    public interface OnStartDragListener {
        void onStartDrag(RecyclerView.ViewHolder viewHolder);
    }

    public EditableStepAdapter(List<Step> steps, OnStartDragListener dragStartListener) {
        this.steps = steps;
        this.dragStartListener = dragStartListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_editable_step, parent, false);
        return new ViewHolder(view, new StepTextWatcher(), new StepTextWatcher());
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Step step = steps.get(position);

        holder.descWatcher.updatePosition(position);
        holder.timerWatcher.updatePosition(position);

        holder.etStepDescription.setText(step.getDescription());
        holder.etStepTimer.setText(step.getTimerMinutes() > 0 ? String.valueOf(step.getTimerMinutes()) : "");

        holder.btnDeleteStep.setOnClickListener(v -> {
            int currentPos = holder.getAdapterPosition();
            if (currentPos != RecyclerView.NO_POSITION) {
                steps.remove(currentPos);
                updateOrderNumbers();
                notifyItemRemoved(currentPos);
                notifyItemRangeChanged(currentPos, steps.size());
            }
        });

        holder.ivDragHandle.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                dragStartListener.onStartDrag(holder);
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return steps == null ? 0 : steps.size();
    }

    public void onItemMove(int fromPosition, int toPosition) {
        if (fromPosition < toPosition) {
            for (int i = fromPosition; i < toPosition; i++) {
                Collections.swap(steps, i, i + 1);
            }
        } else {
            for (int i = fromPosition; i > toPosition; i--) {
                Collections.swap(steps, i, i - 1);
            }
        }
        updateOrderNumbers();
        notifyItemMoved(fromPosition, toPosition);
    }

    private void updateOrderNumbers() {
        for (int i = 0; i < steps.size(); i++) {
            steps.get(i).setOrder(i + 1);
        }
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivDragHandle;
        TextInputEditText etStepDescription;
        TextInputEditText etStepTimer;
        ImageButton btnDeleteStep;

        StepTextWatcher descWatcher;
        StepTextWatcher timerWatcher;

        ViewHolder(View itemView, StepTextWatcher descWatcher, StepTextWatcher timerWatcher) {
            super(itemView);
            ivDragHandle = itemView.findViewById(R.id.ivDragHandle);
            etStepDescription = itemView.findViewById(R.id.etStepDescription);
            etStepTimer = itemView.findViewById(R.id.etStepTimer);
            btnDeleteStep = itemView.findViewById(R.id.btnDeleteStep);

            this.descWatcher = descWatcher;
            this.descWatcher.isDesc = true;
            etStepDescription.addTextChangedListener(this.descWatcher);

            this.timerWatcher = timerWatcher;
            this.timerWatcher.isDesc = false;
            etStepTimer.addTextChangedListener(this.timerWatcher);
        }
    }

    private class StepTextWatcher implements TextWatcher {
        private int position;
        boolean isDesc;

        public void updatePosition(int position) {
            this.position = position;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}

        @Override
        public void afterTextChanged(Editable s) {
            if (position >= 0 && position < steps.size()) {
                if (isDesc) {
                    steps.get(position).setDescription(s.toString());
                } else {
                    int mins = 0;
                    try {
                        mins = Integer.parseInt(s.toString().trim());
                    } catch (NumberFormatException ignored) {}
                    steps.get(position).setTimerMinutes(mins);
                }
            }
        }
    }
}
