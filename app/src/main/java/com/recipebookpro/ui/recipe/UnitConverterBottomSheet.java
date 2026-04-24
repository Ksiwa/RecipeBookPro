package com.recipebookpro.ui.recipe;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.textfield.TextInputEditText;
import com.recipebookpro.R;
import com.recipebookpro.util.UnitConverter;

public class UnitConverterBottomSheet extends BottomSheetDialogFragment {

    private TextInputEditText etAmount;
    private Spinner spinnerFromUnit;
    private Spinner spinnerToUnit;
    private TextView tvConvertResult;

    private final String[] UNITS = {"Cup", "ml", "oz", "gram", "tbsp", "tsp"};

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_unit_converter_dialog, container, false);

        etAmount = view.findViewById(R.id.etAmount);
        spinnerFromUnit = view.findViewById(R.id.spinnerFromUnit);
        spinnerToUnit = view.findViewById(R.id.spinnerToUnit);
        tvConvertResult = view.findViewById(R.id.tvConvertResult);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, UNITS);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        spinnerFromUnit.setAdapter(adapter);
        spinnerToUnit.setAdapter(adapter);

        // Default selections (Cup to ml)
        spinnerFromUnit.setSelection(0);
        spinnerToUnit.setSelection(1);

        view.findViewById(R.id.btnConvert).setOnClickListener(v -> performConversion());

        return view;
    }

    private void performConversion() {
        String amountStr = etAmount.getText() != null ? etAmount.getText().toString().trim() : "";
        if (TextUtils.isEmpty(amountStr)) return;

        try {
            double amount = Double.parseDouble(amountStr);
            String fromUnit = (String) spinnerFromUnit.getSelectedItem();
            String toUnit = (String) spinnerToUnit.getSelectedItem();

            double result = UnitConverter.convert(amount, fromUnit, toUnit);
            if (result == -1) {
                tvConvertResult.setText("Dönüşüm Desteklenmiyor");
            } else {
                tvConvertResult.setText(String.format("Sonuç: %.2f %s", result, toUnit));
            }
        } catch (Exception e) {
            tvConvertResult.setText("Hatalı Giriş");
        }
    }
}
