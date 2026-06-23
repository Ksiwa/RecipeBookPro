package com.recipebookpro.presentation.ui.shopping.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textview.MaterialTextView;
import com.recipebookpro.R;
import com.recipebookpro.domain.model.ShoppingList;

import java.util.List;

public class ShoppingListAdapter extends RecyclerView.Adapter<ShoppingListAdapter.ViewHolder> {

    private final List<ShoppingList> shoppingLists;
    private final OnShoppingListClickListener listener;
    private OnShoppingListLongClickListener longClickListener;

    public interface OnShoppingListClickListener {
        void onListClick(ShoppingList list);
    }

    public interface OnShoppingListLongClickListener {
        void onListLongClick(ShoppingList list);
    }

    public void setOnLongClickListener(OnShoppingListLongClickListener listener) {
        this.longClickListener = listener;
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
        
        String listName = list.getName();
        if ("system_planner_weekly_menu".equals(listName) || "Weekly Menu Shopping".equals(listName) || "Haftalık Menü Alışverişi".equals(listName)) {
            listName = holder.itemView.getContext().getString(R.string.planner_weekly_menu_list_name);
        }
        holder.tvName.setText(listName);
        
        int count = list.getItems() != null ? list.getItems().size() : 0;
        holder.tvCount.setText(holder.itemView.getResources()
                .getQuantityString(R.plurals.shopping_item_count_display, count, count));

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onListClick(list);
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onListLongClick(list);
                return true;
            }
            return false;
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
