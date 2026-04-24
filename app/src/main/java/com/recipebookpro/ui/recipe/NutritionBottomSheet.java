package com.recipebookpro.ui.recipe;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.recipebookpro.R;
import com.recipebookpro.network.GeminiService;

public class NutritionBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_INGREDIENTS = "ingredients_text";
    private String ingredientsText;

    private LinearProgressIndicator progressNutrition;
    private View cardNutritionResult;
    private TextView tvNutritionResult;

    public static NutritionBottomSheet newInstance(String ingredients) {
        NutritionBottomSheet fragment = new NutritionBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_INGREDIENTS, ingredients);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            ingredientsText = getArguments().getString(ARG_INGREDIENTS);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_nutrition_dialog, container, false);
        
        progressNutrition = view.findViewById(R.id.progressNutrition);
        cardNutritionResult = view.findViewById(R.id.cardNutritionResult);
        tvNutritionResult = view.findViewById(R.id.tvNutritionResult);

        view.findViewById(R.id.btnClose).setOnClickListener(v -> dismiss());

        fetchNutritionData();

        return view;
    }

    private void fetchNutritionData() {
        if (ingredientsText == null || ingredientsText.trim().isEmpty()) {
            Toast.makeText(getContext(), "Analiz edilecek malzeme yok.", Toast.LENGTH_SHORT).show();
            dismiss();
            return;
        }

        progressNutrition.setVisibility(View.VISIBLE);
        cardNutritionResult.setVisibility(View.GONE);

        GeminiService.analyzeNutrition(ingredientsText, new GeminiService.GeminiCallback() {
            @Override
            public void onSuccess(String result) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    progressNutrition.setVisibility(View.GONE);
                    cardNutritionResult.setVisibility(View.VISIBLE);
                    tvNutritionResult.setText(result);
                });
            }

            @Override
            public void onError(String error) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    progressNutrition.setVisibility(View.GONE);
                    Toast.makeText(getContext(), "Hata: " + error, Toast.LENGTH_LONG).show();
                    dismiss();
                });
            }
        });
    }
}
