package com.recipebookpro.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textview.MaterialTextView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.recipebookpro.R;
import com.recipebookpro.ui.auth.LoginActivity;

public class ProfileFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        MaterialTextView tvEmail = view.findViewById(R.id.tvProfileEmail);
        MaterialButton btnLogout = view.findViewById(R.id.btnLogout);
        MaterialButton btnSettings = view.findViewById(R.id.btnSettings);
        MaterialButton btnAdmin = view.findViewById(R.id.btnAdminPanel);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            tvEmail.setText(currentUser.getEmail());
        }

        btnLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(requireContext(), LoginActivity.class));
            requireActivity().finishAffinity();
        });

        // Placeholder click listeners — to be implemented later
        btnSettings.setOnClickListener(v -> { /* TODO: open settings */ });
        btnAdmin.setOnClickListener(v -> { /* TODO: open admin panel */ });
    }
}
