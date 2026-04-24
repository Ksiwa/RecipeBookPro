package com.recipebookpro.ui.recipe.adapter;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.recipebookpro.R;
import com.recipebookpro.model.Recipe;

import java.util.List;

public class EditableIngredientAdapter extends RecyclerView.Adapter<EditableIngredientAdapter.ViewHolder> {

    private final List<Recipe.Ingredient> ingredients;
    private final String[] units;

    public EditableIngredientAdapter(List<Recipe.Ingredient> ingredients, String[] units) {
        this.ingredients = ingredients;
        this.units = units;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_editable_ingredient, parent, false);
        return new ViewHolder(view, new IngredientTextWatcher(), new IngredientTextWatcher(), new UnitSelectionListener());
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Recipe.Ingredient ingredient = ingredients.get(position);

        holder.nameWatcher.updatePosition(position);
        holder.amountWatcher.updatePosition(position);
        holder.unitListener.updatePosition(position);

        holder.etName.setText(ingredient.getName());
        holder.etAmount.setText(ingredient.getAmount());
        holder.actvUnit.setText(ingredient.getUnit(), false);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                holder.itemView.getContext(),
                android.R.layout.simple_dropdown_item_1line,
                units
        );
        holder.actvUnit.setAdapter(adapter);

        holder.btnDelete.setOnClickListener(v -> {
            int currentPos = holder.getAdapterPosition();
            if (currentPos != RecyclerView.NO_POSITION) {
                ingredients.remove(currentPos);
                notifyItemRemoved(currentPos);
                notifyItemRangeChanged(currentPos, ingredients.size());
            }
        });
    }

    @Override
    public int getItemCount() {
        return ingredients == null ? 0 : ingredients.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        TextInputEditText etName;
        TextInputEditText etAmount;
        AutoCompleteTextView actvUnit;
        ImageButton btnDelete;

        IngredientTextWatcher nameWatcher;
        IngredientTextWatcher amountWatcher;
        UnitSelectionListener unitListener;

        ViewHolder(View itemView, IngredientTextWatcher nameWatcher, IngredientTextWatcher amountWatcher, UnitSelectionListener unitListener) {
            super(itemView);
            etName = itemView.findViewById(R.id.etName);
            etAmount = itemView.findViewById(R.id.etAmount);
            actvUnit = itemView.findViewById(R.id.actvUnit);
            btnDelete = itemView.findViewById(R.id.btnDelete);

            this.nameWatcher = nameWatcher;
            this.nameWatcher.isName = true;
            etName.addTextChangedListener(this.nameWatcher);

            this.amountWatcher = amountWatcher;
            this.amountWatcher.isName = false;
            etAmount.addTextChangedListener(this.amountWatcher);

            this.unitListener = unitListener;
            actvUnit.setOnItemClickListener(this.unitListener);
        }
    }

    private class IngredientTextWatcher implements TextWatcher {
        private int position;
        boolean isName;

        public void updatePosition(int position) {
            this.position = position;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}

        @Override
        public void afterTextChanged(Editable s) {
            if (position >= 0 && position < ingredients.size()) {
                if (isName) {
                    ingredients.get(position).setName(s.toString());
                } else {
                    ingredients.get(position).setAmount(s.toString());
                }
            }
        }
    }

    private class UnitSelectionListener implements android.widget.AdapterView.OnItemClickListener {
        private int position;

        public void updatePosition(int position) {
            this.position = position;
        }

        @Override
        public void onItemClick(android.widget.AdapterView<?> parent, View view, int pos, long id) {
            if (position >= 0 && position < ingredients.size()) {
                String selectedUnit = parent.getItemAtPosition(pos).toString();
                ingredients.get(position).setUnit(selectedUnit);
            }
        }
    }
}
