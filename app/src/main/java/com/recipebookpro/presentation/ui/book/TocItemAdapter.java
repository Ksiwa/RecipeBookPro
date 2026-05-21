package com.recipebookpro.presentation.ui.book;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textview.MaterialTextView;
import com.recipebookpro.R;
import com.recipebookpro.domain.model.Recipe;

import java.util.List;

public class TocItemAdapter extends RecyclerView.Adapter<TocItemAdapter.VH> {

    public interface OnItemClick {
        void onClick(int position);
    }

    private final List<Recipe> recipes;
    private final OnItemClick listener;

    public TocItemAdapter(List<Recipe> recipes, OnItemClick listener) {
        this.recipes = recipes;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_toc_recipe, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Recipe recipe = recipes.get(position);
        String lang = com.recipebookpro.presentation.ui.LocaleHelper.getLanguage(holder.itemView.getContext());
        holder.tvTitle.setText(recipe.getDisplayTitle(lang));
        holder.tvPage.setText(String.valueOf(position + 3));
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onClick(position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return recipes.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final MaterialTextView tvTitle;
        final MaterialTextView tvPage;

        VH(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTocTitle);
            tvPage = itemView.findViewById(R.id.tvTocPage);
        }
    }
}
