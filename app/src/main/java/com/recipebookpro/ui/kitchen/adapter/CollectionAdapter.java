package com.recipebookpro.ui.kitchen.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textview.MaterialTextView;
import com.recipebookpro.R;
import com.recipebookpro.model.RecipeCollection;

import java.util.List;

public class CollectionAdapter extends RecyclerView.Adapter<CollectionAdapter.ViewHolder> {

    private final List<RecipeCollection> collections;
    private final OnCollectionClickListener listener;

    public interface OnCollectionClickListener {
        void onCollectionClick(RecipeCollection collection);
    }

    public CollectionAdapter(List<RecipeCollection> collections, OnCollectionClickListener listener) {
        this.collections = collections;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_collection, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RecipeCollection collection = collections.get(position);
        holder.tvCollectionName.setText(collection.getName());
        holder.tvCollectionEmoji.setText(collection.getEmoji());
        
        int count = collection.getRecipeIds() != null ? collection.getRecipeIds().size() : 0;
        holder.tvCollectionCount.setText(count + " tarif");

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onCollectionClick(collection);
        });
    }

    @Override
    public int getItemCount() {
        return collections.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        MaterialTextView tvCollectionName, tvCollectionCount, tvCollectionEmoji;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCollectionName = itemView.findViewById(R.id.tvCollectionName);
            tvCollectionCount = itemView.findViewById(R.id.tvCollectionCount);
            tvCollectionEmoji = itemView.findViewById(R.id.tvCollectionEmoji);
        }
    }
}
