package com.recipebookpro.presentation.ui.shopping.adapter;

import android.graphics.Paint;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textview.MaterialTextView;
import com.recipebookpro.R;
import com.recipebookpro.domain.model.ShoppingList.ShoppingItem;

import java.util.List;

public class ShoppingItemAdapter extends RecyclerView.Adapter<ShoppingItemAdapter.ViewHolder> {

    private final List<ShoppingItem> items;
    private final OnItemInteractionListener listener;

    public interface OnItemInteractionListener {
        void onCheckChanged(int position, boolean isChecked);

        void onHomeStatusChanged(int position, boolean haveItAtHome);

        void onEditClick(int position);

        void onDeleteClick(int position);
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

        boolean userAdded = item.isUserAdded();
        holder.tvSource.setText(userAdded ? R.string.shopping_item_source_user : R.string.shopping_item_source_recipe);
        TypedValue typedValue = new TypedValue();
        if (userAdded) {
            holder.itemView.getContext().getTheme().resolveAttribute(
                    com.google.android.material.R.attr.colorPrimary, typedValue, true);
        } else {
            holder.itemView.getContext().getTheme().resolveAttribute(
                    com.google.android.material.R.attr.colorOnSurfaceVariant, typedValue, true);
        }
        holder.tvSource.setTextColor(typedValue.data);

        int btnVis = userAdded ? View.VISIBLE : View.GONE;
        holder.btnEdit.setVisibility(btnVis);
        holder.btnDelete.setVisibility(btnVis);

        // Remove listeners temporarily to prevent unwanted triggers during bind
        holder.cbItem.setOnCheckedChangeListener(null);
        holder.switchStatus.setOnCheckedChangeListener(null);
        
        holder.cbItem.setChecked(item.isChecked());
        
        boolean haveIt = ShoppingItem.STATUS_HAVE_IT.equals(item.getHomeStatus());
        holder.switchStatus.setChecked(haveIt);
        holder.switchStatus.setText(haveIt ? 
                holder.itemView.getContext().getString(R.string.shopping_item_status_at_home) : 
                holder.itemView.getContext().getString(R.string.shopping_item_status_to_buy));
        
        updateStrikeThrough(holder.tvName, item.isChecked() || haveIt);

        holder.cbItem.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateStrikeThrough(holder.tvName, isChecked || holder.switchStatus.isChecked());
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && listener != null) {
                listener.onCheckChanged(pos, isChecked);
            }
        });

        holder.switchStatus.setOnCheckedChangeListener((buttonView, isChecked) -> {
            holder.switchStatus.setText(isChecked ?
                    holder.itemView.getContext().getString(R.string.shopping_item_status_at_home) :
                    holder.itemView.getContext().getString(R.string.shopping_item_status_to_buy));
            updateStrikeThrough(holder.tvName, isChecked || holder.cbItem.isChecked());
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && listener != null) {
                listener.onHomeStatusChanged(pos, isChecked);
            }
        });

        holder.btnEdit.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && listener != null) {
                listener.onEditClick(pos);
            }
        });
        holder.btnDelete.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && listener != null) {
                listener.onDeleteClick(pos);
            }
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
        MaterialTextView tvSource;
        MaterialTextView tvName;
        MaterialSwitch switchStatus;
        MaterialButton btnEdit;
        MaterialButton btnDelete;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            cbItem = itemView.findViewById(R.id.cbShoppingItem);
            tvSource = itemView.findViewById(R.id.tvShoppingItemSource);
            tvName = itemView.findViewById(R.id.tvShoppingItemName);
            switchStatus = itemView.findViewById(R.id.switchHomeStatus);
            btnEdit = itemView.findViewById(R.id.btnEditShoppingItem);
            btnDelete = itemView.findViewById(R.id.btnDeleteShoppingItem);
        }
    }
}
