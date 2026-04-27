package com.recipebookpro.ui.kitchen.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.recipebookpro.R;
import com.recipebookpro.model.Cookbook;

import java.util.List;

import coil.Coil;
import coil.request.ImageRequest;

public class CookbookAdapter extends RecyclerView.Adapter<CookbookAdapter.ViewHolder> {
    
    private final List<Cookbook> cookbooks;
    private final OnCookbookClickListener listener;

    public interface OnCookbookClickListener {
        void onCookbookClick(Cookbook cookbook);
    }

    public CookbookAdapter(List<Cookbook> cookbooks, OnCookbookClickListener listener) {
        this.cookbooks = cookbooks;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_cookbook, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Cookbook book = cookbooks.get(position);
        holder.tvCookbookName.setText(book.getName());
        int count = book.getRecipeIds() != null ? book.getRecipeIds().size() : 0;
        holder.tvRecipeCount.setText(count + " Tarif");
        
        if (book.getCoverImageUrl() != null && !book.getCoverImageUrl().isEmpty()) {
            holder.ivCookbookCover.setPadding(0, 0, 0, 0);
            holder.ivCookbookCover.setBackground(null);
            holder.ivCookbookCover.setImageTintList(null);
            
            ImageRequest request = new ImageRequest.Builder(holder.itemView.getContext())
                    .data(book.getCoverImageUrl())
                    .target(holder.ivCookbookCover)
                    .crossfade(true)
                    .placeholder(R.drawable.ic_book)
                    .error(R.drawable.ic_book)
                    .build();
            Coil.imageLoader(holder.itemView.getContext()).enqueue(request);
        } else {
            // Reset to placeholder state if no image
            holder.ivCookbookCover.setImageResource(R.drawable.ic_book);
            holder.ivCookbookCover.setPadding(32, 32, 32, 32);
            // These might need ContextCompat if tinting manually, but we'll assume XML defaults or stick to basic reset
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onCookbookClick(book);
        });
    }

    @Override
    public int getItemCount() {
        return cookbooks.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvCookbookName, tvRecipeCount;
        ImageView ivCookbookCover;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCookbookName = itemView.findViewById(R.id.tvCookbookName);
            tvRecipeCount = itemView.findViewById(R.id.tvRecipeCount);
            ivCookbookCover = itemView.findViewById(R.id.ivCookbookCover);
        }
    }
}
