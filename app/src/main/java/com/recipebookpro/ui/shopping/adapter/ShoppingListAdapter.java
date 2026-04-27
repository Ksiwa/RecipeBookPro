package com.recipebookpro.ui.shopping.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textview.MaterialTextView;
import com.recipebookpro.R;
import com.recipebookpro.model.ShoppingList;

import java.util.List;

public class ShoppingListAdapter extends RecyclerView.Adapter<ShoppingListAdapter.ViewHolder> {

    private final List<ShoppingList> shoppingLists;
    private final OnShoppingListClickListener listener;

    public interface OnShoppingListClickListener {
        void onListClick(ShoppingList list);
    }

    public ShoppingListAdapter(List<ShoppingList> shoppingLists, OnShoppingListClickListener listener) {
        this.shoppingLists = shoppingLists;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_shopping_list, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ShoppingList list = shoppingLists.get(position);
        holder.tvName.setText(list.getName());
        
        int count = list.getItems() != null ? list.getItems().size() : 0;
        holder.tvCount.setText(count + " ürün");

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onListClick(list);
        });
    }

    @Override
    public int getItemCount() {
        return shoppingLists.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        MaterialTextView tvName, tvCount;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvShoppingListName);
            tvCount = itemView.findViewById(R.id.tvShoppingListCount);
        }
    }
}
