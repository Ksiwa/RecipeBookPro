package com.recipebookpro.presentation.ui.recipe;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
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
import com.recipebookpro.data.remote.GroqAiNutritionService;
import com.recipebookpro.domain.usecase.AnalyzeIngredientNutritionUseCase;
import com.recipebookpro.presentation.ui.LocaleHelper;

public class NutritionBottomSheet extends BottomSheetDialogFragment {

    private static final String TAG = "NutritionBottomSheet";
    private static final String ARG_INGREDIENTS = "ingredients_text";
    private String ingredientsText;

    private LinearProgressIndicator progressNutrition;
    private View cardNutritionResult;
    private TextView tvNutritionResult;

    private AnalyzeIngredientNutritionUseCase analyzeIngredientNutritionUseCase;

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
        analyzeIngredientNutritionUseCase = new AnalyzeIngredientNutritionUseCase(new GroqAiNutritionService());
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
            Toast.makeText(getContext(), R.string.no_ingredients_to_analyze, Toast.LENGTH_SHORT).show();
            dismiss();
            return;
        }

        progressNutrition.setVisibility(View.VISIBLE);
        cardNutritionResult.setVisibility(View.GONE);

        String uiLanguage = LocaleHelper.getLanguage(requireContext());
        analyzeIngredientNutritionUseCase.execute(ingredientsText, uiLanguage, new AnalyzeIngredientNutritionUseCase.Callback() {
            @Override
            public void onSuccess(String nutritionText) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    progressNutrition.setVisibility(View.GONE);
                    cardNutritionResult.setVisibility(View.VISIBLE);
                    tvNutritionResult.setText(nutritionText);
                });
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Besin analizi hatası: " + error);
                new Handler(Looper.getMainLooper()).post(() -> {
                    progressNutrition.setVisibility(View.GONE);
                    Toast.makeText(getContext(), getString(R.string.error_with_reason, error), Toast.LENGTH_LONG).show();
                    dismiss();
                });
            }
        });
    }
}
