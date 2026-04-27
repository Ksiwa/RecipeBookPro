package com.recipebookpro.ui.shopping.adapter;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textview.MaterialTextView;
import com.recipebookpro.R;
import com.recipebookpro.model.ShoppingList.ShoppingItem;

import java.util.List;

public class ShoppingItemAdapter extends RecyclerView.Adapter<ShoppingItemAdapter.ViewHolder> {

    private final List<ShoppingItem> items;
    private final OnItemInteractionListener listener;

    public interface OnItemInteractionListener {
        void onCheckChanged(int position, boolean isChecked);
        void onHomeStatusChanged(int position, boolean haveItAtHome);
    }

    public ShoppingItemAdapter(List<ShoppingItem> items, OnItemInteractionListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_shopping_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ShoppingItem item = items.get(position);
        
        holder.tvName.setText(item.getDisplayText());
        
        // Remove listeners temporarily to prevent unwanted triggers during bind
        holder.cbItem.setOnCheckedChangeListener(null);
        holder.switchStatus.setOnCheckedChangeListener(null);
        
        holder.cbItem.setChecked(item.isChecked());
        
        boolean haveIt = ShoppingItem.STATUS_HAVE_IT.equals(item.getHomeStatus());
        holder.switchStatus.setChecked(haveIt);
        holder.switchStatus.setText(haveIt ? "Evde Var" : "Alınacak");
        
        updateStrikeThrough(holder.tvName, item.isChecked() || haveIt);

        holder.cbItem.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateStrikeThrough(holder.tvName, isChecked || holder.switchStatus.isChecked());
            if (listener != null) listener.onCheckChanged(holder.getAdapterPosition(), isChecked);
        });
        
        holder.switchStatus.setOnCheckedChangeListener((buttonView, isChecked) -> {
            holder.switchStatus.setText(isChecked ? "Evde Var" : "Alınacak");
            updateStrikeThrough(holder.tvName, isChecked || holder.cbItem.isChecked());
            if (listener != null) listener.onHomeStatusChanged(holder.getAdapterPosition(), isChecked);
        });
    }

    private void updateStrikeThrough(MaterialTextView tv, boolean apply) {
        if (apply) {
            tv.setPaintFlags(tv.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            tv.setAlpha(0.5f);
        } else {
            tv.setPaintFlags(tv.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            tv.setAlpha(1.0f);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        MaterialCheckBox cbItem;
        MaterialTextView tvName;
        MaterialSwitch switchStatus;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            cbItem = itemView.findViewById(R.id.cbShoppingItem);
            tvName = itemView.findViewById(R.id.tvShoppingItemName);
            switchStatus = itemView.findViewById(R.id.switchHomeStatus);
        }
    }
}
