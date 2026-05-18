package com.recipebookpro.presentation.ui.recipe;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.List;

public class RecipeDetailViewModel extends AndroidViewModel {

    private final MutableLiveData<List<String>> riskyMatchTerms = new MutableLiveData<>();

    public RecipeDetailViewModel(@NonNull Application application) {
        super(application);
    }

    public void setRiskyMatchTerms(List<String> terms) {
        riskyMatchTerms.setValue(terms);
    }

    public LiveData<List<String>> getRiskyMatchTerms() {
        return riskyMatchTerms;
    }

    public LiveData<com.recipebookpro.domain.model.User> getUserData(com.google.firebase.auth.FirebaseUser currentUser) {
        if (currentUser == null) {
            MutableLiveData<com.recipebookpro.domain.model.User> empty = new MutableLiveData<>();
            empty.setValue(null);
            return empty;
        }

        MutableLiveData<com.recipebookpro.domain.model.User> userData = new MutableLiveData<>();
        final String uid = currentUser.getUid();

        new Thread(() -> {
            // First try Room
            Application app = getApplication();
            if (app != null) {
                com.recipebookpro.data.local.AppDatabase localDb = com.recipebookpro.data.local.AppDatabase.getDatabase(app);
                com.recipebookpro.data.local.entity.UserEntity entity = localDb.userDao().getUserByUid(uid);
                
                if (entity != null) {
                    com.recipebookpro.domain.model.User user = new com.recipebookpro.domain.model.User();
                    user.setHealthConditions(entity.getHealthConditions());
                    user.setCustomHealthConditionsI18n(entity.getCustomHealthConditionsI18n());
                    user.setActiveCustomHealthConditionKeys(entity.getActiveCustomHealthConditionKeys());
                    user.setHealthTriggers(entity.getHealthTriggers());
                    // Post to main thread
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        userData.setValue(user);
                    });
                    return; // Early exit, we have fresh Room data!
                }
            }

            // Fallback to Firestore
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        com.recipebookpro.domain.model.User user = doc.toObject(com.recipebookpro.domain.model.User.class);
                        userData.setValue(user);
                    } else {
                        userData.setValue(null);
                    }
                })
                .addOnFailureListener(e -> userData.setValue(null));

        }).start();

        return userData;
    }
}
