package com.recipebookpro.ui.kitchen.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.recipebookpro.R;
import com.recipebookpro.model.Cookbook;

import java.util.List;

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

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCookbookName = itemView.findViewById(R.id.tvCookbookName);
            tvRecipeCount = itemView.findViewById(R.id.tvRecipeCount);
        }
    }
}
