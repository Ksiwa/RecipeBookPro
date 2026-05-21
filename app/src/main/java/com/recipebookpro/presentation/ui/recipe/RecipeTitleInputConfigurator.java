package com.recipebookpro.presentation.ui.recipe;

import android.os.Build;
import android.os.LocaleList;
import android.text.InputType;

import com.google.android.material.textfield.TextInputEditText;

import java.util.Locale;

final class RecipeTitleInputConfigurator {

    private static final Locale TURKISH = new Locale("tr", "TR");
    private static final Locale ENGLISH = Locale.ENGLISH;

    private RecipeTitleInputConfigurator() {
    }

    static void configure(TextInputEditText editText) {
        if (editText == null) {
            return;
        }

        editText.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        editText.setSingleLine(true);
        editText.setTextLocale(TURKISH);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            editText.setImeHintLocales(new LocaleList(TURKISH, ENGLISH));
        }
    }
}
